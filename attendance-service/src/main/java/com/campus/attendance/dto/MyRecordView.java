package com.campus.attendance.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * "我的记录"返回体：当页明细 + 整月合计。
 *
 * 合计只统计 status=1（已签退）的记录；未签退/异常待确认/核实不通过分别给数量，
 * 避免学生误以为已经算钱。
 *
 * 分页说明：合计基于**整月全量**计算，不随翻页变化，list 只是当前页；
 * 因此不会出现"翻到第 2 页金额变小"的错觉。
 */
public record MyRecordView(
        String month,
        Integer recordCount,
        Integer settledCount,
        Integer pendingCount,
        Integer rejectedCount,
        BigDecimal totalHours,
        BigDecimal totalAmount,
        Integer page,
        Integer size,
        Integer totalPages,
        List<RecordView> list
) {
}
