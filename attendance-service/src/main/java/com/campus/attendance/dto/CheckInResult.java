package com.campus.attendance.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 签到成功返回体 */
public record CheckInResult(
        Long recordId,
        Long jobId,
        String jobName,
        Long deptId,
        BigDecimal hourSalary,
        LocalDateTime checkInTime,
        Integer status,
        String statusName
) {
}
