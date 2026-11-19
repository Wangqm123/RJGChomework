package com.campus.job;

import com.campus.job.client.AuthClient;
import com.campus.job.common.ApplyStatus;
import com.campus.job.common.BusinessException;
import com.campus.job.common.JobStatus;
import com.campus.job.entity.Job;
import com.campus.job.entity.JobApply;
import com.campus.job.repository.JobApplyRepository;
import com.campus.job.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 岗位申请与审核服务单元测试（分工要求：结对 B 产出单元测试代码）。
 *
 * 说明：
 * 1. AuthClient（调用权限服务的客户端）用 MockBean 打桩，测试不依赖 8081 服务是否启动，
 *    只验证本服务的业务规则；
 * 2. 测试类加 @Transactional，每个用例结束后数据库自动回滚，用例之间互不影响、可反复执行；
 * 3. 用例覆盖接口文档的 7 个接口 + 需求文档的修改/下架/申请列表补充接口，
 *    重点是"业务冲突"和"越权"这两类异常路径。
 *
 * 预置数据见 resources/data.sql（岗位 1、2 已上架；3 待审核；4 驳回；5 下架；6 招满）。
 */
@SpringBootTest(properties = {
        // 测试用独立的内存库，不污染开发用的 ./data/jobdb
        "spring.datasource.url=jdbc:h2:mem:jobtest;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.show-sql=false"
})
@AutoConfigureMockMvc
@Transactional
class JobServiceTests {

    /** 部门管理员：userId=2，部门=1（与权限服务预置账号 deptA 对应） */
    private static final String DEPT_TOKEN = "token-deptA";
    /** 助学中心管理员：userId=3 */
    private static final String CENTER_TOKEN = "token-center";
    /** 学生 student1：userId=4 */
    private static final String STUDENT_TOKEN = "token-student1";
    /** 学生 student2：userId=5 */
    private static final String STUDENT2_TOKEN = "token-student2";
    /** 另一个部门的管理员：userId=20，部门=2（权限服务未预置，仅用于越权用例） */
    private static final String DEPT2_TOKEN = "token-deptB";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobApplyRepository applyRepository;

    @MockBean
    private AuthClient authClient;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void mockAuthService() {
        mockUser(DEPT_TOKEN, 2L, 2, 1L);
        mockUser(CENTER_TOKEN, 3L, 3, 0L);
        mockUser(STUDENT_TOKEN, 4L, 1, 0L);
        mockUser(STUDENT2_TOKEN, 5L, 1, 0L);
        mockUser(DEPT2_TOKEN, 20L, 2, 2L);
        // 模拟权限服务对"没带 Token"的真实返回：401（不依赖真实 HTTP 调用）
        when(authClient.checkToken(null)).thenThrow(new BusinessException(401, "未登录或 Token 已失效"));
    }

    /** 打桩：Token → 用户，用户 → 部门 */
    private void mockUser(String token, Long userId, int roleId, Long deptId) {
        when(authClient.checkToken(token)).thenReturn(new AuthClient.Session(userId, roleId));
        when(authClient.getUserInfo(userId, token)).thenReturn(new AuthClient.UserInfo(userId, deptId, roleId));
    }

    // ==================== 岗位发布 ====================

    /** 发布成功：新岗位落入待中心审核状态，部门取操作者部门 */
    @Test
    void publishSuccess() throws Exception {
        mockMvc.perform(post("/api/job/publish")
                        .header("token", DEPT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobName\":\"自习室值守\",\"address\":\"图书馆三楼\",\"hourSalary\":20,"
                                + "\"needNum\":2,\"description\":\"维护自习室秩序\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.jobId").isNumber());

        // 校验入库状态：待中心审核(1)、部门 1
        var saved = jobRepository.findAll().stream()
                .filter(job -> "自习室值守".equals(job.getJobName()))
                .findFirst().orElseThrow();
        assertEquals(1, saved.getStatus());
        assertEquals(1L, saved.getDeptId());
        assertEquals(0, saved.getHiredNum());
    }

    /** 学生发布岗位 → 403 */
    @Test
    void publishDeniedForStudent() throws Exception {
        mockMvc.perform(post("/api/job/publish")
                        .header("token", STUDENT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobName\":\"x\",\"address\":\"y\",\"hourSalary\":10,"
                                + "\"needNum\":1,\"description\":\"z\"}"))
                .andExpect(jsonPath("$.code").value(403));
    }

    /** 部门管理员把岗位挂到别的部门 → 403（越权拦截） */
    @Test
    void publishToOtherDeptDenied() throws Exception {
        mockMvc.perform(post("/api/job/publish")
                        .header("token", DEPT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobName\":\"x\",\"address\":\"y\",\"hourSalary\":10,"
                                + "\"needNum\":1,\"description\":\"z\",\"deptId\":2}"))
                .andExpect(jsonPath("$.code").value(403));
    }

