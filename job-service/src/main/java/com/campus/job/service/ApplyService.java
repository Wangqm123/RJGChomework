package com.campus.job.service;

import com.campus.job.client.AuthClient;
import com.campus.job.common.ApplyStatus;
import com.campus.job.common.BusinessException;
import com.campus.job.common.JobStatus;
import com.campus.job.common.Roles;
import com.campus.job.dto.ApplyView;
import com.campus.job.entity.Job;
import com.campus.job.entity.JobApply;
import com.campus.job.repository.JobApplyRepository;
import com.campus.job.repository.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 岗位申请业务（需求文档 3.2）：
 * 学生投递 → 部门管理员录用/拒绝 → 录用人数达到需求后岗位自动"招满"。
 *
 * 与工时服务的对接点：getStudentHired(jobId, userId)
 * 工时服务签到前先调本接口，返回 true 才允许打卡。
 */
@Service
public class ApplyService {

    private final JobRepository jobRepository;
    private final JobApplyRepository applyRepository;
    private final AuthGuard authGuard;

    public ApplyService(JobRepository jobRepository, JobApplyRepository applyRepository, AuthGuard authGuard) {
        this.jobRepository = jobRepository;
        this.applyRepository = applyRepository;
        this.authGuard = authGuard;
    }

    /**
     * 学生投递岗位：
     * - 只有已上架岗位可以投递；
     * - 同一岗位只能投递一次（业务查重 + 数据库唯一约束双重保证）；
     * - userId 必须与 Token 中的用户一致，防止冒用他人身份投递。
     */
    @Transactional
    public Map<String, Object> applyJob(String token, Long jobId, Long userId) {
        AuthClient.Session session = authGuard.requireRole(token, Roles.STUDENT);
        if (!session.userId().equals(userId)) {
            throw new BusinessException(403, "权限不足：只能以本人身份投递岗位");
        }

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException(400, "岗位不存在：id=" + jobId));
        if (job.getStatus() != JobStatus.LISTED) {
            throw new BusinessException(400, "岗位当前状态为「" + JobStatus.nameOf(job.getStatus())
                    + "」，只有已上架岗位可以投递");
        }
        if (applyRepository.existsByJobIdAndUserId(jobId, session.userId())) {
            throw new BusinessException(400, "已投递过该岗位，请勿重复申请");
        }

        JobApply apply = new JobApply();
        apply.setJobId(jobId);
        apply.setUserId(session.userId());
        apply.setStatus(ApplyStatus.PENDING);

        Long applyId = applyRepository.save(apply).getId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("applyId", applyId);
        return data;
    }

    /**
     * 部门管理员审核学生申请（录用 / 拒绝）：
     * - 只能审核本部门岗位的申请（数据隔离）；
     * - 已处理过的申请不能重复审核；
     * - 录用时校验岗位是否已招满，录用满额后岗位自动转为"已招满"。
     */
    @Transactional
    public Map<String, Object> auditApply(String token, Long applyId, String applyResult, String remark) {
        AuthGuard.Operator operator = authGuard.requireDeptAdmin(token);

        JobApply apply = applyRepository.findById(applyId)
                .orElseThrow(() -> new BusinessException(400, "申请记录不存在：id=" + applyId));
        Job job = jobRepository.findById(apply.getJobId())
                .orElseThrow(() -> new BusinessException(400, "岗位不存在：id=" + apply.getJobId()));

        if (!job.getDeptId().equals(operator.deptId())) {
            throw new BusinessException(403, "权限不足：只能审核本部门（部门 " + operator.deptId() + "）岗位的申请");
        }
        if (apply.getStatus() != ApplyStatus.PENDING) {
            throw new BusinessException(400, "该申请已处理（当前状态：" + ApplyStatus.nameOf(apply.getStatus())
                    + "），不能重复审核");
        }

        boolean accept = "accept".equalsIgnoreCase(applyResult);
        if (accept) {
            if (job.getStatus() != JobStatus.LISTED) {
                throw new BusinessException(400, "岗位当前状态为「" + JobStatus.nameOf(job.getStatus())
                        + "」，只有已上架岗位可以录用学生");
            }
            if (job.getHiredNum() >= job.getNeedNum()) {
                throw new BusinessException(400, "岗位名额已满（需求 " + job.getNeedNum() + " 人），无法录用");
            }
            apply.setStatus(ApplyStatus.HIRED);
            // 录用人数 +1；满额则岗位状态流转为"已招满"
            job.setHiredNum(job.getHiredNum() + 1);
            if (job.getHiredNum() >= job.getNeedNum()) {
                job.setStatus(JobStatus.FULL);
            }
        } else {
            apply.setStatus(ApplyStatus.REFUSED);
        }

        apply.setRemark(remark);
        apply.setAuditTime(LocalDateTime.now());
        applyRepository.save(apply);
        jobRepository.save(job);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", apply.getStatus());
        data.put("statusName", ApplyStatus.nameOf(apply.getStatus()));
        data.put("jobStatus", job.getStatus());
        data.put("jobStatusName", JobStatus.nameOf(job.getStatus()));
        return data;
    }

    /**
     * 查询学生是否被该岗位录用（接口文档约定：给工时服务调用，data 只有 true/false）。
     * 没投递、被拒绝、岗位不存在都返回 false，让调用方统一按"不可打卡"处理。
     */
    public boolean isStudentHired(Long jobId, Long userId) {
        return applyRepository.findByJobIdAndUserId(jobId, userId)
                .map(apply -> apply.getStatus() == ApplyStatus.HIRED)
                .orElse(false);
    }

    /** 学生查询"我的申请记录"：只能查看本人记录，返回岗位信息 + 审核状态 */
    public List<ApplyView> listMyApply(String token, Long userId) {
        AuthClient.Session session = authGuard.requireRole(token, Roles.STUDENT);
        if (!session.userId().equals(userId)) {
            throw new BusinessException(403, "权限不足：只能查看本人的申请记录");
        }
        return toViews(applyRepository.findByUserIdOrderByIdDesc(userId));
    }

    /**
     * 部门管理员 / 助学中心管理员查看某个岗位的申请列表
     *（需求文档 3.2「申请记录查询」；接口文档初稿未列出，属补充接口）。
     * 部门管理员只能看本部门岗位的申请。
     */
    public List<ApplyView> listJobApply(String token, Long jobId) {
        AuthClient.Session session = authGuard.requireAnyRole(token, Roles.DEPT_ADMIN, Roles.CENTER_ADMIN);

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException(400, "岗位不存在：id=" + jobId));
        if (session.roleId() == Roles.DEPT_ADMIN) {
            AuthGuard.Operator operator = authGuard.requireDeptAdmin(token, session);
            if (!job.getDeptId().equals(operator.deptId())) {
                throw new BusinessException(403, "权限不足：只能查看本部门（部门 " + operator.deptId() + "）岗位的申请");
            }
        }
        return toViews(applyRepository.findByJobIdOrderByIdAsc(jobId));
    }

    /** 批量补齐岗位信息，避免逐条查询造成 N+1 */
    private List<ApplyView> toViews(List<JobApply> applies) {
        if (applies.isEmpty()) {
            return List.of();
        }
        List<Long> jobIds = applies.stream().map(JobApply::getJobId).distinct().toList();
        Map<Long, Job> jobMap = jobRepository.findAllById(jobIds).stream()
                .collect(Collectors.toMap(Job::getId, Function.identity()));
        return applies.stream()
                .map(apply -> ApplyView.from(apply, jobMap.get(apply.getJobId())))
                .toList();
    }
}
