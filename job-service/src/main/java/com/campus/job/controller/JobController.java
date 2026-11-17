package com.campus.job.controller;

import com.campus.job.common.ApiResponse;
import com.campus.job.dto.ApplyJobRequest;
import com.campus.job.dto.ApplyView;
import com.campus.job.dto.AuditApplyRequest;
import com.campus.job.dto.AuditJobRequest;
import com.campus.job.dto.JobView;
import com.campus.job.dto.OfflineJobRequest;
import com.campus.job.dto.PageResult;
import com.campus.job.dto.PublishJobRequest;
import com.campus.job.dto.UpdateJobRequest;
import com.campus.job.service.ApplyService;
import com.campus.job.service.JobService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 岗位申请与审核服务对外接口（接口文档初稿 · 服务2，前缀 /api/job）。
 *
 * 需要登录的接口统一从请求头 token 取身份，服务内部再调权限服务校验，
 * 调用方无需传 userId/roleId 之类可伪造的字段（投递接口的 userId 也会交叉校验）。
 */
@RestController
@RequestMapping("/api/job")
public class JobController {

    private final JobService jobService;
    private final ApplyService applyService;

    public JobController(JobService jobService, ApplyService applyService) {
        this.jobService = jobService;
        this.applyService = applyService;
    }

    /**
     * POST /api/job/publish 部门管理员发布岗位
     * 请求参数：jobName、address、hourSalary、needNum、description、deptId（选填）
     * 返回 data：{ jobId }
     */
    @PostMapping("/publish")
    public ApiResponse<Map<String, Object>> publish(
            @RequestHeader(value = "token", required = false) String token,
            @Valid @RequestBody PublishJobRequest req) {
        return ApiResponse.ok(jobService.publish(token, req));
    }

    /**
     * POST /api/job/auditJob 助学中心审核岗位
     * 请求参数：jobId、auditResult（pass/reject）、auditRemark
     * 返回 data：{ status, statusName }
     */
    @PostMapping("/auditJob")
    public ApiResponse<Map<String, Object>> auditJob(
            @RequestHeader(value = "token", required = false) String token,
            @Valid @RequestBody AuditJobRequest req) {
        return ApiResponse.ok(jobService.auditJob(token, req.getJobId(), req.getAuditResult(), req.getAuditRemark()));
    }

    /**
     * GET /api/job/listJob 查询已上架岗位列表（公开接口，学生浏览用）
     * 请求参数：page（从 1 开始，默认 1）、size（默认 10，最大 50）
     * 返回 data：{ list: [...], total }
     */
    @GetMapping("/listJob")
    public ApiResponse<PageResult<JobView>> listJob(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ApiResponse.ok(jobService.listJobs(page, size));
    }

    /**
     * POST /api/job/applyJob 学生投递岗位
     * 请求参数：jobId、userId（必须与 Token 中的用户一致）
     * 返回 data：{ applyId }
     */
    @PostMapping("/applyJob")
    public ApiResponse<Map<String, Object>> applyJob(
            @RequestHeader(value = "token", required = false) String token,
            @Valid @RequestBody ApplyJobRequest req) {
        return ApiResponse.ok(applyService.applyJob(token, req.getJobId(), req.getUserId()));
    }

    /**
     * POST /api/job/auditApply 部门管理员审核学生申请
     * 请求参数：applyId、applyResult（accept/refuse）、remark
     * 返回 data：{ status, statusName, jobStatus, jobStatusName }
     */
    @PostMapping("/auditApply")
    public ApiResponse<Map<String, Object>> auditApply(
            @RequestHeader(value = "token", required = false) String token,
            @Valid @RequestBody AuditApplyRequest req) {
        return ApiResponse.ok(applyService.auditApply(token, req.getApplyId(),
                req.getApplyResult(), req.getRemark()));
    }

    /**
     * GET /api/job/getStudentHired 查询学生是否被该岗位录用（给工时服务调用）
     * 请求参数：jobId、userId
     * 返回 data：true / false
     */
    @GetMapping("/getStudentHired")
    public ApiResponse<Boolean> getStudentHired(@RequestParam("jobId") Long jobId,
                                                @RequestParam("userId") Long userId) {
        return ApiResponse.ok(applyService.isStudentHired(jobId, userId));
    }

    /**
     * GET /api/job/listMyApply 查询我的申请记录
     * 请求参数：userId（必须与 Token 中的用户一致）
     * 返回 data：申请列表（含岗位信息、审核状态）
     */
    @GetMapping("/listMyApply")
    public ApiResponse<List<ApplyView>> listMyApply(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam("userId") Long userId) {
        return ApiResponse.ok(applyService.listMyApply(token, userId));
    }

    // ================== 以下为按《需求文档》补充的接口（接口文档初稿未列出） ==================

    /**
     * POST /api/job/updateJob 部门管理员修改本部门岗位（需求文档 DeptAdmin-2）
     * 请求参数：jobId + 需要修改的字段（jobName/address/hourSalary/needNum/description）
     * 返回 data：{ status, statusName }
     */
    @PostMapping("/updateJob")
    public ApiResponse<Map<String, Object>> updateJob(
            @RequestHeader(value = "token", required = false) String token,
            @Valid @RequestBody UpdateJobRequest req) {
        return ApiResponse.ok(jobService.updateJob(token, req));
    }

    /**
     * POST /api/job/offlineJob 部门管理员下架本部门岗位（需求文档 DeptAdmin-2）
     * 请求参数（JSON）：jobId
     * 返回 data：{ status, statusName }
     */
    @PostMapping("/offlineJob")
    public ApiResponse<Map<String, Object>> offlineJob(
            @RequestHeader(value = "token", required = false) String token,
            @Valid @RequestBody OfflineJobRequest req) {
        return ApiResponse.ok(jobService.offlineJob(token, req.getJobId()));
    }

    /**
     * GET /api/job/listApply 管理员查看某岗位的申请列表（需求文档 3.2 申请记录查询）
     * 请求参数：jobId
     * 权限：部门管理员（仅本部门岗位）/ 助学中心管理员（全部）
     */
    @GetMapping("/listApply")
    public ApiResponse<List<ApplyView>> listApply(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam("jobId") Long jobId) {
        return ApiResponse.ok(applyService.listJobApply(token, jobId));
    }
}
