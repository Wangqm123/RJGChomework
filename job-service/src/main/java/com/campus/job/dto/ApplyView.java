package com.campus.job.dto;

import com.campus.job.common.ApplyStatus;
import com.campus.job.entity.Job;
import com.campus.job.entity.JobApply;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 申请记录返回体（含岗位信息与审核状态）：
 * 用于 /api/job/listMyApply、/api/job/listApply。
 */
public record ApplyView(
        Long applyId,
        Long jobId,
        String jobName,
        String address,
        BigDecimal hourSalary,
        Long deptId,
        Integer status,
        String statusName,
        String remark,
        LocalDateTime applyTime,
        LocalDateTime auditTime) {

    /** 实体 → 视图对象；job 为空时岗位相关字段留空，避免空指针 */
    public static ApplyView from(JobApply apply, Job job) {
        return new ApplyView(
                apply.getId(),
                apply.getJobId(),
                job == null ? null : job.getJobName(),
                job == null ? null : job.getAddress(),
                job == null ? null : job.getHourSalary(),
                job == null ? null : job.getDeptId(),
                apply.getStatus(),
                ApplyStatus.nameOf(apply.getStatus()),
                apply.getRemark(),
                apply.getApplyTime(),
                apply.getAuditTime());
    }
}
