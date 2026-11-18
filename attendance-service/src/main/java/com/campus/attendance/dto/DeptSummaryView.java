package com.campus.attendance.dto;

import java.math.BigDecimal;
import java.util.List;

/** 部门月度工时汇总（部门管理员看本部门，助学中心管理员指定部门看） */
public record DeptSummaryView(
        Long deptId,
        String month,
        Integer personCount,
        BigDecimal totalHours,
        BigDecimal totalAmount,
        List<PersonSummary> list
) {
}
