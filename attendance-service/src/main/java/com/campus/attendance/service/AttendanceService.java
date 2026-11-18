package com.campus.attendance.service;

import com.campus.attendance.client.AuthClient;
import com.campus.attendance.client.JobClient;
import com.campus.attendance.common.AttendanceStatus;
import com.campus.attendance.common.BusinessException;
import com.campus.attendance.common.CalcUtils;
import com.campus.attendance.common.MonthRange;
import com.campus.attendance.common.Roles;
import com.campus.attendance.dto.CheckInResult;
import com.campus.attendance.dto.CheckOutResult;
import com.campus.attendance.dto.ConfirmResult;
import com.campus.attendance.dto.MyRecordView;
import com.campus.attendance.dto.RecordView;
import com.campus.attendance.entity.AttendanceRecord;
import com.campus.attendance.repository.AttendanceRecordRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 签到/签退/我的记录。
 *
 * 签到是本服务的核心校验链路（需求文档 3.3），必须过两道关才允许落库：
 *   ① 调权限服务 checkToken → 401 直接拒绝，拿到 userId
 *   ② 调岗位服务 getStudentHired → false 直接拒绝（没投递、被拒绝、待审核都不行）
 * 岗位服务不可用时不会"放行"，而是返回 500 友好提示——宁可打不了卡，也不能让未录用的人混进来。
 */
@Service
public class AttendanceService {

    /** 单次工时上限：超过 12 小时标记为异常待确认，人工核实后才计薪 */
    private static final int MAX_WORK_MINUTES = 12 * 60;

    /** 岗位已下架（与岗位服务 JobStatus 字典一致），下架岗位不能继续签到 */
    private static final int JOB_OFFLINE = 5;

    /** "我的记录"默认每页条数与上限：不传就按 20 条/页，防止一次拉出整月几百条把前端拖死 */
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final AttendanceRecordRepository recordRepository;
    private final AuthGuard authGuard;
    private final JobClient jobClient;

    public AttendanceService(AttendanceRecordRepository recordRepository,
                             AuthGuard authGuard,
                             JobClient jobClient) {
        this.recordRepository = recordRepository;
        this.authGuard = authGuard;
        this.jobClient = jobClient;
    }

    /**
     * 学生签到。
     * userId 只从 Token 取，不接受请求体传参，防止冒用他人身份打卡。
     */
    @Transactional
    public CheckInResult checkIn(String token, Long jobId) {
        AuthClient.Session session = authGuard.requireRole(token, Roles.STUDENT);
        Long userId = session.userId();

        // 关 1：岗位必须存在（不存在时岗位服务返回 400，这里原样透传提示）
        JobClient.JobInfo job = jobClient.getJob(jobId);
        if (job.status() != null && job.status() == JOB_OFFLINE) {
            throw new BusinessException(400, "岗位已下架，无法签到");
        }

        // 关 1.5：岗位数据必须完整。deptId/hourSalary 是快照的来源，缺了会写出"部门 0、时薪 0"的
        // 脏记录（既不进任何部门汇总，也会把工时算成 0 元），宁可签到失败也不落这种数据。
        // 这是上游数据异常，按 500 处理（不是学生的错，提示联系管理员）。
        if (job.deptId() == null || job.deptId() <= 0) {
            throw new BusinessException(500, "岗位数据异常：未取到所属部门，暂不能签到，请联系管理员");
        }
        if (job.hourSalary() == null) {
            throw new BusinessException(500, "岗位数据异常：未取到时薪，暂不能签到，请联系管理员");
        }

        // 关 2：必须被该岗位录用（跨服务校验，本服务不保存录用关系）
        if (!jobClient.isStudentHired(jobId, userId)) {
            throw new BusinessException(400, "您未被该岗位录用，无法签到");
        }

        // 关 3：同一岗位不能有未签退的签到（防重复打卡、防工时重复计算）
        if (recordRepository.findFirstByUserIdAndJobIdAndStatusOrderByCheckInTimeDesc(
                userId, jobId, AttendanceStatus.CHECKED_IN).isPresent()) {
            throw new BusinessException(400, "您在该岗位已签到且未签退，请先签退");
        }

        LocalDateTime now = LocalDateTime.now();
        AttendanceRecord record = new AttendanceRecord();
        record.setJobId(jobId);
        record.setUserId(userId);
        // 岗位名、时薪、所属部门在这里**快照**：日后岗位调薪或下架都不影响这条历史工时
        record.setDeptId(job.deptId());
        record.setJobName(job.jobName());
        record.setHourSalary(job.hourSalary());
        record.setCheckInTime(now);
        record.setStatus(AttendanceStatus.CHECKED_IN);
        // 未签退期间的排他键：并发请求里只有一个能插入成功，其余被唯一索引拒绝
        record.setActiveKey(AttendanceRecord.buildActiveKey(userId, jobId));
        try {
            // 用 saveAndFlush 让唯一索引冲突在这里立刻暴露，好转换成 400 而不是 500
            recordRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException e) {
            // 上面的"关 3"只能挡住先后请求，挡不住同时到达的两个请求；
            // 真正兜底的是 uk_att_active 唯一索引，冲突说明对方请求已抢先落库
            throw new BusinessException(400, "您在该岗位已签到且未签退，请先签退");
        }

        return new CheckInResult(
                record.getId(),
                jobId,
                job.jobName(),
                job.deptId(),
                job.hourSalary(),
                now,
                AttendanceStatus.CHECKED_IN,
                AttendanceStatus.nameOf(AttendanceStatus.CHECKED_IN));
    }

