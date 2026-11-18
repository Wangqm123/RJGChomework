package com.campus.attendance.dto;

import java.math.BigDecimal;

/** 部门汇总里的"单个学生"条目 */
public record PersonSummary(
        Long userId,
        String userName,
        Integer recordCount,
        BigDecimal totalHours,
        BigDecimal totalAmount
) {
}
