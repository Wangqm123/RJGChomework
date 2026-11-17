package com.campus.auth.repository;

import com.campus.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 用户表数据访问层。
 * 继承 JpaRepository 即自动获得增删改查能力，无需写 SQL。
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /** 按登录账号查询（登录时使用） */
    Optional<User> findByUsername(String username);

    /** 账号是否已存在（新增用户前查重） */
    boolean existsByUsername(String username);
}
