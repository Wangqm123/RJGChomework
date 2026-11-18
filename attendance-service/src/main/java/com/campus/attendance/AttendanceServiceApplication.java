package com.campus.attendance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 工时考勤与补贴核算服务启动类（端口 8083）。
 *
 * 三大服务之一：学生签到/签退 → 工时统计 → 月度工时补贴核算 → 部门/全校汇总。
 * 不保存用户表、不保存岗位表，只存 user_id / job_id 这类逻辑外键，
 * 身份与录用资格全部通过 HTTP 调权限服务、岗位服务校验（分布式服务解耦）。
 */
@SpringBootApplication
public class AttendanceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AttendanceServiceApplication.class, args);
    }
}
