package com.campus.attendance.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * "我的记录"返回体：记录列表 + 当月合计。
 * 合计只统计 status=1（已签退）的记录；未签退和异常待确认的单独给数量，避免学生误以为已经算钱。
 */
public record MyRecordView(
        String month,
        Integer recordCount,
        Integer settledCount,
        Integer pendingCount,
        BigDecimal totalHours,
        BigDecimal totalAmount,
        List<RecordView> list
) {
}
