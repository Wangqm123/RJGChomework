package com.campus.attendance;

import com.campus.attendance.client.AuthClient;
import com.campus.attendance.client.JobClient;
import com.campus.attendance.common.AttendanceStatus;
import com.campus.attendance.common.BusinessException;
import com.campus.attendance.common.SubsidyStatus;
import com.campus.attendance.entity.AttendanceRecord;
import com.campus.attendance.entity.Subsidy;
import com.campus.attendance.repository.AttendanceRecordRepository;
import com.campus.attendance.repository.SubsidyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 月度结算与发放测试（TC-24 起，接 AttendanceServiceTests 的编号）。
 *
 * 这一组验证的是"核算 → 留痕 → 打款"闭环：
 *   结算生成发放单 → 重复结算只刷新不新增 → 已发放冻结 → 批量发放 → 幂等。
 *
 * 测试库用独立内存库，每个用例结束后回滚，互不影响。
 * 工时数据直接构造（不用接口签到）：结算只关心"已签退记录"，构造数据更直接、也不依赖岗位服务。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:settletest;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.show-sql=false"
})
@AutoConfigureMockMvc
@Transactional
class SettlementServiceTests {

    private static final String STUDENT_TOKEN = "token-student1";
    private static final String DEPT_TOKEN = "token-deptA";
    private static final String CENTER_TOKEN = "token-center";

