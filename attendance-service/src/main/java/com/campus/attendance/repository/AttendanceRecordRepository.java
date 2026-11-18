package com.campus.attendance.repository;

import com.campus.attendance.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 签到记录数据访问。
 *
 * 月份过滤统一用 **半开区间** [月初 00:00, 下月初 00:00)：
 * JPQL 里显式写 {@code >= from AND < to}，而不是派生查询的 Between。
 * 因为 Spring Data 的 Between 生成的是 SQL `BETWEEN ?1 AND ?2`，**两端都闭**，
 * 恰好落在"下月 1 日 00:00:00"的记录会被前后两个月各统计一次（明细重复、汇总虚高）。
 * 半开区间只含左端点，是统计类查询的标准写法，不会漏也不会重复。
 */
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    /** 查某人某岗位是否还有未签退的记录（防重复签到，取最近一条） */
    Optional<AttendanceRecord> findFirstByUserIdAndJobIdAndStatusOrderByCheckInTimeDesc(
            Long userId, Long jobId, Integer status);

    /** 某人某月 [from, to) 的全部记录（含未签退、异常），按签到时间倒序，用于"我的记录" */
    @Query("SELECT r FROM AttendanceRecord r "
            + "WHERE r.userId = :userId AND r.checkInTime >= :from AND r.checkInTime < :to "
            + "ORDER BY r.checkInTime DESC")
    List<AttendanceRecord> findUserMonthRecords(@Param("userId") Long userId,
                                                @Param("from") LocalDateTime from,
                                                @Param("to") LocalDateTime to);

    /** 某部门某月 [from, to)、指定状态的记录，用于部门汇总与按部门结算 */
    @Query("SELECT r FROM AttendanceRecord r "
            + "WHERE r.deptId = :deptId AND r.status = :status "
            + "AND r.checkInTime >= :from AND r.checkInTime < :to "
            + "ORDER BY r.checkInTime ASC")
    List<AttendanceRecord> findDeptMonthRecords(@Param("deptId") Long deptId,
                                                @Param("status") Integer status,
                                                @Param("from") LocalDateTime from,
                                                @Param("to") LocalDateTime to);

    /** 全校某月 [from, to)、指定状态的记录，用于全校汇总与全校结算 */
    @Query("SELECT r FROM AttendanceRecord r "
            + "WHERE r.status = :status "
            + "AND r.checkInTime >= :from AND r.checkInTime < :to "
            + "ORDER BY r.checkInTime ASC")
    List<AttendanceRecord> findMonthRecordsByStatus(@Param("status") Integer status,
                                                    @Param("from") LocalDateTime from,
                                                    @Param("to") LocalDateTime to);

    /**
     * 异常核实：只有仍处于「异常待确认（status=2）」时才允许改，返回影响行数。
     *
     * 为什么用条件更新而不是"读出来改字段再 save"：两个管理员同时点核实时，
     * 两边都读到 status=2，各自写一次，后写的会覆盖先写的结论（通过/不通过互相打架）。
     * 带 `AND status = 2` 的 UPDATE 是原子的，第二次影响 0 行，调用方据此提示"已被他人核实"，
     * 与发放接口用的是同一套思路，不需要悲观锁。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AttendanceRecord r SET r.status = :newStatus, r.remark = :remark, "
            + "r.confirmOperatorId = :operatorId, r.confirmTime = :confirmTime, r.updateTime = :confirmTime "
            + "WHERE r.id = :id AND r.status = 2")
    int markConfirmed(@Param("id") Long id,
                      @Param("newStatus") Integer newStatus,
                      @Param("remark") String remark,
                      @Param("operatorId") Long operatorId,
                      @Param("confirmTime") LocalDateTime confirmTime);
}
