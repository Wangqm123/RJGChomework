package com.campus.attendance.dto;

import com.campus.attendance.common.AttendanceStatus;
import com.campus.attendance.common.CalcUtils;
import com.campus.attendance.entity.AttendanceRecord;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 签到记录返回体（"我的记录"列表用）。
 * 同时给出 workHours（小时，1 位小数）和 amount（金额，2 位小数），前端可直接展示。
 */
public record RecordView(
        Long recordId,
        Long jobId,
        String jobName,
        BigDecimal hourSalary,
        LocalDateTime checkInTime,
        LocalDateTime checkOutTime,
        BigDecimal workHours,
        BigDecimal amount,
        Integer status,
        String statusName,
        String remark
) {

    public static RecordView from(AttendanceRecord r) {
        return new RecordView(
                r.getId(),
                r.getJobId(),
                r.getJobName(),
                r.getHourSalary(),
                r.getCheckInTime(),
                r.getCheckOutTime(),
                CalcUtils.workHours(r.getWorkMinutes()),
                CalcUtils.amount(r.getWorkMinutes(), r.getHourSalary()),
                r.getStatus(),
                AttendanceStatus.nameOf(r.getStatus()),
                r.getRemark());
    }
}
