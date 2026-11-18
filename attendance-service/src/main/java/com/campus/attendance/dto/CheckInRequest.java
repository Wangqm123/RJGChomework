package com.campus.attendance.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 签到请求。
 * userId 不从请求体取——由 Token 解析得出，防止冒用他人身份打卡。
 */
public record CheckInRequest(
        @NotNull(message = "岗位编号不能为空") Long jobId
) {
}
