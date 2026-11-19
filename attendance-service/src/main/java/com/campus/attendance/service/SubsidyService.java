package com.campus.attendance.service;

import com.campus.attendance.client.AuthClient;
import com.campus.attendance.common.AttendanceStatus;
import com.campus.attendance.common.BusinessException;
import com.campus.attendance.common.CalcUtils;
import com.campus.attendance.common.MonthRange;
import com.campus.attendance.common.Roles;
import com.campus.attendance.common.SubsidyStatus;
import com.campus.attendance.dto.DeptSummaryView;
import com.campus.attendance.dto.MonthSubsidyView;
import com.campus.attendance.dto.PersonSummary;
import com.campus.attendance.dto.SchoolSummaryView;
import com.campus.attendance.entity.AttendanceRecord;
import com.campus.attendance.entity.Subsidy;
import com.campus.attendance.repository.AttendanceRecordRepository;
import com.campus.attendance.repository.SubsidyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 月度工时补贴核算 + 部门/全校汇总。
 *
 * 核算口径（README 与答辩口径以此为准）：
 *   1. 只统计 status=1（已签退）的记录；未签退、异常待确认不计入；
 *   2. 金额 = Σ(该条记录的分钟数 × 该条记录的时薪快照 ÷ 60)，保留 2 位小数；
 *   3. 工时 = Σ分钟 ÷ 60，保留 1 位小数，仅用于展示。
 *
 * 权限隔离：
 *   - 学生：只能看自己的补贴；
 *   - 部门管理员：只能看本部门汇总（deptId 从权限服务取，不接受前端传参绕过）；
 *   - 助学中心管理员：看全校汇总（按部门下钻），查单个部门时必须显式指定 deptId。
 */
@Service
public class SubsidyService {

    private static final Logger log = LoggerFactory.getLogger(SubsidyService.class);

    private final AttendanceRecordRepository recordRepository;
    private final SubsidyRepository subsidyRepository;
    private final AuthGuard authGuard;
    private final AuthClient authClient;

    public SubsidyService(AttendanceRecordRepository recordRepository,
                          SubsidyRepository subsidyRepository,
                          AuthGuard authGuard,
                          AuthClient authClient) {
        this.recordRepository = recordRepository;
        this.subsidyRepository = subsidyRepository;
        this.authGuard = authGuard;
        this.authClient = authClient;
    }

