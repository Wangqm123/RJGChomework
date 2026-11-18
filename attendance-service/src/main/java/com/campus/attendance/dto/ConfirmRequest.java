package com.campus.attendance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 异常工时核实请求（部门管理员核实本部门 / 助学中心管理员核实全校）。
 *
 * pass = true   核实通过，这段工时计入补贴（status 2 → 1）；
 * pass = false  核实不通过，标记作废（status 2 → 3），不计薪但记录保留可追溯。
 *
 * remark 会拼进签到记录的 remark，所以限制长度（库里该列是 VARCHAR(200)）。
 */
public record ConfirmRequest(
        @NotNull(message = "记录编号不能为空") Long recordId,

        @NotNull(message = "请明确核实结果（pass=true 通过 / false 不通过）") Boolean pass,

        @Size(max = 100, message = "核实说明不能超过 100 字") String remark
) {
}
