package com.campus.attendance.dto;

import java.math.BigDecimal;

/** 批量发放结果：本次实际打款的人数与金额 */
public record PayResult(
        String month,
        Long deptId,
        Integer paidCount,
        BigDecimal paidAmount,
        String tip
) {
}
