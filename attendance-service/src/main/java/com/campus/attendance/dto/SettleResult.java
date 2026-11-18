package com.campus.attendance.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 月度结算结果。
 *
 * 三个计数字段是本接口的重点，前端/答辩都要能讲清：
 * - createdCount   新建发放单（第一次结算该学生该月）
 * - refreshedCount 刷新已有的"待发放"单（结算后有人补录工时，重新核算）
 * - skippedCount   跳过已发放的单（钱已经打了，金额冻结，不允许改）
 *
 * totalHours / totalAmount 只统计**本次实际写入**的单（不含 skipped），
 * 这样"结算结果"和"账上新增应付"始终一致。
 */
public record SettleResult(
        String month,
        Long deptId,
        Integer userCount,
        Integer createdCount,
        Integer refreshedCount,
        Integer skippedCount,
        BigDecimal totalHours,
        BigDecimal totalAmount,
        List<SubsidyView> list
) {
}
