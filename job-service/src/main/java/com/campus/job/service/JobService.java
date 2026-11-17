package com.campus.job.service;

import com.campus.job.common.BusinessException;
import com.campus.job.common.JobStatus;
import com.campus.job.common.Roles;
import com.campus.job.dto.JobView;
import com.campus.job.dto.PageResult;
import com.campus.job.dto.PublishJobRequest;
import com.campus.job.dto.UpdateJobRequest;
import com.campus.job.entity.Job;
import com.campus.job.repository.JobRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 岗位生命周期业务（需求文档 3.2）：
 * 岗位发布 → 助学中心审核（上架/驳回）→ 部门管理员修改 / 下架。
 *
 * 学生投递、申请审核等"申请"相关逻辑见 ApplyService。
 */
@Service
public class JobService {

    private final JobRepository jobRepository;
    private final AuthGuard authGuard;

    public JobService(JobRepository jobRepository, AuthGuard authGuard) {
        this.jobRepository = jobRepository;
        this.authGuard = authGuard;
    }

    /**
     * 发布岗位（部门管理员）：
     * 校验操作者角色与部门 → 入库 → 状态置为"待中心审核"。
     *
     * 注：接口文档中的 deptId 选填，不传则使用操作者所属部门；
     * 传了就必须与操作者部门一致，防止越权把岗位挂到其他部门名下。
     */
    @Transactional
    public Map<String, Object> publish(String token, PublishJobRequest req) {
        AuthGuard.Operator operator = authGuard.requireDeptAdmin(token);

        if (req.getDeptId() != null && !req.getDeptId().equals(operator.deptId())) {
            throw new BusinessException(403, "权限不足：只能发布本部门（部门 " + operator.deptId() + "）的岗位");
        }

        Job job = new Job();
        job.setJobName(req.getJobName());
        job.setAddress(req.getAddress());
        job.setHourSalary(req.getHourSalary());
        job.setNeedNum(req.getNeedNum());
        job.setHiredNum(0);
        job.setDescription(req.getDescription());
        job.setDeptId(operator.deptId());
        job.setStatus(JobStatus.PENDING_AUDIT);
        job.setPublisherId(operator.userId());

        Long jobId = jobRepository.save(job).getId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("jobId", jobId);
        return data;
    }

    /**
     * 助学中心审核岗位：
     * pass → 已上架（学生可浏览投递）；reject → 已驳回（必须填写驳回理由）。
     *
     * 业务冲突：只有"待中心审核"的岗位可以审核，重复审核返回 400。
     */
    @Transactional
    public Map<String, Object> auditJob(String token, Long jobId, String auditResult, String auditRemark) {
        authGuard.requireRole(token, Roles.CENTER_ADMIN);

        Job job = getJobOrThrow(jobId);
        if (job.getStatus() != JobStatus.PENDING_AUDIT) {
            throw new BusinessException(400, "岗位当前状态为「" + JobStatus.nameOf(job.getStatus())
                    + "」，只有待中心审核的岗位可以审核");
        }

        boolean pass = "pass".equalsIgnoreCase(auditResult);
        if (!pass && (auditRemark == null || auditRemark.isBlank())) {
            throw new BusinessException(400, "驳回岗位必须填写驳回理由");
        }

        job.setStatus(pass ? JobStatus.LISTED : JobStatus.REJECTED);
        job.setAuditRemark(auditRemark);
        jobRepository.save(job);
        return statusData(job);
    }

    /**
     * 学生浏览已上架岗位（分页）。page 从 1 开始，size 最大 50，防止一次拉太多数据。
     */
    public PageResult<JobView> listJobs(Integer page, Integer size) {
        int pageNo = (page == null || page < 1) ? 1 : page;
        int pageSize = (size == null || size < 1) ? 10 : Math.min(size, 50);

        Page<Job> result = jobRepository.findByStatusOrderByIdDesc(JobStatus.LISTED,
                PageRequest.of(pageNo - 1, pageSize));
        List<JobView> list = result.getContent().stream().map(JobView::from).toList();
        return new PageResult<>(list, result.getTotalElements());
    }

    /**
     * 部门管理员修改岗位（需求文档 DeptAdmin-2）。
     * - 只能改本部门岗位；
     * - 已下架 / 已招满的岗位不允许修改；
     * - 需求人数不能小于已录用人数；
     * - 被驳回的岗位修改后自动重新提交中心审核。
     */
    @Transactional
    public Map<String, Object> updateJob(String token, UpdateJobRequest req) {
        AuthGuard.Operator operator = authGuard.requireDeptAdmin(token);
        Job job = getJobOrThrow(req.getJobId());
        requireOwnDept(job, operator.deptId());

        if (job.getStatus() == JobStatus.OFFLINE) {
            throw new BusinessException(400, "已下架的岗位不能修改");
        }
        if (job.getStatus() == JobStatus.FULL) {
            throw new BusinessException(400, "已招满的岗位不能修改");
        }

        if (req.getJobName() != null) {
            job.setJobName(req.getJobName());
        }
        if (req.getAddress() != null) {
            job.setAddress(req.getAddress());
        }
        if (req.getHourSalary() != null) {
            job.setHourSalary(req.getHourSalary());
        }
        if (req.getDescription() != null) {
            job.setDescription(req.getDescription());
        }
        if (req.getNeedNum() != null) {
            if (req.getNeedNum() < job.getHiredNum()) {
                throw new BusinessException(400, "需求人数不能小于已录用人数（当前已录用 "
                        + job.getHiredNum() + " 人）");
            }
            job.setNeedNum(req.getNeedNum());
        }

        // 驳回后的岗位修改完内容，重新进入待审核队列
        if (job.getStatus() == JobStatus.REJECTED) {
            job.setStatus(JobStatus.PENDING_AUDIT);
            job.setAuditRemark(null);
        }

        jobRepository.save(job);
        return statusData(job);
    }

    /**
     * 部门管理员下架岗位（需求文档 DeptAdmin-2）：
     * 只有"已上架 / 已招满"的岗位可以下架，下架后学生不可再浏览、投递。
     */
    @Transactional
    public Map<String, Object> offlineJob(String token, Long jobId) {
        AuthGuard.Operator operator = authGuard.requireDeptAdmin(token);
        Job job = getJobOrThrow(jobId);
        requireOwnDept(job, operator.deptId());

        if (job.getStatus() != JobStatus.LISTED && job.getStatus() != JobStatus.FULL) {
            throw new BusinessException(400, "岗位当前状态为「" + JobStatus.nameOf(job.getStatus())
                    + "」，只有已上架或已招满的岗位可以下架");
        }
        job.setStatus(JobStatus.OFFLINE);
        jobRepository.save(job);
        return statusData(job);
    }

    /** 查询岗位，不存在直接 400 */
    private Job getJobOrThrow(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException(400, "岗位不存在：id=" + jobId));
    }

    /** 部门数据隔离：只能操作本部门岗位 */
    private void requireOwnDept(Job job, Long deptId) {
        if (!job.getDeptId().equals(deptId)) {
            throw new BusinessException(403, "权限不足：只能操作本部门（部门 " + deptId + "）的岗位");
        }
    }

    /** 返回岗位最新状态：{status, statusName} */
    private Map<String, Object> statusData(Job job) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", job.getStatus());
        data.put("statusName", JobStatus.nameOf(job.getStatus()));
        return data;
    }
}