    /** 我的月度工时补贴 */
    @Transactional(readOnly = true)
    public MonthSubsidyView mySubsidy(String token, String month) {
        AuthClient.Session session = authGuard.requireRole(token, Roles.STUDENT);
        MonthRange range = MonthRange.of(month);

        List<AttendanceRecord> records = recordRepository
                .findUserMonthRecords(session.userId(), range.from(), range.to())
                .stream()
                .filter(r -> r.getStatus() != null && r.getStatus() == AttendanceStatus.CHECKED_OUT)
                .toList();

        int minutes = records.stream()
                .mapToInt(r -> r.getWorkMinutes() == null ? 0 : r.getWorkMinutes())
                .sum();
        BigDecimal amount = records.stream()
                .map(r -> CalcUtils.amount(r.getWorkMinutes(), r.getHourSalary()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Integer settleStatus = settleStatusOf(session.userId(), range.month());

        return new MonthSubsidyView(
                session.userId(),
                safeUserName(session.userId(), token),
                range.month(),
                records.size(),
                CalcUtils.workHours(minutes),
                CalcUtils.money(amount),
                settleStatus,
                SubsidyStatus.nameOf(settleStatus));
    }

    /**
     * 该学生本月发放单的整体状态（跨部门汇总）：
     * 没有任何发放单 = null（未结算）；全部已发放 = 1；否则 = 0（还有待发放）。
     */
    private Integer settleStatusOf(Long userId, String month) {
        List<Subsidy> slips = subsidyRepository.findByUserIdAndSettleMonth(userId, month);
        if (slips.isEmpty()) {
            return null;
        }
        boolean allPaid = slips.stream()
                .allMatch(s -> s.getStatus() != null && s.getStatus() == SubsidyStatus.PAID);
        return allPaid ? SubsidyStatus.PAID : SubsidyStatus.PENDING;
    }

    /**
     * 部门月度汇总。
     * 部门管理员只能查本部门（传别的部门直接 403）；助学中心管理员必须指定 deptId。
     */
    @Transactional(readOnly = true)
    public DeptSummaryView deptSummary(String token, String month, Long deptIdParam) {
        AuthClient.Session session = authGuard.requireAnyRole(
                token, Roles.DEPT_ADMIN, Roles.CENTER_ADMIN);

        Long deptId;
        if (session.roleId() == Roles.DEPT_ADMIN) {
            // deptId 一律从权限服务取，前端传什么都不作数（防越权）
            Long ownDeptId = authGuard.requireDeptAdmin(token, session).deptId();
            if (deptIdParam != null && !deptIdParam.equals(ownDeptId)) {
                throw new BusinessException(403, "权限不足：只能查看本部门的工时汇总");
            }
            deptId = ownDeptId;
        } else {
            if (deptIdParam == null) {
                throw new BusinessException(400, "助学中心管理员查询部门汇总时需指定部门编号 deptId");
            }
            deptId = deptIdParam;
        }

        MonthRange range = MonthRange.of(month);
        List<AttendanceRecord> records = recordRepository
                .findDeptMonthRecords(deptId, AttendanceStatus.CHECKED_OUT, range.from(), range.to());

        Map<Long, List<AttendanceRecord>> byUser = new LinkedHashMap<>();
        for (AttendanceRecord r : records) {
            byUser.computeIfAbsent(r.getUserId(), k -> new ArrayList<>()).add(r);
        }

        List<PersonSummary> people = new ArrayList<>();
        int totalMinutes = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Map.Entry<Long, List<AttendanceRecord>> entry : byUser.entrySet()) {
            int minutes = entry.getValue().stream()
                    .mapToInt(r -> r.getWorkMinutes() == null ? 0 : r.getWorkMinutes())
                    .sum();
            BigDecimal amount = entry.getValue().stream()
                    .map(r -> CalcUtils.amount(r.getWorkMinutes(), r.getHourSalary()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalMinutes += minutes;
            totalAmount = totalAmount.add(amount);
            people.add(new PersonSummary(
                    entry.getKey(),
                    safeUserName(entry.getKey(), token),
                    entry.getValue().size(),
                    CalcUtils.workHours(minutes),
                    CalcUtils.money(amount)));
        }
        // 金额高的排前面，方便管理员一眼看到用工大户
        people.sort(Comparator.comparing(PersonSummary::totalAmount).reversed());

        return new DeptSummaryView(
                deptId,
                range.month(),
                people.size(),
                CalcUtils.workHours(totalMinutes),
                CalcUtils.money(totalAmount),
                people);
    }

    /** 全校月度汇总（按部门下钻到部门粒度，不下钻到个人） */
    @Transactional(readOnly = true)
    public SchoolSummaryView schoolSummary(String token, String month) {
        authGuard.requireRole(token, Roles.CENTER_ADMIN);
        MonthRange range = MonthRange.of(month);

        List<AttendanceRecord> records = recordRepository
                .findMonthRecordsByStatus(
                        AttendanceStatus.CHECKED_OUT, range.from(), range.to());

        Map<Long, List<AttendanceRecord>> byDept = new LinkedHashMap<>();
        for (AttendanceRecord r : records) {
            byDept.computeIfAbsent(r.getDeptId(), k -> new ArrayList<>()).add(r);
        }

        List<SchoolSummaryView.DeptItem> items = new ArrayList<>();
        Set<Long> allUsers = new LinkedHashSet<>();
        int totalMinutes = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Map.Entry<Long, List<AttendanceRecord>> entry : byDept.entrySet()) {
            int minutes = entry.getValue().stream()
                    .mapToInt(r -> r.getWorkMinutes() == null ? 0 : r.getWorkMinutes())
                    .sum();
            BigDecimal amount = entry.getValue().stream()
                    .map(r -> CalcUtils.amount(r.getWorkMinutes(), r.getHourSalary()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Set<Long> users = new LinkedHashSet<>();
            entry.getValue().forEach(r -> users.add(r.getUserId()));
            allUsers.addAll(users);

            totalMinutes += minutes;
            totalAmount = totalAmount.add(amount);
            items.add(new SchoolSummaryView.DeptItem(
                    entry.getKey(),
                    users.size(),
                    CalcUtils.workHours(minutes),
                    CalcUtils.money(amount)));
        }
        items.sort(Comparator.comparing(SchoolSummaryView.DeptItem::deptId));

        return new SchoolSummaryView(
                range.month(),
                items.size(),
                allUsers.size(),
                CalcUtils.workHours(totalMinutes),
                CalcUtils.money(totalAmount),
                items);
    }

    /**
     * 取用户姓名用于展示。
     * 汇总类接口不该因为权限服务偶尔查不到某个用户就整体失败，所以这里降级为空字符串。
     */
    private String safeUserName(Long userId, String token) {
        try {
            return authClient.getUserInfo(userId, token).name();
        } catch (RuntimeException e) {
            log.warn("查询用户姓名失败，userId={}，降级为空", userId);
            return "";
        }
    }
}
