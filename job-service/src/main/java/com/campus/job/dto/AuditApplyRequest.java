package com.campus.job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 部门管理员审核学生申请请求体：POST /api/job/auditApply
 *
 * applyResult 只接受 accept（录用）/ refuse（拒绝），remark 为审核意见。
 */
public class AuditApplyRequest {

    @NotNull(message = "申请记录 id 不能为空")
    private Long applyId;

    @NotBlank(message = "审核结果不能为空")
    @Pattern(regexp = "accept|refuse", message = "审核结果只能是 accept 或 refuse")
    private String applyResult;

    /** 审核意见 */
    @Size(max = 200, message = "审核意见不能超过 200 字")
    private String remark;

    public Long getApplyId() {
        return applyId;
    }

    public void setApplyId(Long applyId) {
        this.applyId = applyId;
    }

    public String getApplyResult() {
        return applyResult;
    }

    public void setApplyResult(String applyResult) {
        this.applyResult = applyResult;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
