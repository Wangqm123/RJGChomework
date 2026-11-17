package com.campus.auth.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 通用配置：密码加密器。
 *
 * BCrypt 的特点（真实系统标准做法）：
 * - 加盐哈希：同一个密码每次加密结果都不同，数据库泄露也无法反查明文；
 * - 校验用 matches(明文, 哈希)，而不是字符串比较；
 * - 自带慢哈希特性，暴力破解成本高。
 */
@Configuration
public class AuthConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
