package com.campus.job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 助学中心审核岗位请求体：POST /api/job/auditJob
 *
 * auditResult 只接受 pass（通过上架）/ reject（驳回）；
 * 驳回时 auditRemark 必填，由业务层校验（通过时意见可为空）。
 */
public class AuditJobRequest {

    @NotNull(message = "岗位 id 不能为空")
    private Long jobId;

    @NotBlank(message = "审核结果不能为空")
    @Pattern(regexp = "pass|reject", message = "审核结果只能是 pass 或 reject")
    private String auditResult;

    /** 审核意见（驳回时必填） */
    @Size(max = 200, message = "审核意见不能超过 200 字")
    private String auditRemark;

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public String getAuditResult() {
        return auditResult;
    }

    public void setAuditResult(String auditResult) {
        this.auditResult = auditResult;
    }

    public String getAuditRemark() {
        return auditRemark;
    }

    public void setAuditRemark(String auditRemark) {
        this.auditRemark = auditRemark;
    }
}
