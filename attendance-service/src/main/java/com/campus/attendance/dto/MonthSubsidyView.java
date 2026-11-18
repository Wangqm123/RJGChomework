package com.campus.attendance.dto;

import java.math.BigDecimal;

/**
 * 学生月度工时补贴（"我要拿多少钱"）。
 *
 * 金额是**实时核算**的结果；settleStatus 是"这笔钱有没有走完结算/发放流程"：
 *   null = 该月尚未结算（还没生成发放单，钱只是账面上的应得）
 *   0    = 已结算待发放（金额已固化，等助学中心打款）
 *   1    = 已发放（多个部门都有单时，需全部发放才算已发放）
 * 两个数字可能不一致是正常的：结算后又补录了工时，实时核算会变，但已发放的单不会变。
 */
public record MonthSubsidyView(
        Long userId,
        String userName,
        String month,
        Integer recordCount,
        BigDecimal totalHours,
        BigDecimal totalAmount,
        Integer settleStatus,
        String settleStatusName
) {
}
