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
import com.campus.attendance.dto.MyRecordView;
import com.campus.attendance.dto.RecordView;
import com.campus.attendance.entity.AttendanceRecord;
import com.campus.attendance.repository.AttendanceRecordRepository;
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
        recordRepository.save(record);

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
     * 我的签到记录（按月）。
     * 合计只算已签退的记录，未签退/异常待确认单独给数量，避免学生误以为已经算钱。
     */
    @Transactional(readOnly = true)
    public MyRecordView myRecord(String token, String month) {
        AuthClient.Session session = authGuard.requireRole(token, Roles.STUDENT);
        MonthRange range = MonthRange.of(month);

        List<AttendanceRecord> records = recordRepository
                .findByUserIdAndCheckInTimeBetweenOrderByCheckInTimeDesc(
                        session.userId(), range.from(), range.to());

        int settledCount = 0;
        int pendingCount = 0;
        int settledMinutes = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (AttendanceRecord r : records) {
            if (r.getStatus() != null && r.getStatus() == AttendanceStatus.CHECKED_OUT) {
                settledCount++;
                settledMinutes += (r.getWorkMinutes() == null ? 0 : r.getWorkMinutes());
                totalAmount = totalAmount.add(CalcUtils.amount(r.getWorkMinutes(), r.getHourSalary()));
            } else {
                pendingCount++;
            }
        }

        List<RecordView> views = records.stream().map(RecordView::from).toList();
        return new MyRecordView(
                range.month(),
                records.size(),
                settledCount,
                pendingCount,
                CalcUtils.workHours(settledMinutes),
                CalcUtils.money(totalAmount),
                views);
    }
}
