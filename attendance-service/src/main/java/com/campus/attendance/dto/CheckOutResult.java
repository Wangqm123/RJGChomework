package com.campus.attendance.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 签退成功返回体：直接给出本次工时与应得金额，前端无需自己算 */
public record CheckOutResult(
        Long recordId,
        Long jobId,
        LocalDateTime checkInTime,
        LocalDateTime checkOutTime,
        Integer workMinutes,
        BigDecimal workHours,
        BigDecimal amount,
        Integer status,
        String statusName,
        String tip
) {
}
