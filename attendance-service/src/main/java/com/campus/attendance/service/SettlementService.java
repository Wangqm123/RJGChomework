package com.campus.attendance.service;

import com.campus.attendance.client.AuthClient;
import com.campus.attendance.common.AttendanceStatus;
import com.campus.attendance.common.BusinessException;
import com.campus.attendance.common.CalcUtils;
import com.campus.attendance.common.MonthRange;
import com.campus.attendance.common.Roles;
import com.campus.attendance.common.SubsidyStatus;
import com.campus.attendance.dto.PayResult;
import com.campus.attendance.dto.SettleResult;
import com.campus.attendance.dto.SubsidyView;
import com.campus.attendance.entity.AttendanceRecord;
import com.campus.attendance.entity.Subsidy;
import com.campus.attendance.repository.AttendanceRecordRepository;
import com.campus.attendance.repository.SubsidyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 月度补贴结算与发放（"核算 → 留痕 → 打款"的闭环）。
 *
 * 与 SubsidyService 的区别（答辩最容易混淆的一点）：
 *   - SubsidyService 是**实时核算视图**：每次查询都按当前签到记录重算，不落库；
 *   - SettlementService 是**结算动作**：把某月某部门的已签退工时固化成发放单（t_subsidy），
 *     之后打款、对账都以发放单为准，补录工时不会影响已发放的金额。
 *
 * 结算规则：
 *   1. 只有 status=1（已签退）的记录参与结算；未签退、异常待确认不计；
 *   2. 一个人在一个月内为多个部门打过工，会生成多张单（按部门分开核算），
 *      唯一键是 (user_id, dept_id, settle_month)，部门对账时"发放单合计 = 部门汇总"；
 *   3. 已存在的"待发放"单允许刷新（有人补录工时后重新算），"已发放"单直接跳过、金额冻结；
 *   4. 并发重复结算由数据库唯一键兜底，冲突时整体回滚并提示重试，不会生成两张单。
 */
