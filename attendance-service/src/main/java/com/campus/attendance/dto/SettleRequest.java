package com.campus.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 月度结算请求（助学中心管理员）。
 *
 * month 必填且必须显式指定：结算是不可逆的留痕动作，不做"不传就默认当月"，
 * 避免误点一次就把本月账结掉。
 * deptId 不传 = 全校结算；传了 = 只结算该部门。
 * remark 会写进发放单备注（VARCHAR(200)），限制长度避免超长直接撞数据库约束。
 */
public record SettleRequest(
        @NotBlank(message = "结算月份不能为空")
        @Pattern(regexp = "\\d{4}-\\d{2}", message = "月份格式应为 yyyy-MM，例如 2026-11")
        String month,

        Long deptId,

        @Size(max = 100, message = "结算备注不能超过 100 字")
        String remark
) {
}
