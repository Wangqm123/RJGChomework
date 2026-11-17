package com.campus.job.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 修改岗位请求体：POST /api/job/updateJob（需求文档 DeptAdmin-2「修改岗位」）
 *
 * 只更新传了值的字段（null 表示不修改）。
 * 接口文档初稿未列出该接口，属于按需求文档补充的实现，联调时可选用。
 */
public class UpdateJobRequest {

    @NotNull(message = "岗位 id 不能为空")
    private Long jobId;

    @Size(max = 100, message = "岗位名称不能超过 100 字")
    private String jobName;

    @Size(max = 200, message = "工作地点不能超过 200 字")
    private String address;

    @DecimalMin(value = "0.01", message = "时薪必须大于 0")
    private BigDecimal hourSalary;

    @Min(value = 1, message = "需求人数至少为 1 人")
    private Integer needNum;

    @Size(max = 500, message = "岗位描述不能超过 500 字")
    private String description;

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public BigDecimal getHourSalary() {
        return hourSalary;
    }

    public void setHourSalary(BigDecimal hourSalary) {
        this.hourSalary = hourSalary;
    }

    public Integer getNeedNum() {
        return needNum;
    }

    public void setNeedNum(Integer needNum) {
        this.needNum = needNum;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
