package com.campus.job.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 学生投递岗位请求体：POST /api/job/applyJob
 *
 * userId 按接口文档传入，但服务端会校验它必须等于 Token 中的用户，
 * 防止学生 A 冒充学生 B 投递（越权拦截）。
 */
public class ApplyJobRequest {

    @NotNull(message = "岗位 id 不能为空")
    private Long jobId;

    @NotNull(message = "用户 id 不能为空")
    private Long userId;

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
