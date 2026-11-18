package com.campus.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 批量发放请求（助学中心管理员）。
 * deptId 不传 = 发放全校该月所有待发放单；传了 = 只发放该部门的。
 */
public record PayRequest(
        @NotBlank(message = "发放月份不能为空")
        @Pattern(regexp = "\\d{4}-\\d{2}", message = "月份格式应为 yyyy-MM，例如 2026-11")
        String month,

        Long deptId
) {
}
