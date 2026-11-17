package com.campus.auth.repository;

import com.campus.auth.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

/** 角色表数据访问接口 */
public interface RoleRepository extends JpaRepository<Role, Integer> {
}
