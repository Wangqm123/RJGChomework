package com.campus.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 用户权限服务启动类
 *
 * 服务职责（见需求文档 3.1）：
 * 1. 用户登录：账号密码登录，颁发身份凭证 Token
 * 2. 身份校验：对外提供 checkToken 接口，供其他两个服务校验身份
 * 3. 用户管理：系统管理员新增用户、分配角色
 * 4. 权限拦截：判断当前用户是否有权访问对应功能
 */
@SpringBootApplication
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