    /**
     * 学生签退：回填签退时间和工时，按是否超过 12 小时区分「已签退」与「异常待确认」。
     */
    @Transactional
    public CheckOutResult checkOut(String token, Long jobId) {
        AuthClient.Session session = authGuard.requireRole(token, Roles.STUDENT);
        Long userId = session.userId();

        AttendanceRecord record = recordRepository
                .findFirstByUserIdAndJobIdAndStatusOrderByCheckInTimeDesc(
                        userId, jobId, AttendanceStatus.CHECKED_IN)
                .orElseThrow(() -> new BusinessException(400, "未找到该岗位的签到记录，请先签到"));

        LocalDateTime now = LocalDateTime.now();
        long minutes = Duration.between(record.getCheckInTime(), now).toMinutes();
        if (minutes < 1) {
            throw new BusinessException(400, "签到时长不足 1 分钟，请稍后再签退");
        }

        int workMinutes = (int) minutes;
        boolean abnormal = workMinutes > MAX_WORK_MINUTES;

        record.setCheckOutTime(now);
        record.setWorkMinutes(workMinutes);
        record.setStatus(abnormal ? AttendanceStatus.ABNORMAL : AttendanceStatus.CHECKED_OUT);
        record.setRemark(abnormal ? "单次工时超过 12 小时，需管理员核实后计薪" : null);
        // 释放排他键：签退后该岗位可以再次签到（历史多次打卡记录不受唯一索引影响）
        record.setActiveKey(null);
        recordRepository.save(record);

        String tip = abnormal
                ? "本次工时超过 12 小时，已标记为异常待确认，核实通过后才计入补贴"
                : null;
        return new CheckOutResult(
                record.getId(),
                jobId,
                record.getCheckInTime(),
                now,
                workMinutes,
                CalcUtils.workHours(workMinutes),
                CalcUtils.amount(workMinutes, record.getHourSalary()),
                record.getStatus(),
                AttendanceStatus.nameOf(record.getStatus()),
                tip);
    }

    /**
     * 我的签到记录（按月，支持分页）。
     *
     * 合计只算已签退的记录，未签退/异常待确认/核实不通过分别给数量，避免学生误以为已经算钱。
     * page/size 不传时默认第 1 页、20 条/页。
     *
     * 为什么在内存里切页而不是 SQL 分页：合计必须基于整月全量数据（否则第 2 页的金额会变小），
     * 一次查询拿到整月记录既省一次往返，也保证"明细翻页、合计不变"；单月记录量在几十条量级，
     * 内存开销可忽略。若将来单月记录上万，再拆成"统计查询 + 分页查询"两条 SQL。
     */
    @Transactional(readOnly = true)
    public MyRecordView myRecord(String token, String month, Integer page, Integer size) {
        AuthClient.Session session = authGuard.requireRole(token, Roles.STUDENT);
        MonthRange range = MonthRange.of(month);

        int pageNo = (page == null) ? 1 : page;
        int pageSize = (size == null) ? DEFAULT_PAGE_SIZE : size;
        if (pageNo < 1) {
            throw new BusinessException(400, "页码 page 必须大于 0");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(400, "每页条数 size 必须在 1~" + MAX_PAGE_SIZE + " 之间");
        }

        List<AttendanceRecord> records = recordRepository
                .findUserMonthRecords(session.userId(), range.from(), range.to());

        int settledCount = 0;
        int pendingCount = 0;
        int rejectedCount = 0;
        int settledMinutes = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (AttendanceRecord r : records) {
            int status = (r.getStatus() == null ? AttendanceStatus.CHECKED_IN : r.getStatus());
            if (status == AttendanceStatus.CHECKED_OUT) {
                settledCount++;
                settledMinutes += (r.getWorkMinutes() == null ? 0 : r.getWorkMinutes());
                totalAmount = totalAmount.add(CalcUtils.amount(r.getWorkMinutes(), r.getHourSalary()));
            } else if (status == AttendanceStatus.REJECTED) {
                rejectedCount++;
            } else {
                pendingCount++;
            }
        }

        int total = records.size();
        int totalPages = (total + pageSize - 1) / pageSize;
        // 用 long 算偏移：page 传个很大的数时不会 int 溢出成负数
        int fromIndex = (int) Math.min((long) (pageNo - 1) * pageSize, total);
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<RecordView> views = records.subList(fromIndex, toIndex).stream()
                .map(RecordView::from)
                .toList();

        return new MyRecordView(
                range.month(),
                total,
                settledCount,
                pendingCount,
                rejectedCount,
                CalcUtils.workHours(settledMinutes),
                CalcUtils.money(totalAmount),
                pageNo,
                pageSize,
                totalPages,
                views);
    }

