package com.campus.attendance.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 签退请求。
 */
public record CheckOutRequest(
        @NotNull(message = "岗位编号不能为空") Long jobId
) {
}
