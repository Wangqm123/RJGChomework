package com.campus.attendance.controller;

import com.campus.attendance.common.ApiResponse;
import com.campus.attendance.dto.CheckInRequest;
import com.campus.attendance.dto.CheckInResult;
import com.campus.attendance.dto.CheckOutRequest;
import com.campus.attendance.dto.CheckOutResult;
import com.campus.attendance.dto.ConfirmRequest;
import com.campus.attendance.dto.ConfirmResult;
import com.campus.attendance.dto.DeptSummaryView;
import com.campus.attendance.dto.MonthSubsidyView;
import com.campus.attendance.dto.MyRecordView;
import com.campus.attendance.dto.PayRequest;
import com.campus.attendance.dto.PayResult;
import com.campus.attendance.dto.SchoolSummaryView;
import com.campus.attendance.dto.SettleRequest;
import com.campus.attendance.dto.SettleResult;
import com.campus.attendance.dto.SubsidyView;
import com.campus.attendance.service.AttendanceService;
import com.campus.attendance.service.SettlementService;
import com.campus.attendance.service.SubsidyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工时考勤与补贴核算服务对外接口（《接口文档初稿》服务3，前缀 /api/attendance）。
 *
 * 需要登录的接口统一从请求头 token 取身份，服务内部调权限服务校验；
 * 不接受前端传 userId/roleId 这类可伪造的字段。
 * 统一返回 {code, msg, data}，错误码 200/400/401/403/500，与另外两个服务一致。
 */
