package com.campus.job;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 岗位申请与审核服务启动类（全组端口约定：auth=8081 / job=8082 / attendance=8083）。
 *
 * 本服务是三大分布式服务之二，只负责"岗位"相关的业务：
 * 岗位发布 → 助学中心审核 → 学生投递 → 部门管理员审核录用/拒绝 → 状态流转。
 * 用户身份不落库，统一通过 HTTP 调用权限服务的 checkToken 校验。
 */
@SpringBootApplication
public class JobServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobServiceApplication.class, args);
    }
}
