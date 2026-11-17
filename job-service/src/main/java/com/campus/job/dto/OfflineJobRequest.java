package com.campus.job.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 下架岗位请求体：POST /api/job/offlineJob
 *
 * 与其余接口统一使用 JSON 请求体传参（不再用 query 参数），
 * 前端调用时 body 传 {"jobId": 1} 即可。
 */
public class OfflineJobRequest {

    @NotNull(message = "岗位 id 不能为空")
    private Long jobId;

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }
}