@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final SubsidyService subsidyService;
    private final SettlementService settlementService;

    public AttendanceController(AttendanceService attendanceService,
                                SubsidyService subsidyService,
                                SettlementService settlementService) {
        this.attendanceService = attendanceService;
        this.subsidyService = subsidyService;
        this.settlementService = settlementService;
    }

    /**
     * POST /api/attendance/checkIn 学生签到
     * 请求参数：jobId
     * 返回 data：{ recordId, jobId, jobName, deptId, hourSalary, checkInTime, status, statusName }
     * 前置校验：Token 有效 → 岗位存在且未下架 → 已被该岗位录用 → 无未签退记录
     */
    @PostMapping("/checkIn")
    public ApiResponse<CheckInResult> checkIn(
            @RequestHeader(value = "token", required = false) String token,
            @Valid @RequestBody CheckInRequest req) {
        return ApiResponse.ok(attendanceService.checkIn(token, req.jobId()));
    }

    /**
     * POST /api/attendance/checkOut 学生签退
     * 请求参数：jobId
     * 返回 data：{ recordId, checkInTime, checkOutTime, workMinutes, workHours, amount, status, statusName, tip }
     */
    @PostMapping("/checkOut")
    public ApiResponse<CheckOutResult> checkOut(
            @RequestHeader(value = "token", required = false) String token,
            @Valid @RequestBody CheckOutRequest req) {
        return ApiResponse.ok(attendanceService.checkOut(token, req.jobId()));
    }

    /**
     * POST /api/attendance/confirmAbnormal 异常工时核实（部门管理员核实本部门 / 中心管理员核实全校）
     * 请求体：{ recordId, pass: true|false, remark?: "..." }
     *   pass=true  → 计入补贴（status 2 → 1）
     *   pass=false → 核实不通过、不计薪（status 2 → 3，记录保留）
     * 返回 data：{ recordId, userId, jobId, deptId, status, statusName, workMinutes, amount,
     *             confirmOperatorId, confirmTime, tip }
     */
    @PostMapping("/confirmAbnormal")
    public ApiResponse<ConfirmResult> confirmAbnormal(
            @RequestHeader(value = "token", required = false) String token,
            @Valid @RequestBody ConfirmRequest req) {
        return ApiResponse.ok(attendanceService.confirmAbnormal(
                token, req.recordId(), req.pass(), req.remark()));
    }

    /**
     * GET /api/attendance/myRecord 我的签到记录 + 当月合计（学生本人）
     * 请求参数：month（yyyy-MM，不传默认当月）、page（默认 1）、size（默认 20，最大 100）
     * 返回 data：{ month, recordCount, settledCount, pendingCount, rejectedCount,
     *             totalHours, totalAmount, page, size, totalPages, list }
     *
     * 合计基于整月全量，翻页不变；list 只返回当前页。
     */
    @GetMapping("/myRecord")
    public ApiResponse<MyRecordView> myRecord(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "month", required = false) String month,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ApiResponse.ok(attendanceService.myRecord(token, month, page, size));
    }

    /**
     * GET /api/attendance/mySubsidy 我的月度工时补贴（学生本人）
     * 请求参数：month（yyyy-MM，不传默认当月）
     * 返回 data：{ userId, userName, month, recordCount, totalHours, totalAmount }
     */
    @GetMapping("/mySubsidy")
    public ApiResponse<MonthSubsidyView> mySubsidy(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "month", required = false) String month) {
        return ApiResponse.ok(subsidyService.mySubsidy(token, month));
    }

    /**
     * GET /api/attendance/deptSummary 部门月度工时汇总
     * 请求参数：month（yyyy-MM）、deptId（部门管理员可省略=查本部门；中心管理员必填）
     * 返回 data：{ deptId, month, personCount, totalHours, totalAmount, list:[{userId,userName,...}] }
     */
    @GetMapping("/deptSummary")
    public ApiResponse<DeptSummaryView> deptSummary(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "month", required = false) String month,
            @RequestParam(value = "deptId", required = false) Long deptId) {
        return ApiResponse.ok(subsidyService.deptSummary(token, month, deptId));
    }

    /**
     * GET /api/attendance/schoolSummary 全校月度工时汇总（助学中心管理员）
     * 请求参数：month（yyyy-MM）
     * 返回 data：{ month, deptCount, personCount, totalHours, totalAmount, list:[{deptId,...}] }
     */
    @GetMapping("/schoolSummary")
    public ApiResponse<SchoolSummaryView> schoolSummary(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "month", required = false) String month) {
        return ApiResponse.ok(subsidyService.schoolSummary(token, month));
    }

    /**
     * POST /api/attendance/settle 月度结算（助学中心管理员）
     * 请求体：{ month: "2026-11", deptId?: 1, remark?: "..." }
     *   不传 deptId = 全校结算；传了 = 只结算该部门
     * 返回 data：{ month, deptId, userCount, createdCount, refreshedCount, skippedCount,
     *             totalHours, totalAmount, list:[发放单] }
     *
     * 语义：把该月已签退工时固化成发放单。
     *   新建 / 刷新待发放单；已发放的单跳过（金额冻结，不重复发钱）。
     */
    @PostMapping("/settle")
    public ApiResponse<SettleResult> settle(
            @RequestHeader(value = "token", required = false) String token,
            @Valid @RequestBody SettleRequest req) {
        return ApiResponse.ok(settlementService.settle(
                token, req.month(), req.deptId(), req.remark()));
    }

    /**
     * POST /api/attendance/pay 批量发放（助学中心管理员）
     * 请求体：{ month: "2026-11", deptId?: 1 }
     * 返回 data：{ month, deptId, paidCount, paidAmount, tip }
     *
     * 只把"待发放"改成"已发放"，重复点不会重复计数（条件更新保证幂等）。
     */
    @PostMapping("/pay")
    public ApiResponse<PayResult> pay(
            @RequestHeader(value = "token", required = false) String token,
            @Valid @RequestBody PayRequest req) {
        return ApiResponse.ok(settlementService.pay(token, req.month(), req.deptId()));
    }

    /**
     * GET /api/attendance/subsidyList 补贴发放单列表
     * 请求参数：month（yyyy-MM）、deptId（部门管理员可省略=本部门；中心管理员可省略=全校）、
     *          status（0 待发放 / 1 已发放，不传=全部）
     * 返回 data：[{ subsidyId, userId, userName, deptId, month, recordCount,
     *              totalHours, totalAmount, status, statusName, settleTime, payTime }]
     */
    @GetMapping("/subsidyList")
    public ApiResponse<List<SubsidyView>> subsidyList(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "month", required = false) String month,
            @RequestParam(value = "deptId", required = false) Long deptId,
            @RequestParam(value = "status", required = false) Integer status) {
        return ApiResponse.ok(settlementService.subsidyList(token, month, deptId, status));
    }
}