@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final AttendanceRecordRepository recordRepository;
    private final SubsidyRepository subsidyRepository;
    private final AuthGuard authGuard;
    private final AuthClient authClient;

    public SettlementService(AttendanceRecordRepository recordRepository,
                             SubsidyRepository subsidyRepository,
                             AuthGuard authGuard,
                             AuthClient authClient) {
        this.recordRepository = recordRepository;
        this.subsidyRepository = subsidyRepository;
        this.authGuard = authGuard;
        this.authClient = authClient;
    }

    /**
     * 月度结算：把指定月份（可限定部门）的已签退工时固化为发放单。
     * 仅助学中心管理员可执行——钱由中心统一核算发放，部门管理员只负责用工和查看。
     */
    @Transactional
    public SettleResult settle(String token, String month, Long deptIdParam, String remark) {
        AuthClient.Session session = authGuard.requireRole(token, Roles.CENTER_ADMIN);
        MonthRange range = MonthRange.of(month);

        List<AttendanceRecord> records = (deptIdParam == null)
                ? recordRepository.findByStatusAndCheckInTimeBetweenOrderByCheckInTimeAsc(
                        AttendanceStatus.CHECKED_OUT, range.from(), range.to())
                : recordRepository.findByDeptIdAndStatusAndCheckInTimeBetweenOrderByCheckInTimeAsc(
                        deptIdParam, AttendanceStatus.CHECKED_OUT, range.from(), range.to());

        // 按 "人 + 部门" 分组：同一个人跨部门打工时，各部门各自一张单
        Map<String, List<AttendanceRecord>> groups = new LinkedHashMap<>();
        for (AttendanceRecord r : records) {
            String key = r.getUserId() + ":" + r.getDeptId();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        if (groups.isEmpty()) {
            // 没有可结算工时：如果之前已经结算过，把现有单原样返回（方便前端展示），否则明确报错
            List<Subsidy> existed = queryByMonthAndDept(range.month(), deptIdParam);
            if (existed.isEmpty()) {
                throw new BusinessException(400, "该月没有可结算的已签退工时记录，无法生成发放单");
            }
            List<SubsidyView> views = existed.stream()
                    .map(s -> SubsidyView.from(s, safeUserName(s.getUserId())))
                    .toList();
            return new SettleResult(range.month(), deptIdParam, views.size(),
                    0, 0, 0, CalcUtils.workHours(0), CalcUtils.money(BigDecimal.ZERO), views);
        }

        LocalDateTime now = LocalDateTime.now();
        int created = 0;
        int refreshed = 0;
        int skipped = 0;
        int writtenMinutes = 0;
        BigDecimal writtenAmount = BigDecimal.ZERO;
        List<SubsidyView> views = new ArrayList<>();

        for (Map.Entry<String, List<AttendanceRecord>> entry : groups.entrySet()) {
            List<AttendanceRecord> group = entry.getValue();
            AttendanceRecord first = group.get(0);
            Long userId = first.getUserId();
            Long deptId = first.getDeptId();

            int minutes = group.stream()
                    .mapToInt(r -> r.getWorkMinutes() == null ? 0 : r.getWorkMinutes())
                    .sum();
            BigDecimal amount = group.stream()
                    .map(r -> CalcUtils.amount(r.getWorkMinutes(), r.getHourSalary()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            String recordIds = group.stream()
                    .map(r -> String.valueOf(r.getId()))
                    .collect(Collectors.joining(","));

            Subsidy subsidy = subsidyRepository
                    .findByUserIdAndDeptIdAndSettleMonth(userId, deptId, range.month())
                    .orElse(null);

            if (subsidy != null && subsidy.getStatus() != null
                    && subsidy.getStatus() == SubsidyStatus.PAID) {
                // 钱已经打了，金额冻结：不刷新、不计入本次合计，只回显
                skipped++;
                views.add(SubsidyView.from(subsidy, safeUserName(userId)));
                continue;
            }

            boolean isNew = (subsidy == null);
            if (isNew) {
                subsidy = new Subsidy();
                subsidy.setUserId(userId);
                subsidy.setDeptId(deptId);
                subsidy.setSettleMonth(range.month());
                subsidy.setStatus(SubsidyStatus.PENDING);
            }
            subsidy.setRecordCount(group.size());
            subsidy.setWorkMinutes(minutes);
            subsidy.setTotalAmount(CalcUtils.money(amount));
            subsidy.setRecordIds(recordIds);
            subsidy.setSettleTime(now);
            subsidy.setOperatorId(session.userId());
            if (remark != null && !remark.isBlank()) {
                subsidy.setRemark(remark.trim());
            }

            try {
                // saveAndFlush：让唯一键冲突在循环内立刻暴露，而不是拖到事务提交时才报错
                subsidyRepository.saveAndFlush(subsidy);
            } catch (DataIntegrityViolationException e) {
                log.warn("结算冲突：userId={}, deptId={}, month={}", userId, deptId, range.month(), e);
                throw new BusinessException(400, "本月结算正在被其他操作执行（或已存在重复发放单），请稍后重试");
            }

            if (isNew) {
                created++;
            } else {
                refreshed++;
            }
            writtenMinutes += minutes;
            writtenAmount = writtenAmount.add(amount);
            views.add(SubsidyView.from(subsidy, safeUserName(userId)));
        }

        log.info("月度结算完成：month={}, deptId={}, created={}, refreshed={}, skipped={}",
                range.month(), deptIdParam, created, refreshed, skipped);

        return new SettleResult(range.month(), deptIdParam, groups.size(),
                created, refreshed, skipped,
                CalcUtils.workHours(writtenMinutes), CalcUtils.money(writtenAmount), views);
    }

    /**
     * 批量发放：把该月（可限定部门）所有"待发放"的发放单标记为已发放。
     *
     * 这里用条件更新（只有 status=0 才更新）而不是"读出来改字段再 save"：
     * 两个管理员同时点发放时，第二次更新影响 0 行，不会把同一笔钱记两次，
     * 也不需要靠悲观锁。
     */
    @Transactional
    public PayResult pay(String token, String month, Long deptIdParam) {
        AuthClient.Session session = authGuard.requireRole(token, Roles.CENTER_ADMIN);
        String settleMonth = MonthRange.of(month).month();

        List<Subsidy> candidates = queryByMonthAndDept(settleMonth, deptIdParam).stream()
                .filter(s -> s.getStatus() != null && s.getStatus() == SubsidyStatus.PENDING)
                .toList();
        if (candidates.isEmpty()) {
            throw new BusinessException(400, "该月没有待发放的补贴单（尚未结算，或已全部发放）");
        }

        LocalDateTime now = LocalDateTime.now();
        int paidCount = 0;
        BigDecimal paidAmount = BigDecimal.ZERO;
        for (Subsidy s : candidates) {
            int affected = subsidyRepository.markPaid(s.getId(), now, session.userId());
            if (affected == 1) {
                paidCount++;
                paidAmount = paidAmount.add(s.getTotalAmount() == null ? BigDecimal.ZERO : s.getTotalAmount());
            }
        }

        log.info("补贴发放完成：month={}, deptId={}, paidCount={}, paidAmount={}",
                settleMonth, deptIdParam, paidCount, paidAmount);

        return new PayResult(settleMonth, deptIdParam, paidCount, CalcUtils.money(paidAmount),
                "已标记为已发放，请按发放单金额完成打款；已发放的金额不再随工时变动");
    }

    /**
     * 发放单列表：部门管理员只看本部门，中心管理员可看全校或指定部门。
     */
    @Transactional(readOnly = true)
    public List<SubsidyView> subsidyList(String token, String month, Long deptIdParam, Integer status) {
        AuthClient.Session session = authGuard.requireAnyRole(
                token, Roles.DEPT_ADMIN, Roles.CENTER_ADMIN);
        String settleMonth = MonthRange.of(month).month();

        Long deptId;
        if (session.roleId() == Roles.DEPT_ADMIN) {
            Long ownDeptId = authGuard.requireDeptAdmin(session).deptId();
            if (deptIdParam != null && !deptIdParam.equals(ownDeptId)) {
                throw new BusinessException(403, "权限不足：只能查看本部门的补贴发放单");
            }
            deptId = ownDeptId;
        } else {
            deptId = deptIdParam;
        }

        return queryByMonthAndDept(settleMonth, deptId).stream()
                .filter(s -> status == null || (s.getStatus() != null && s.getStatus().equals(status)))
                .map(s -> SubsidyView.from(s, safeUserName(s.getUserId())))
                .toList();
    }

    /** 按月（可限定部门）取发放单；发放单数量级很小（一人一月一张），状态过滤在内存里做即可 */
    private List<Subsidy> queryByMonthAndDept(String settleMonth, Long deptId) {
        return (deptId == null)
                ? subsidyRepository.findBySettleMonthOrderByDeptIdAscUserIdAsc(settleMonth)
                : subsidyRepository.findBySettleMonthAndDeptIdOrderByUserIdAsc(settleMonth, deptId);
    }

    /**
     * 取用户姓名用于展示。
     * 发放单列表不该因为权限服务偶尔查不到某个人就整体失败，所以这里降级为空字符串。
     */
    private String safeUserName(Long userId) {
        try {
            AuthClient.UserInfo info = authClient.getUserInfo(userId);
            return info == null ? "" : info.name();
        } catch (RuntimeException e) {
            log.warn("查询用户姓名失败，userId={}，降级为空", userId);
            return "";
        }
    }
}
