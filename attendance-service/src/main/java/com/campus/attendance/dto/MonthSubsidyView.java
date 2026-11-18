package com.campus.attendance.dto;

import java.math.BigDecimal;

/** 学生月度工时补贴（"我要拿多少钱"） */
public record MonthSubsidyView(
        Long userId,
        String userName,
        String month,
        Integer recordCount,
        BigDecimal totalHours,
        BigDecimal totalAmount
) {
}
