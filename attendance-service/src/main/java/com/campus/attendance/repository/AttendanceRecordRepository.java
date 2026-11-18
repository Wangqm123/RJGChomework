package com.campus.attendance.repository;

import com.campus.attendance.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 签到记录数据访问。
 *
 * 只写方法名让 Spring Data 自动生成 SQL（派生查询），不手写 JPQL，减少出错。
 * 月份过滤统一用 [月初 00:00, 下月初 00:00) 半开区间，避免月末 23:59:59 漏数据。
 */
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    /** 查某人某岗位是否还有未签退的记录（防重复签到，取最近一条） */
    Optional<AttendanceRecord> findFirstByUserIdAndJobIdAndStatusOrderByCheckInTimeDesc(
            Long userId, Long jobId, Integer status);

    /** 某人某月的全部记录（含未签退、异常），按签到时间倒序，用于"我的记录" */
    List<AttendanceRecord> findByUserIdAndCheckInTimeBetweenOrderByCheckInTimeDesc(
            Long userId, LocalDateTime from, LocalDateTime to);

    /** 某部门某月、指定状态的记录，用于部门汇总（只统计 status=1 已签退的） */
    List<AttendanceRecord> findByDeptIdAndStatusAndCheckInTimeBetweenOrderByCheckInTimeAsc(
            Long deptId, Integer status, LocalDateTime from, LocalDateTime to);

    /** 全校某月、指定状态的记录，用于全校汇总 */
    List<AttendanceRecord> findByStatusAndCheckInTimeBetweenOrderByCheckInTimeAsc(
            Integer status, LocalDateTime from, LocalDateTime to);
}
