package com.campus.attendance.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 异常工时核实结果。
 *
 * status 只会是 1（核实通过、计入补贴）或 3（核实不通过、作废不计薪）；
 * amount 在核实不通过时固定为 0.00，避免前端误展示成"应发金额"。
 * confirmOperatorId / confirmTime 是留痕字段，出争议时可回溯"谁在什么时候核实的"。
 */
public record ConfirmResult(
        Long recordId,
        Long userId,
        Long jobId,
        Long deptId,
        Integer status,
        String statusName,
        Integer workMinutes,
        BigDecimal amount,
        Long confirmOperatorId,
        LocalDateTime confirmTime,
        String tip
) {
}