    /** 用固定月份，避免"当前月"变化导致断言漂移 */
    private static final String MONTH = "2026-11";
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 11, 10, 9, 0);

    private static final BigDecimal SALARY_25 = new BigDecimal("25.00");
    private static final BigDecimal SALARY_20 = new BigDecimal("20.00");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AttendanceRecordRepository recordRepository;

    @Autowired
    private SubsidyRepository subsidyRepository;

    @MockBean
    private AuthClient authClient;

    @MockBean
    private JobClient jobClient;

    @BeforeEach
    void setUp() {
        when(authClient.checkToken(any())).thenAnswer(inv -> {
            String token = inv.getArgument(0);
            if (token == null) {
                throw new BusinessException(401, "未登录或 Token 已失效");
            }
            return switch (token) {
                case STUDENT_TOKEN -> new AuthClient.Session(4L, 1);
                case DEPT_TOKEN -> new AuthClient.Session(2L, 2);
                case CENTER_TOKEN -> new AuthClient.Session(3L, 3);
                default -> throw new BusinessException(401, "未登录或 Token 已失效");
            };
        });
        when(authClient.getUserInfo(anyLong())).thenAnswer(inv -> {
            Long userId = inv.getArgument(0);
            return switch (userId.intValue()) {
                case 2 -> new AuthClient.UserInfo(2L, "图书馆管理员", 1L, 2);
                case 3 -> new AuthClient.UserInfo(3L, "助学中心管理员", 0L, 3);
                case 4 -> new AuthClient.UserInfo(4L, "张三", 0L, 1);
                case 5 -> new AuthClient.UserInfo(5L, "李四", 0L, 1);
                default -> new AuthClient.UserInfo(userId, "用户" + userId, 1L, 1);
            };
        });
    }

    // ==================== 结算 ====================

    @Test
    @DisplayName("TC-24 全校结算：按人+部门生成发放单，金额与明细核算一致")
    void tc24_settleCreatesSlips() throws Exception {
        // student1 在部门 1 干了 120 分钟（25 元/时）= 50 元；student2 干了 60 分钟（20 元/时）= 20 元
        insertRecord(4L, 1L, 120, SALARY_25);
        insertRecord(5L, 1L, 60, SALARY_20);

        mockMvc.perform(post("/api/attendance/settle")
                        .header("token", CENTER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"" + MONTH + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.createdCount").value(2))
                .andExpect(jsonPath("$.data.refreshedCount").value(0))
                .andExpect(jsonPath("$.data.skippedCount").value(0))
                .andExpect(jsonPath("$.data.totalAmount").value(70.00))
                .andExpect(jsonPath("$.data.list[0].statusName").value("待发放"));

        List<Subsidy> slips = subsidyRepository.findBySettleMonthOrderByDeptIdAscUserIdAsc(MONTH);
        assertThat(slips).hasSize(2);
        assertThat(slips).allMatch(s -> s.getStatus() == SubsidyStatus.PENDING);
        // 发放单要能回溯到原始打卡记录
        assertThat(slips).allMatch(s -> s.getRecordIds() != null && !s.getRecordIds().isBlank());
    }

    @Test
    @DisplayName("TC-25 重复结算：刷新待发放单，不新增单据、金额不翻倍")
    void tc25_settleTwiceOnlyRefresh() throws Exception {
        insertRecord(4L, 1L, 120, SALARY_25);

        settle();
        settle();

        List<Subsidy> slips = subsidyRepository.findBySettleMonthOrderByDeptIdAscUserIdAsc(MONTH);
        assertThat(slips).hasSize(1);
        assertThat(slips.get(0).getTotalAmount()).isEqualByComparingTo("50.00");

        mockMvc.perform(post("/api/attendance/settle")
                        .header("token", CENTER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"" + MONTH + "\"}"))
                .andExpect(jsonPath("$.data.createdCount").value(0))
                .andExpect(jsonPath("$.data.refreshedCount").value(1))
                .andExpect(jsonPath("$.data.totalAmount").value(50.00));
    }

    @Test
    @DisplayName("TC-26 结算后补录工时：待发放单金额被刷新为新值")
    void tc26_settleRefreshAfterSupplement() throws Exception {
        insertRecord(4L, 1L, 60, SALARY_25);   // 25 元
        settle();

        insertRecord(4L, 1L, 60, SALARY_25);   // 再补一条，共 50 元
        settle();

        List<Subsidy> slips = subsidyRepository.findBySettleMonthOrderByDeptIdAscUserIdAsc(MONTH);
        assertThat(slips).hasSize(1);
        assertThat(slips.get(0).getTotalAmount()).isEqualByComparingTo("50.00");
        assertThat(slips.get(0).getRecordCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("TC-27 已发放的单不可被重新结算：skipped 计数且金额冻结")
    void tc27_paidSlipIsFrozen() throws Exception {
        insertRecord(4L, 1L, 60, SALARY_25);
        settle();
        pay(MONTH);

        // 再补录 60 分钟，然后重新结算：钱已发，不能再改
        insertRecord(4L, 1L, 60, SALARY_25);
        mockMvc.perform(post("/api/attendance/settle")
                        .header("token", CENTER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"" + MONTH + "\"}"))
                .andExpect(jsonPath("$.data.createdCount").value(0))
                .andExpect(jsonPath("$.data.refreshedCount").value(0))
                .andExpect(jsonPath("$.data.skippedCount").value(1))
                .andExpect(jsonPath("$.data.totalAmount").value(0.00));

        Subsidy slip = subsidyRepository.findBySettleMonthOrderByDeptIdAscUserIdAsc(MONTH).get(0);
        assertThat(slip.getStatus()).isEqualTo(SubsidyStatus.PAID);
        assertThat(slip.getTotalAmount()).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("TC-28 跨部门打工：按部门各生成一张单，部门对账不串")
    void tc28_onePersonTwoDepts() throws Exception {
        insertRecord(4L, 1L, 60, SALARY_25);   // 部门 1：25 元
        insertRecord(4L, 2L, 120, SALARY_20);  // 部门 2：40 元
        insertRecord(5L, 1L, 60, SALARY_20);   // 部门 1：20 元

        mockMvc.perform(post("/api/attendance/settle")
                        .header("token", CENTER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"" + MONTH + "\"}"))
                .andExpect(jsonPath("$.data.createdCount").value(3))
                .andExpect(jsonPath("$.data.totalAmount").value(85.00));

        // 只结算部门 1 时应是 45 元（25 + 20）
        mockMvc.perform(post("/api/attendance/settle")
                        .header("token", CENTER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"" + MONTH + "\",\"deptId\":1}"))
                .andExpect(jsonPath("$.data.createdCount").value(0))
                .andExpect(jsonPath("$.data.refreshedCount").value(2))
                .andExpect(jsonPath("$.data.totalAmount").value(45.00));
    }

    @Test
    @DisplayName("TC-29 学生无权结算：403")
    void tc29_studentCannotSettle() throws Exception {
        insertRecord(4L, 1L, 60, SALARY_25);
        mockMvc.perform(post("/api/attendance/settle")
                        .header("token", STUDENT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"" + MONTH + "\"}"))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("TC-30 部门管理员无权结算：403（钱由中心统一核算）")
    void tc30_deptAdminCannotSettle() throws Exception {
        insertRecord(4L, 1L, 60, SALARY_25);
        mockMvc.perform(post("/api/attendance/settle")
                        .header("token", DEPT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"" + MONTH + "\"}"))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("TC-31 该月没有可结算工时时：400 且不生成空单")
    void tc31_settleEmptyMonth() throws Exception {
        mockMvc.perform(post("/api/attendance/settle")
                        .header("token", CENTER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2026-10\"}"))
                .andExpect(jsonPath("$.code").value(400));
        assertThat(subsidyRepository.findBySettleMonthOrderByDeptIdAscUserIdAsc("2026-10")).isEmpty();
    }

    @Test
    @DisplayName("TC-32 结算缺少月份：400（结算必须显式指定月份，不默认当月）")
    void tc32_settleWithoutMonth() throws Exception {
        mockMvc.perform(post("/api/attendance/settle")
                        .header("token", CENTER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== 发放 ====================

    @Test
    @DisplayName("TC-33 批量发放：待发放全部转为已发放，金额汇总正确")
    void tc33_payAll() throws Exception {
        insertRecord(4L, 1L, 120, SALARY_25);
        insertRecord(5L, 1L, 60, SALARY_20);
        settle();

        mockMvc.perform(post("/api/attendance/pay")
                        .header("token", CENTER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"" + MONTH + "\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.paidCount").value(2))
                .andExpect(jsonPath("$.data.paidAmount").value(70.00));

        List<Subsidy> slips = subsidyRepository.findBySettleMonthOrderByDeptIdAscUserIdAsc(MONTH);
        assertThat(slips).allMatch(s -> s.getStatus() == SubsidyStatus.PAID && s.getPayTime() != null);
    }

    @Test
    @DisplayName("TC-34 重复发放：第二次 400，不会重复计数")
    void tc34_payTwice() throws Exception {
        insertRecord(4L, 1L, 60, SALARY_25);
        settle();
        pay(MONTH);

        mockMvc.perform(post("/api/attendance/pay")
                        .header("token", CENTER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"" + MONTH + "\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("TC-35 未结算直接发放：400 提示先结算")
    void tc35_payWithoutSettle() throws Exception {
        insertRecord(4L, 1L, 60, SALARY_25);
        mockMvc.perform(post("/api/attendance/pay")
                        .header("token", CENTER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"" + MONTH + "\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== 查询与隔离 ====================

    @Test
    @DisplayName("TC-36 发放单列表：部门管理员只看本部门，跨部门 403")
    void tc36_subsidyListIsolation() throws Exception {
        insertRecord(4L, 1L, 60, SALARY_25);
        insertRecord(5L, 2L, 60, SALARY_20);
        settle();

        mockMvc.perform(get("/api/attendance/subsidyList")
                        .header("token", DEPT_TOKEN)
                        .param("month", MONTH))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].deptId").value(1));

        mockMvc.perform(get("/api/attendance/subsidyList")
                        .header("token", DEPT_TOKEN)
                        .param("month", MONTH)
                        .param("deptId", "2"))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("TC-37 发放单列表按状态过滤：status=1 只返回已发放")
    void tc37_subsidyListFilterByStatus() throws Exception {
        insertRecord(4L, 1L, 60, SALARY_25);
        settle();
        pay(MONTH);

        mockMvc.perform(get("/api/attendance/subsidyList")
                        .header("token", CENTER_TOKEN)
                        .param("month", MONTH)
                        .param("status", "1"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].statusName").value("已发放"));

        mockMvc.perform(get("/api/attendance/subsidyList")
                        .header("token", CENTER_TOKEN)
                        .param("month", MONTH)
                        .param("status", "0"))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("TC-38 学生 mySubsidy 带出发放状态：未结算 null → 待发放 0 → 已发放 1")
    void tc38_mySubsidySettleStatus() throws Exception {
        insertRecord(4L, 1L, 60, SALARY_25);

        assertMySubsidyStatus(null, "未结算");
        settle();
        assertMySubsidyStatus(0, "待发放");
        pay(MONTH);
        assertMySubsidyStatus(1, "已发放");
    }

    // ==================== 并发兜底 ====================

    @Test
    @DisplayName("TC-39 唯一键兜底：同一人同一部门同一月重复插入被数据库拒绝")
    void tc39_uniqueKeyRejectsDuplicate() {
        Subsidy slip = newSlip(4L, 1L);
        subsidyRepository.saveAndFlush(slip);

        Subsidy duplicate = newSlip(4L, 1L);
        assertThatThrownBy(() -> subsidyRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ==================== 辅助方法 ====================

    /** 构造一条"已签退"的工时记录 */
    private void insertRecord(Long userId, Long deptId, int minutes, BigDecimal salary) {
        AttendanceRecord r = new AttendanceRecord();
        r.setJobId(1L);
        r.setUserId(userId);
        r.setDeptId(deptId);
        r.setJobName("图书馆图书整理助理");
        r.setHourSalary(salary);
        r.setCheckInTime(BASE_TIME);
        r.setCheckOutTime(BASE_TIME.plusMinutes(minutes));
        r.setWorkMinutes(minutes);
        r.setStatus(AttendanceStatus.CHECKED_OUT);
        recordRepository.save(r);
    }

    private void settle() throws Exception {
        mockMvc.perform(post("/api/attendance/settle")
                        .header("token", CENTER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"" + MONTH + "\"}"))
                .andExpect(jsonPath("$.code").value(200));
    }

    private void pay(String month) throws Exception {
        mockMvc.perform(post("/api/attendance/pay")
                        .header("token", CENTER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"" + month + "\"}"))
                .andExpect(jsonPath("$.code").value(200));
    }

    private void assertMySubsidyStatus(Integer expectedStatus, String expectedName) throws Exception {
        var result = mockMvc.perform(get("/api/attendance/mySubsidy")
                        .header("token", STUDENT_TOKEN)
                        .param("month", MONTH))
                .andExpect(jsonPath("$.data.settleStatusName").value(expectedName))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        if (expectedStatus == null) {
            assertThat(body).contains("\"settleStatus\":null");
        } else {
            assertThat(body).contains("\"settleStatus\":" + expectedStatus);
        }
    }

    private Subsidy newSlip(Long userId, Long deptId) {
        Subsidy s = new Subsidy();
        s.setUserId(userId);
        s.setDeptId(deptId);
        s.setSettleMonth(MONTH);
        s.setRecordCount(1);
        s.setWorkMinutes(60);
        s.setTotalAmount(new BigDecimal("25.00"));
        s.setStatus(SubsidyStatus.PENDING);
        return s;
    }
}
