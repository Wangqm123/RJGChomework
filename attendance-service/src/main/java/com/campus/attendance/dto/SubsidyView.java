package com.campus.attendance.dto;

import com.campus.attendance.common.CalcUtils;
import com.campus.attendance.common.SubsidyStatus;
import com.campus.attendance.entity.Subsidy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 月度补贴发放单返回体（发放单列表、结算结果里的人员明细都用它）。
 */
public record SubsidyView(
        Long subsidyId,
        Long userId,
        String userName,
        Long deptId,
        String month,
        Integer recordCount,
        BigDecimal totalHours,
        BigDecimal totalAmount,
        Integer status,
        String statusName,
        LocalDateTime settleTime,
        LocalDateTime payTime
) {

    public static SubsidyView from(Subsidy s, String userName) {
        return new SubsidyView(
                s.getId(),
                s.getUserId(),
                userName == null ? "" : userName,
                s.getDeptId(),
                s.getSettleMonth(),
                s.getRecordCount(),
                CalcUtils.workHours(s.getWorkMinutes()),
                CalcUtils.money(s.getTotalAmount()),
                s.getStatus(),
                SubsidyStatus.nameOf(s.getStatus()),
                s.getSettleTime(),
                s.getPayTime());
    }
}
