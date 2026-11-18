package com.campus.attendance.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 全校月度工时汇总（助学中心管理员看）。
 * 只下钻到"部门"粒度，不下钻到个人——中心关注总量，个人明细由部门管理员看，避免越权。
 */
public record SchoolSummaryView(
        String month,
        Integer deptCount,
        Integer personCount,
        BigDecimal totalHours,
        BigDecimal totalAmount,
        List<DeptItem> list
) {

    /** 全校汇总里的"单个部门"条目 */
    public record DeptItem(
            Long deptId,
            Integer personCount,
            BigDecimal totalHours,
            BigDecimal totalAmount
    ) {
    }
}
