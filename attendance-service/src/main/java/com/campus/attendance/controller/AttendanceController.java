package com.campus.attendance.controller;

import com.campus.attendance.common.ApiResponse;
import com.campus.attendance.dto.CheckInRequest;
import com.campus.attendance.dto.CheckInResult;
import com.campus.attendance.dto.CheckOutRequest;
import com.campus.attendance.dto.CheckOutResult;
import com.campus.attendance.dto.DeptSummaryView;
import com.campus.attendance.dto.MonthSubsidyView;
import com.campus.attendance.dto.MyRecordView;
import com.campus.attendance.dto.SchoolSummaryView;
import com.campus.attendance.service.AttendanceService;
import com.campus.attendance.service.SubsidyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    public AttendanceController(AttendanceService attendanceService,
                                SubsidyService subsidyService) {
        this.attendanceService = attendanceService;
        this.subsidyService = subsidyService;
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
     * GET /api/attendance/myRecord 我的签到记录 + 当月合计（学生本人）
     * 请求参数：month（yyyy-MM，不传默认当月）
     * 返回 data：{ month, recordCount, settledCount, pendingCount, totalHours, totalAmount, list }
     */
    @GetMapping("/myRecord")
    public ApiResponse<MyRecordView> myRecord(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "month", required = false) String month) {
        return ApiResponse.ok(attendanceService.myRecord(token, month));
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
}
