package com.campus.job.dto;

import com.campus.job.common.JobStatus;
import com.campus.job.entity.Job;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 岗位信息返回体（列表/详情统一使用）。
 * data = 岗位字段 + statusName（中文状态，前端直接展示）
 */
public record JobView(
        Long jobId,
        String jobName,
        String address,
        BigDecimal hourSalary,
        Integer needNum,
        Integer hiredNum,
        String description,
        Long deptId,
        Integer status,
        String statusName,
        String auditRemark,
        LocalDateTime createTime) {

    /** 实体 → 视图对象 */
    public static JobView from(Job job) {
        return new JobView(
                job.getId(),
                job.getJobName(),
                job.getAddress(),
                job.getHourSalary(),
                job.getNeedNum(),
                job.getHiredNum(),
                job.getDescription(),
                job.getDeptId(),
                job.getStatus(),
                JobStatus.nameOf(job.getStatus()),
                job.getAuditRemark(),
                job.getCreateTime());
    }
}
