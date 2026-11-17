package com.campus.job.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 发布岗位请求体：POST /api/job/publish
 *
 * 仅部门管理员可调用，操作者 Token 放在请求头 token 中；
 * deptId 可以传也可以不传：不传则默认取操作者所属部门，
 * 传了则必须与操作者所属部门一致（防止把岗位挂到别的部门）。
 */
public class PublishJobRequest {

    @NotBlank(message = "岗位名称不能为空")
    @Size(max = 100, message = "岗位名称不能超过 100 字")
    private String jobName;

    @NotBlank(message = "工作地点不能为空")
    @Size(max = 200, message = "工作地点不能超过 200 字")
    private String address;

    @NotNull(message = "时薪不能为空")
    @DecimalMin(value = "0.01", message = "时薪必须大于 0")
    private BigDecimal hourSalary;

    @NotNull(message = "需求人数不能为空")
    @Min(value = 1, message = "需求人数至少为 1 人")
    private Integer needNum;

    @NotBlank(message = "岗位描述不能为空")
    @Size(max = 500, message = "岗位描述不能超过 500 字")
    private String description;

    /** 所属部门 id（选填，默认取操作者所属部门） */
    private Long deptId;

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

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }
}
