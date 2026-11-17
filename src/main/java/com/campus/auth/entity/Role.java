package com.campus.auth.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 角色表（t_role）：系统 4 种角色的字典表。
 * 用户表通过 role_id 引用本表，新增用户时先校验角色是否合法。
 */
@Entity
@Table(name = "t_role")
public class Role {

    /** 角色编号：1 学生 / 2 部门管理员 / 3 助学中心管理员 / 4 系统管理员 */
    @Id
    private Integer id;

    /** 角色名称 */
    private String roleName;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }
}