    /**
     * 异常工时核实（部门管理员核实本部门 / 助学中心管理员核实全校）。
     *
     * 背景：签退时单次工时超过 12 小时会被标记为「异常待确认」并排除在计薪之外，
     * 但必须有人能把它"落定"，否则这条记录永远悬着、学生永远拿不到钱。
     *
     * pass=true  → status 1（已签退），这条工时进入补贴与结算；
     * pass=false → status 3（核实不通过），不计薪，但记录保留可追溯，不物理删除。
     *
     * 已核实的记录再调一次直接 400：核实是留痕动作，不允许反复改结论（要改只能走人工修数）。
     */
    @Transactional
    public ConfirmResult confirmAbnormal(String token, Long recordId, Boolean pass, String remark) {
        AuthClient.Session session = authGuard.requireAnyRole(
                token, Roles.DEPT_ADMIN, Roles.CENTER_ADMIN);

        AttendanceRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new BusinessException(400, "签到记录不存在：recordId=" + recordId));

        // 部门管理员只能核实自己部门的记录；中心管理员不限制部门
        if (session.roleId() == Roles.DEPT_ADMIN) {
            Long ownDeptId = authGuard.requireDeptAdmin(session).deptId();
            if (!ownDeptId.equals(record.getDeptId())) {
                throw new BusinessException(403, "权限不足：只能核实本部门的工时记录");
            }
        }

        Integer current = record.getStatus();
        if (current == null || current != AttendanceStatus.ABNORMAL) {
            throw new BusinessException(400, "只有「异常待确认」的记录需要核实，当前状态："
                    + AttendanceStatus.nameOf(current));
        }

        boolean accept = Boolean.TRUE.equals(pass);
        int target = accept ? AttendanceStatus.CHECKED_OUT : AttendanceStatus.REJECTED;
        String note = (remark == null || remark.isBlank()) ? "" : "：" + remark.trim();
        String newRemark = (accept ? "异常核实通过，已计入补贴" : "异常核实不通过，不计薪") + note;
        LocalDateTime confirmTime = LocalDateTime.now();

        // 条件更新兜底并发：两个管理员同时点核实时，只有第一个能把 status=2 改掉，
        // 第二个影响 0 行 → 400，不会出现"后点的把先点的结论覆盖掉"
        int affected = recordRepository.markConfirmed(
                recordId, target, newRemark, session.userId(), confirmTime);
        if (affected == 0) {
            throw new BusinessException(400, "该记录已被其他管理员核实，请刷新后查看最新状态");
        }

        BigDecimal amount = accept
                ? CalcUtils.amount(record.getWorkMinutes(), record.getHourSalary())
                : CalcUtils.money(BigDecimal.ZERO);
        String tip = accept
                ? "已计入补贴；若该月已结算，请重新结算一次以刷新待发放单金额"
                : "已标记为核实不通过，不计薪；记录保留，可追溯谁在何时核实";

        return new ConfirmResult(
                record.getId(),
                record.getUserId(),
                record.getJobId(),
                record.getDeptId(),
                target,
                AttendanceStatus.nameOf(target),
                record.getWorkMinutes(),
                amount,
                session.userId(),
                confirmTime,
                tip);
    }
}
