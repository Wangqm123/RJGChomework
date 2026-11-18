package com.campus.attendance;

import com.campus.attendance.client.AuthClient;
import com.campus.attendance.client.JobClient;
import com.campus.attendance.common.AttendanceStatus;
import com.campus.attendance.common.BusinessException;
import com.campus.attendance.entity.AttendanceRecord;
import com.campus.attendance.repository.AttendanceRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 工时考勤与补贴核算服务单元测试（分工要求：成员 5 产出测试用例 + 缺陷报告）。
 *
 * 说明：
 * 1. AuthClient、JobClient 用 @MockBean 打桩，测试不依赖 8081/8082 是否启动，
 *    只验证本服务的业务规则；服务间不可用的场景也用打桩模拟（见 TC-19）。
 * 2. 测试类加 @Transactional，每个用例结束后自动回滚，用例之间互不影响、可反复执行。
 * 3. 用例编号 TC-xx 与《成员5-任务执行手册》3.2 节的用例清单一一对应。
 *
 * 预置账号（与权限服务一致）：student1=userId 4、student2=userId 5、deptA=userId 2(dept 1)、center=userId 3
 */
@SpringBootTest(properties = {
        // 测试用独立的内存库，不污染开发用的 ./data/attendancedb
        "spring.datasource.url=jdbc:h2:mem:attendtest;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.show-sql=false"
})
@AutoConfigureMockMvc
@Transactional
class AttendanceServiceTests {

    /** 学生 student1（userId=4），岗位服务预置：已被岗位 1 录用 */
    private static final String STUDENT_TOKEN = "token-student1";
    /** 学生 student2（userId=5），未录用岗位 1 */
    private static final String STUDENT2_TOKEN = "token-student2";
    /** 部门管理员 deptA（userId=2，部门 1） */
    private static final String DEPT_TOKEN = "token-deptA";
    /** 助学中心管理员（userId=3） */
    private static final String CENTER_TOKEN = "token-center";

    /** 演示岗位：图书馆图书整理助理，时薪 25 元，部门 1，状态已上架 */
    private static final long JOB_ID = 1L;
    private static final long DEPT_ID = 1L;
    private static final BigDecimal SALARY = new BigDecimal("25.00");

    private static final String CURRENT_MONTH = YearMonth.now().toString();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AttendanceRecordRepository recordRepository;

    @MockBean
    private AuthClient authClient;

    @MockBean
    private JobClient jobClient;

    @BeforeEach
    void setUp() {

        // Token 解析：模拟权限服务 checkToken 的返回
        when(authClient.checkToken(any())).thenAnswer(inv -> {
            String token = inv.getArgument(0);
            if (token == null) {
                throw new BusinessException(401, "未登录或 Token 已失效");
            }
            return switch (token) {
                case STUDENT_TOKEN -> new AuthClient.Session(4L, 1);
                case STUDENT2_TOKEN -> new AuthClient.Session(5L, 1);
                case DEPT_TOKEN -> new AuthClient.Session(2L, 2);
                case CENTER_TOKEN -> new AuthClient.Session(3L, 3);
                default -> throw new BusinessException(401, "未登录或 Token 已失效");
            };
        });

        // 用户信息：模拟权限服务 getUserInfo（部门 1 的管理员、学生姓名等）
        when(authClient.getUserInfo(anyLong())).thenAnswer(inv -> {
            Long userId = inv.getArgument(0);
            return switch (userId.intValue()) {
                case 2 -> new AuthClient.UserInfo(2L, "图书馆管理员", DEPT_ID, 2);
                case 3 -> new AuthClient.UserInfo(3L, "助学中心管理员", 0L, 3);
                case 4 -> new AuthClient.UserInfo(4L, "张三", 0L, 1);
                case 5 -> new AuthClient.UserInfo(5L, "李四", 0L, 1);
                default -> new AuthClient.UserInfo(userId, "用户" + userId, DEPT_ID, 1);
            };
        });

        // 岗位服务：默认"岗位不存在"，只有岗位 1 存在；默认"未录用"，只有 student1 被岗位 1 录用。
        // 注意：这里必须用 thenAnswer 一次性给出所有分支，不能写成
        //   when(getJob(anyLong())).thenThrow(...)  再  when(getJob(eq(1L))).thenReturn(...)
        // 因为第二行的 when(...) 参数求值时就会调用 mock，被前一个"抛异常"的桩命中，直接在 setUp 里抛出。
        when(jobClient.getJob(anyLong())).thenAnswer(inv -> {
            Long jobId = inv.getArgument(0);
            if (jobId != null && jobId == JOB_ID) {
                return new JobClient.JobInfo(JOB_ID, "图书馆图书整理助理", SALARY, DEPT_ID, 2);
            }
            throw new BusinessException(400, "岗位不存在");
        });
        when(jobClient.isStudentHired(anyLong(), anyLong())).thenAnswer(inv -> {
            Long jobId = inv.getArgument(0);
            Long userId = inv.getArgument(1);
            return jobId != null && jobId == JOB_ID && userId != null && userId == 4L;
        });
    }

