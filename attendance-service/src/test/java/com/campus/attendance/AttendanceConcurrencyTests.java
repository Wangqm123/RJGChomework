package com.campus.attendance;

import com.campus.attendance.client.AuthClient;
import com.campus.attendance.client.JobClient;
import com.campus.attendance.entity.AttendanceRecord;
import com.campus.attendance.repository.AttendanceRecordRepository;
import com.campus.attendance.service.AttendanceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * TC-20 并发签到（对应《成员5-任务执行手册》3.2 节，也是缺陷报告 BUG-04 的复现与回归用例）。
 *
 * 为什么单独一个类、且**不加 @Transactional**：
 * 并发竞态必须在两个事务真正提交的情况下才能暴露；
 * 若整个用例回滚，两个线程看不到彼此已提交的数据，测不出问题。
 *
 * 预期：同一学生同一岗位同时发起两次签到，只有一条成功，另一条返回 400，
 * 库中只留下 1 条未签退记录（修复前实测会留下 2 条，工时被重复计算）。
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
}