    /** 时薪为 0 → 参数校验 400 */
    @Test
    void publishInvalidSalary() throws Exception {
        mockMvc.perform(post("/api/job/publish")
                        .header("token", DEPT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobName\":\"x\",\"address\":\"y\",\"hourSalary\":0,"
                                + "\"needNum\":1,\"description\":\"z\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    /** 没有 Token → 401 */
    @Test
    void publishWithoutToken() throws Exception {
        mockMvc.perform(post("/api/job/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobName\":\"x\",\"address\":\"y\",\"hourSalary\":10,"
                                + "\"needNum\":1,\"description\":\"z\"}"))
                .andExpect(jsonPath("$.code").value(401));
    }

    // ==================== 岗位审核 ====================

    /** 中心审核通过：待审核岗位 → 已上架，学生列表可见 */
    @Test
    void auditJobPass() throws Exception {
        mockMvc.perform(post("/api/job/auditJob")
                        .header("token", CENTER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":3,\"auditResult\":\"pass\",\"auditRemark\":\"同意上架\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(2))
                .andExpect(jsonPath("$.data.statusName").value("已上架"));

        mockMvc.perform(get("/api/job/listJob"))
                .andExpect(jsonPath("$.data.total").value(3));
    }

    /** 驳回不填理由 → 400 */
    @Test
    void auditJobRejectWithoutRemark() throws Exception {
        mockMvc.perform(post("/api/job/auditJob")
                        .header("token", CENTER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":3,\"auditResult\":\"reject\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    /** 非中心管理员审核 → 403 */
    @Test
    void auditJobDeniedForDeptAdmin() throws Exception {
        mockMvc.perform(post("/api/job/auditJob")
                        .header("token", DEPT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":3,\"auditResult\":\"pass\"}"))
                .andExpect(jsonPath("$.code").value(403));
    }

    /** 重复审核已上架岗位 → 400（业务冲突） */
    @Test
    void auditJobTwice() throws Exception {
        mockMvc.perform(post("/api/job/auditJob")
                        .header("token", CENTER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":1,\"auditResult\":\"pass\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== 岗位浏览 ====================

    /** 列表只返回已上架岗位，按 id 倒序 */
    @Test
    void listOnlyListedJobs() throws Exception {
        mockMvc.perform(get("/api/job/listJob").param("page", "1").param("size", "10"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.list[0].jobId").value(2))
                .andExpect(jsonPath("$.data.list[0].status").value(2))
                .andExpect(jsonPath("$.data.list[1].jobId").value(1));
    }

    /** 分页参数非法时兜底（size 超上限被截断，不会一次拉全库） */
    @Test
    void listJobWithInvalidPageParam() throws Exception {
        mockMvc.perform(get("/api/job/listJob").param("page", "0").param("size", "999"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2));
    }

    // ==================== 学生投递 ====================

    /** 投递已上架岗位成功（student2 尚未投递过 job2） */
    @Test
    void applyJobSuccess() throws Exception {
        mockMvc.perform(post("/api/job/applyJob")
                        .header("token", STUDENT2_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":2,\"userId\":5}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.applyId").isNumber());

        assertTrue(applyRepository.existsByJobIdAndUserId(2L, 5L));
    }

    /** 重复投递同一岗位 → 400 */
    @Test
    void applyJobTwice() throws Exception {
        mockMvc.perform(post("/api/job/applyJob")
                        .header("token", STUDENT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":1,\"userId\":4}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    /** 投递未上架（待审核）岗位 → 400 */
    @Test
    void applyJobNotListed() throws Exception {
        mockMvc.perform(post("/api/job/applyJob")
                        .header("token", STUDENT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":3,\"userId\":4}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    /** 冒用他人 userId 投递 → 403 */
    @Test
    void applyJobForAnotherUserDenied() throws Exception {
        mockMvc.perform(post("/api/job/applyJob")
                        .header("token", STUDENT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":2,\"userId\":5}"))
                .andExpect(jsonPath("$.code").value(403));
    }

    // ==================== 申请审核 ====================

    /** 录用成功：申请变已录用，岗位最后 1 个名额用完后自动招满 */
    @Test
    void auditApplyAccept() throws Exception {
        mockMvc.perform(post("/api/job/auditApply")
                        .header("token", DEPT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applyId\":2,\"applyResult\":\"accept\",\"remark\":\"同意录用\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.statusName").value("已录用"))
                .andExpect(jsonPath("$.data.jobStatus").value(4))
                .andExpect(jsonPath("$.data.jobStatusName").value("已招满"));
    }

    /** 名额已满的岗位不能再录用 → 400 */
    @Test
    void auditApplyWhenJobFull() throws Exception {
        mockMvc.perform(post("/api/job/auditApply")
                        .header("token", DEPT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applyId\":4,\"applyResult\":\"accept\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    /** 审核其他部门岗位的申请 → 403 */
    @Test
    void auditApplyForOtherDeptDenied() throws Exception {
        mockMvc.perform(post("/api/job/auditApply")
                        .header("token", DEPT2_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applyId\":2,\"applyResult\":\"accept\"}"))
                .andExpect(jsonPath("$.code").value(403));
    }

    /** 重复审核已处理申请 → 400 */
    @Test
    void auditApplyTwice() throws Exception {
        mockMvc.perform(post("/api/job/auditApply")
                        .header("token", DEPT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applyId\":1,\"applyResult\":\"refuse\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== 录用查询 / 我的申请 ====================

    /** 工时服务调用（透传学生 Token）：已录用 true，未录用/被拒 false */
    @Test
    void getStudentHired() throws Exception {
        mockMvc.perform(get("/api/job/getStudentHired")
                        .header("token", STUDENT_TOKEN)
                        .param("jobId", "1").param("userId", "4"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/api/job/getStudentHired")
                        .header("token", STUDENT_TOKEN)
                        .param("jobId", "2").param("userId", "4"))
                .andExpect(jsonPath("$.data").value(false));
    }

    /** 匿名调用 getStudentHired → 401（联调评审 4-2） */
    @Test
    void getStudentHiredRequiresToken() throws Exception {
        mockMvc.perform(get("/api/job/getStudentHired")
                        .param("jobId", "1").param("userId", "4"))
                .andExpect(jsonPath("$.code").value(401));
    }

    /** 学生查询他人录用状态 → 403 */
    @Test
    void getStudentHiredForAnotherUserDenied() throws Exception {
        mockMvc.perform(get("/api/job/getStudentHired")
                        .header("token", STUDENT_TOKEN)
                        .param("jobId", "1").param("userId", "5"))
                .andExpect(jsonPath("$.code").value(403));
    }

    /** 查询我的申请：只返回本人记录，带岗位信息 */
    @Test
    void listMyApply() throws Exception {
        mockMvc.perform(get("/api/job/listMyApply")
                        .header("token", STUDENT_TOKEN)
                        .param("userId", "4"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].jobId").value(2))
                .andExpect(jsonPath("$.data[0].statusName").value("已拒绝"));
    }

    /** 查看他人申请记录 → 403 */
    @Test
    void listMyApplyForOthersDenied() throws Exception {
        mockMvc.perform(get("/api/job/listMyApply")
                        .header("token", STUDENT_TOKEN)
                        .param("userId", "5"))
                .andExpect(jsonPath("$.code").value(403));
    }

    /** 部门管理员查看本部门岗位的申请列表（补充接口） */
    @Test
    void listApplyForDept() throws Exception {
        mockMvc.perform(get("/api/job/listApply")
                        .header("token", DEPT_TOKEN)
                        .param("jobId", "1"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].userId").value(4))
                .andExpect(jsonPath("$.data[1].userId").value(5));

        mockMvc.perform(get("/api/job/listApply")
                        .header("token", DEPT2_TOKEN)
                        .param("jobId", "1"))
                .andExpect(jsonPath("$.code").value(403));
    }

    // ==================== 岗位详情（工时服务联调补充） ====================

    /** 已下架岗位也能查到时薪、部门、状态（历史工时结算依赖，不能只查已上架列表） */
    @Test
    void getJobDetailForOfflineJob() throws Exception {
        mockMvc.perform(get("/api/job/getJob")
                        .header("token", STUDENT_TOKEN)
                        .param("jobId", "5"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.jobId").value(5))
                .andExpect(jsonPath("$.data.hourSalary").value(18.0))
                .andExpect(jsonPath("$.data.deptId").value(1))
                .andExpect(jsonPath("$.data.status").value(5))
                .andExpect(jsonPath("$.data.statusName").value("已下架"));
    }

    /** 匿名调用 getJob → 401（联调评审 4-2） */
    @Test
    void getJobRequiresToken() throws Exception {
        mockMvc.perform(get("/api/job/getJob").param("jobId", "5"))
                .andExpect(jsonPath("$.code").value(401));
    }

    /** 岗位不存在 → 400 */
    @Test
    void getJobNotFound() throws Exception {
        mockMvc.perform(get("/api/job/getJob")
                        .header("token", STUDENT_TOKEN)
                        .param("jobId", "999"))
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== 修改 / 下架（补充接口） ====================

    /** 已上架岗位下架成功（jobId 走 JSON 请求体） */
    @Test
    void offlineJobSuccess() throws Exception {
        mockMvc.perform(post("/api/job/offlineJob")
                        .header("token", DEPT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":1}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(5))
                .andExpect(jsonPath("$.data.statusName").value("已下架"));

        mockMvc.perform(get("/api/job/listJob"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    /** 待审核岗位不能下架 → 400 */
    @Test
    void offlinePendingJobDenied() throws Exception {
        mockMvc.perform(post("/api/job/offlineJob")
                        .header("token", DEPT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":3}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    /** 下架接口不传 jobId → 400（参数校验） */
    @Test
    void offlineJobWithoutId() throws Exception {
        mockMvc.perform(post("/api/job/offlineJob")
                        .header("token", DEPT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    /** 被驳回岗位修改后重新进入待审核 */
    @Test
    void updateRejectedJobResubmit() throws Exception {
        mockMvc.perform(post("/api/job/updateJob")
                        .header("token", DEPT2_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":4,\"description\":\"负责耗材入库登记、库存盘点，每周 4 小时\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.statusName").value("待中心审核"));
    }

    /** 已下架岗位不能修改 → 400 */
    @Test
    void updateOfflineJobDenied() throws Exception {
        mockMvc.perform(post("/api/job/updateJob")
                        .header("token", DEPT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":5,\"jobName\":\"新名字\"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    /** 把岗位名称改成空白字符串 → 400（不能绕过必填校验） */
    @Test
    void updateJobWithBlankNameRejected() throws Exception {
        mockMvc.perform(post("/api/job/updateJob")
                        .header("token", DEPT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":1,\"jobName\":\"   \"}"))
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== 并发控制（乐观锁） ====================

    /**
     * 模拟"两个管理员同时基于同一版本修改岗位"：
     * 另一个事务先提交（模拟先完成录用），当前事务拿着过期版本继续提交，
     * 必须抛乐观锁异常 —— 这就是防止超额录用的关键。
     *
     * 用例自建临时岗位并在结束后删除，避免污染种子数据（REQUIRES_NEW 提交不随测试回滚）。
     */
    @Test
    void concurrentUpdateTriggersOptimisticLock() {
        TransactionTemplate newTx = new TransactionTemplate(transactionManager);
        newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // 1. 独立事务插入一个临时岗位
        Long jobId = newTx.execute(status -> {
            Job job = new Job();
            job.setJobName("并发测试岗位");
            job.setAddress("测试地点");
            job.setHourSalary(new BigDecimal("10.00"));
            job.setNeedNum(1);
            job.setHiredNum(0);
            job.setDescription("并发控制用例");
            job.setDeptId(1L);
            job.setStatus(JobStatus.PENDING_AUDIT);
            job.setPublisherId(2L);
            return jobRepository.saveAndFlush(job).getId();
        });

        // 2. 当前事务读到 version 快照
        Job stale = jobRepository.findById(jobId).orElseThrow();

        // 3. 另一个事务先提交修改（模拟另一个管理员先完成录用）
        newTx.execute(status -> {
            Job job = jobRepository.findById(jobId).orElseThrow();
            job.setHiredNum(1);
            job.setStatus(JobStatus.FULL);
            jobRepository.saveAndFlush(job);
            return null;
        });

        // 4. 过期版本继续提交 → 影响行数为 0 → 乐观锁异常
        stale.setJobName("并发改名");
        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> jobRepository.saveAndFlush(stale));

        // 5. 清理临时岗位
        newTx.execute(status -> {
            jobRepository.deleteById(jobId);
            return null;
        });
    }

    /**
     * 同一份申请被并发"录用 + 拒绝"：
     * 后提交的一方必须因版本号过期而失败，避免出现
     * "岗位已录用人数 +1，但申请状态被改成已拒绝"的不一致数据。
     */
    @Test
    void concurrentAuditOfSameApplyTriggersOptimisticLock() {
        TransactionTemplate newTx = new TransactionTemplate(transactionManager);
        newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // 1. 独立事务插入一条临时申请（job1 + 不存在的学生 userId=42，避免影响种子数据）
        Long applyId = newTx.execute(status -> {
            JobApply apply = new JobApply();
            apply.setJobId(1L);
            apply.setUserId(42L);
            apply.setStatus(ApplyStatus.PENDING);
            return applyRepository.saveAndFlush(apply).getId();
        });

        // 2. 当前事务读到 version 快照
        JobApply stale = applyRepository.findById(applyId).orElseThrow();

        // 3. 另一个事务先提交"录用"
        newTx.execute(status -> {
            JobApply apply = applyRepository.findById(applyId).orElseThrow();
            apply.setStatus(ApplyStatus.HIRED);
            apply.setAuditTime(LocalDateTime.now());
            applyRepository.saveAndFlush(apply);
            return null;
        });

        // 4. 过期版本继续提交"拒绝" → 乐观锁异常
        stale.setStatus(ApplyStatus.REFUSED);
        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> applyRepository.saveAndFlush(stale));

        // 5. 清理临时申请
        newTx.execute(status -> {
            applyRepository.deleteById(applyId);
            return null;
        });
    }
}