    // ==================== 签到 ====================

    @Test
    @DisplayName("TC-01 学生正常签到：返回记录号、岗位名、时薪快照")
    void tc01_checkInSuccess() throws Exception {
        mockMvc.perform(post("/api/attendance/checkIn")
                        .header("token", STUDENT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.recordId").isNumber())
                .andExpect(jsonPath("$.data.jobName").value("图书馆图书整理助理"))
                .andExpect(jsonPath("$.data.hourSalary").value(25.00))
                .andExpect(jsonPath("$.data.status").value(AttendanceStatus.CHECKED_IN));
    }

    @Test
    @DisplayName("TC-02 未被录用的学生签到：400，跨服务录用校验生效")
    void tc02_checkInNotHired() throws Exception {
        mockMvc.perform(post("/api/attendance/checkIn")
                        .header("token", STUDENT2_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":1}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("您未被该岗位录用，无法签到"));
    }

    @Test
    @DisplayName("TC-03 无 Token 签到：401")
    void tc03_checkInWithoutToken() throws Exception {
        mockMvc.perform(post("/api/attendance/checkIn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":1}"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("TC-04 部门管理员调签到接口：403 权限不足")
    void tc04_checkInByDeptAdmin() throws Exception {
        mockMvc.perform(post("/api/attendance/checkIn")
                        .header("token", DEPT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":1}"))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("TC-05 同一岗位重复签到（未签退）：400")
    void tc05_checkInTwice() throws Exception {
        insertRecord(4L, JOB_ID, DEPT_ID, LocalDateTime.now().minusMinutes(30), null, 0);

        mockMvc.perform(post("/api/attendance/checkIn")
                        .header("token", STUDENT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":1}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("您在该岗位已签到且未签退，请先签退"));
    }

    @Test
    @DisplayName("TC-19 岗位不存在：透传岗位服务的 400 提示")
    void tc19_checkInJobNotFound() throws Exception {
        mockMvc.perform(post("/api/attendance/checkIn")
                        .header("token", STUDENT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":99}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("岗位不存在"));
    }

    @Test
    @DisplayName("TC-20 岗位服务不可用：500 友好提示，不放行签到")
    void tc20_jobServiceDown() throws Exception {
        when(jobClient.isStudentHired(eq(JOB_ID), eq(5L)))
                .thenThrow(new BusinessException(500, "岗位服务暂不可用（未启动或网络不通），请稍后重试"));

        mockMvc.perform(post("/api/attendance/checkIn")
                        .header("token", STUDENT2_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":1}"))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("岗位服务暂不可用（未启动或网络不通），请稍后重试"));
    }

    // ==================== 签退 ====================

    @Test
    @DisplayName("TC-06 正常签退：工时 3.5 小时，金额 87.50 元")
    void tc06_checkOutSuccess() throws Exception {
        insertRecord(4L, JOB_ID, DEPT_ID, LocalDateTime.now().minusMinutes(210), null, 0);

        mockMvc.perform(post("/api/attendance/checkOut")
                        .header("token", STUDENT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":1}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.workMinutes").value(210))
                .andExpect(jsonPath("$.data.workHours").value(3.5))
                .andExpect(jsonPath("$.data.amount").value(87.50))
                .andExpect(jsonPath("$.data.status").value(AttendanceStatus.CHECKED_OUT));
    }

    @Test
    @DisplayName("TC-07 没签到直接签退：400")
    void tc07_checkOutWithoutCheckIn() throws Exception {
        mockMvc.perform(post("/api/attendance/checkOut")
                        .header("token", STUDENT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":1}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("未找到该岗位的签到记录，请先签到"));
    }

    @Test
    @DisplayName("TC-08 单次工时超 12 小时：标记异常待确认，暂不计薪")
    void tc08_checkOutAbnormal() throws Exception {
        insertRecord(4L, JOB_ID, DEPT_ID, LocalDateTime.now().minusHours(13), null, 0);

        mockMvc.perform(post("/api/attendance/checkOut")
                        .header("token", STUDENT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":1}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(AttendanceStatus.ABNORMAL))
                .andExpect(jsonPath("$.data.statusName").value("异常待确认"))
                .andExpect(jsonPath("$.data.tip").exists());
    }

    // ==================== 我的记录 / 补贴 ====================

    @Test
    @DisplayName("TC-09 我的记录按月份过滤：只返回当月数据")
    void tc09_myRecordFilterByMonth() throws Exception {
        insertRecord(4L, JOB_ID, DEPT_ID, LocalDateTime.now().minusMinutes(120), 120, 1);
        // 上上个月的记录不应出现在本月结果里
        insertRecord(4L, JOB_ID, DEPT_ID, LocalDateTime.now().minusMonths(2), 120, 1);

        mockMvc.perform(get("/api/attendance/myRecord")
                        .header("token", STUDENT_TOKEN)
                        .param("month", CURRENT_MONTH))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.month").value(CURRENT_MONTH))
                .andExpect(jsonPath("$.data.recordCount").value(1))
                .andExpect(jsonPath("$.data.list.length()").value(1));
    }

    @Test
    @DisplayName("TC-11 月度补贴 = Σ(工时×时薪快照)，精确到分")
    void tc11_mySubsidyAmount() throws Exception {
        insertRecord(4L, JOB_ID, DEPT_ID, LocalDateTime.now().minusMinutes(300), 180, 1); // 3h × 25 = 75.00
        insertRecord(4L, JOB_ID, DEPT_ID, LocalDateTime.now().minusMinutes(200), 210, 1); // 3.5h × 25 = 87.50

        mockMvc.perform(get("/api/attendance/mySubsidy")
                        .header("token", STUDENT_TOKEN)
                        .param("month", CURRENT_MONTH))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userName").value("张三"))
                .andExpect(jsonPath("$.data.recordCount").value(2))
                .andExpect(jsonPath("$.data.totalHours").value(6.5))
                .andExpect(jsonPath("$.data.totalAmount").value(162.50));
    }

    @Test
    @DisplayName("TC-12 当月无用工记录：工时 0、金额 0.00")
    void tc12_mySubsidyEmpty() throws Exception {
        mockMvc.perform(get("/api/attendance/mySubsidy")
                        .header("token", STUDENT_TOKEN)
                        .param("month", CURRENT_MONTH))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.recordCount").value(0))
                .andExpect(jsonPath("$.data.totalHours").value(0.0))
                .andExpect(jsonPath("$.data.totalAmount").value(0.00));
    }

    @Test
    @DisplayName("TC-13 月份格式非法：400")
    void tc13_invalidMonth() throws Exception {
        mockMvc.perform(get("/api/attendance/mySubsidy")
                        .header("token", STUDENT_TOKEN)
                        .param("month", "2026-13"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("月份格式应为 yyyy-MM，例如 2026-11"));
    }

    // ==================== 部门 / 全校汇总 ====================

    @Test
    @DisplayName("TC-14 部门管理员查本部门汇总：只含本部门学生")
    void tc14_deptSummaryOwnDept() throws Exception {
        insertRecord(4L, JOB_ID, DEPT_ID, LocalDateTime.now().minusMinutes(300), 180, 1);
        insertRecord(5L, JOB_ID, DEPT_ID, LocalDateTime.now().minusMinutes(300), 120, 1);
        // 部门 2 的记录不应出现在部门 1 的汇总里
        insertRecord(4L, 2L, 2L, LocalDateTime.now().minusMinutes(300), 60, 1);

        mockMvc.perform(get("/api/attendance/deptSummary")
                        .header("token", DEPT_TOKEN)
                        .param("month", CURRENT_MONTH))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.deptId").value(1))
                .andExpect(jsonPath("$.data.personCount").value(2))
                .andExpect(jsonPath("$.data.totalHours").value(5.0))
                .andExpect(jsonPath("$.data.totalAmount").value(125.00));
    }

    @Test
    @DisplayName("TC-15 部门管理员查其他部门：403")
    void tc15_deptSummaryOtherDept() throws Exception {
        mockMvc.perform(get("/api/attendance/deptSummary")
                        .header("token", DEPT_TOKEN)
                        .param("month", CURRENT_MONTH)
                        .param("deptId", "2"))
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("权限不足：只能查看本部门的工时汇总"));
    }

    @Test
    @DisplayName("TC-16 学生查部门汇总：403")
    void tc16_deptSummaryByStudent() throws Exception {
        mockMvc.perform(get("/api/attendance/deptSummary")
                        .header("token", STUDENT_TOKEN)
                        .param("month", CURRENT_MONTH))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("TC-17 中心管理员查全校汇总：按部门下钻")
    void tc17_schoolSummary() throws Exception {
        insertRecord(4L, JOB_ID, DEPT_ID, LocalDateTime.now().minusMinutes(300), 180, 1);
        insertRecord(5L, JOB_ID, DEPT_ID, LocalDateTime.now().minusMinutes(300), 120, 1);
        insertRecord(4L, 2L, 2L, LocalDateTime.now().minusMinutes(300), 60, 1);

        mockMvc.perform(get("/api/attendance/schoolSummary")
                        .header("token", CENTER_TOKEN)
                        .param("month", CURRENT_MONTH))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.deptCount").value(2))
                .andExpect(jsonPath("$.data.personCount").value(2))
                .andExpect(jsonPath("$.data.totalHours").value(6.0))
                .andExpect(jsonPath("$.data.totalAmount").value(150.00));
    }

    @Test
    @DisplayName("TC-18 中心管理员查部门汇总未指定 deptId：400")
    void tc18_deptSummaryByCenterWithoutDeptId() throws Exception {
        mockMvc.perform(get("/api/attendance/deptSummary")
                        .header("token", CENTER_TOKEN)
                        .param("month", CURRENT_MONTH))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("助学中心管理员查询部门汇总时需指定部门编号 deptId"));
    }

    @Test
    @DisplayName("TC-21 异常待确认的记录不计入补贴（只算已签退）")
    void tc21_abnormalRecordNotPaid() throws Exception {
        insertRecord(4L, JOB_ID, DEPT_ID, LocalDateTime.now().minusHours(13), 780, 2);

        mockMvc.perform(get("/api/attendance/mySubsidy")
                        .header("token", STUDENT_TOKEN)
                        .param("month", CURRENT_MONTH))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.recordCount").value(0))
                .andExpect(jsonPath("$.data.totalAmount").value(0.00));
    }

    // ==================== 测试辅助 ====================

    /**
     * 直接插一条签到记录（模拟历史数据），避免测试用例依赖"先签到再签退"的时序。
     * workMinutes 传 null 表示尚未签退。
     */
    private void insertRecord(Long userId, Long jobId, Long deptId,
                              LocalDateTime checkInTime, Integer workMinutes, int status) {
        AttendanceRecord r = new AttendanceRecord();
        r.setUserId(userId);
        r.setJobId(jobId);
        r.setDeptId(deptId);
        r.setJobName("图书馆图书整理助理");
        r.setHourSalary(SALARY);
        r.setCheckInTime(checkInTime);
        if (workMinutes != null) {
            r.setCheckOutTime(checkInTime.plusMinutes(workMinutes));
        }
        r.setWorkMinutes(workMinutes);
        r.setStatus(status);
        recordRepository.save(r);
    }
}
