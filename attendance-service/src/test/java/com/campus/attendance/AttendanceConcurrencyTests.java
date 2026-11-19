package com.campus.attendance;

import com.campus.attendance.client.AuthClient;
import com.campus.attendance.client.JobClient;
import com.campus.attendance.common.AttendanceStatus;
import com.campus.attendance.entity.AttendanceRecord;
import com.campus.attendance.repository.AttendanceRecordRepository;
import com.campus.attendance.service.AttendanceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 并发竞态用例：TC-20 并发签到（《成员5-任务执行手册》3.2 节，也是缺陷报告 BUG-04 的复现与回归用例）、
 * TC-48 并发签退（评审表 5-3 「checkOut 无并发保护」的整改回归用例）。
 *
 * 为什么单独一个类、且**不加 @Transactional**：
 * 并发竞态必须在两个事务真正提交的情况下才能暴露；
 * 若整个用例回滚，两个线程看不到彼此已提交的数据，测不出问题。
 *
 * TC-20 预期：同一学生同一岗位同时发起两次签到，只有一条成功，另一条返回 400，
 * 库中只留下 1 条未签退记录（修复前实测会留下 2 条，工时被重复计算）。
 * TC-48 预期：同一条已签到记录被两个请求同时签退，只有一个成功，
 * 另一个返回 400，工时只结算一次、不会被写两遍。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:attendanceconcurrency;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.show-sql=false"
})
class AttendanceConcurrencyTests {

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private AttendanceRecordRepository repository;

    @MockBean
    private AuthClient authClient;

    @MockBean
    private JobClient jobClient;

    @Test
    @DisplayName("TC-20 同岗位并发签到：只有一条成功，另一条 400，库中只留 1 条未签退记录")
    void tc20_concurrentCheckIn() throws Exception {
        when(authClient.checkToken("t")).thenReturn(new AuthClient.Session(4L, 1));
        when(jobClient.getJob(anyLong(), anyString()))
                .thenReturn(new JobClient.JobInfo(1L, "图书馆图书整理助理", new BigDecimal("25.00"), 1L, 2));
        when(jobClient.isStudentHired(anyLong(), anyLong(), anyString())).thenReturn(true);

        int threads = 2;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        Callable<String> task = () -> {
            start.await();
            try {
                attendanceService.checkIn("t", 1L);
                return "OK";
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                return e.getClass().getSimpleName() + ": " + msg;
            }
        };

        Future<String> first = pool.submit(task);
        Future<String> second = pool.submit(task);
        start.countDown();          // 两线程同时放行，尽可能撞在同一时间窗

        String r1 = first.get(20, TimeUnit.SECONDS);
        String r2 = second.get(20, TimeUnit.SECONDS);
        pool.shutdown();

        long success = List.of(r1, r2).stream().filter("OK"::equals).count();
        assertEquals(1, success, "并发签到应只有一条成功，实际：" + r1 + " / " + r2);

        String failed = "OK".equals(r1) ? r2 : r1;
        assertTrue(failed.contains("已签到且未签退"), "失败方应提示重复签到，实际：" + failed);

        List<AttendanceRecord> all = repository.findAll();
        long pending = all.stream()
                .filter(r -> r.getStatus() != null && r.getStatus() == 0)
                .count();
        assertEquals(1, pending, "库中未签退记录应只有 1 条，实际：" + pending);
    }

    /**
     * 评审表 5-3「checkOut 无并发保护」的回归用例。
     *
     * 修复前是"查出来 → 改字段 → save"，两个并发签退都会读到 status=0，后写的覆盖先写的，
     * 同一条签到被结算两次（工时/金额错乱）。现在签退改走带 `AND status = 0` 的条件更新，
     * 第二次影响 0 行，因此必须只有一个成功。
     *
     * 失败方可能落到两种提示，都是正确的防护结果：
     * 1. 两个请求都查到了记录 → 条件更新 0 行 → 「该签到记录已被签退，请勿重复操作」；
     * 2. 第二个请求查询时前一个已提交 → 查不到未签退记录 → 「未找到该岗位的签到记录，请先签到」。
     */
    @Test
    @DisplayName("TC-48 同一记录并发签退：只有一条成功，另一条 400，工时不被结算两次（评审表 5-3）")
    void tc48_concurrentCheckOut() throws Exception {
        final long jobId = 2L;      // 与 TC-20 用的 jobId=1 区分，避免两个用例的数据互相干扰
        when(authClient.checkToken("t")).thenReturn(new AuthClient.Session(4L, 1));

        // 直接落一条 120 分钟前签到、仍处于「已签到未签退」的记录，把验证点收敛到签退本身
        AttendanceRecord seeded = new AttendanceRecord();
        seeded.setUserId(4L);
        seeded.setJobId(jobId);
        seeded.setDeptId(1L);
        seeded.setJobName("实验室设备维护助理");
        seeded.setHourSalary(new BigDecimal("25.00"));
        seeded.setCheckInTime(LocalDateTime.now().minusMinutes(120));
        seeded.setStatus(AttendanceStatus.CHECKED_IN);
        seeded.setActiveKey(AttendanceRecord.buildActiveKey(4L, jobId));
        repository.saveAndFlush(seeded);

        int threads = 2;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        Callable<String> task = () -> {
            start.await();
            try {
                attendanceService.checkOut("t", jobId);
                return "OK";
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                return e.getClass().getSimpleName() + ": " + msg;
            }
        };

        Future<String> first = pool.submit(task);
        Future<String> second = pool.submit(task);
        start.countDown();          // 两线程同时放行，尽可能撞在同一时间窗

        String r1 = first.get(20, TimeUnit.SECONDS);
        String r2 = second.get(20, TimeUnit.SECONDS);
        pool.shutdown();

        long success = List.of(r1, r2).stream().filter("OK"::equals).count();
        assertEquals(1, success, "并发签退应只有一条成功，实际：" + r1 + " / " + r2);

        String failed = "OK".equals(r1) ? r2 : r1;
        assertTrue(failed.contains("已被签退") || failed.contains("未找到该岗位的签到记录"),
                "失败方应提示已被签退或无需签退，实际：" + failed);

        List<AttendanceRecord> records = repository.findAll().stream()
                .filter(r -> r.getJobId() != null && r.getJobId() == jobId)
                .toList();
        assertEquals(1, records.size(), "并发签退不应产生额外记录，实际：" + records.size());

        AttendanceRecord after = records.get(0);
        assertEquals(AttendanceStatus.CHECKED_OUT, after.getStatus(), "记录应被正常签退");
        assertEquals(120, after.getWorkMinutes(), "工时只应结算一次，不能被签退两次写乱");
        assertNull(after.getActiveKey(), "签退后排他键应置空，允许同一岗位下次签到");
    }
}
