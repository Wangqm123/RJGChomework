package com.campus.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 用户实体（对应数据库表 t_user）
 *
 * 一个用户 = 账号信息 + 角色 + 所属部门。
 * 角色决定权限：学生只能看自己的岗位与工时，
 * 部门管理员管理本部门岗位，助学中心管理员全局监管。
 */
@Entity
@Table(name = "t_user")
public class User {

    /** 用户 id（主键，自增） */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录账号（唯一） */
    @Column(unique = true, nullable = false, length = 50)
    private String username;

    /** 登录密码（课程演示项目，明文存储；改进点：BCrypt 加密） */
    @Column(nullable = false, length = 100)
    private String password;

    /** 用户姓名 */
    @Column(nullable = false, length = 50)
    private String name;

    /** 学号（学生角色使用，其他角色可为空） */
    @Column(length = 30)
    private String studentNo;

    /** 所属部门 id（管理员角色使用；0 或空表示不隶属部门） */
    private Long deptId;

    /** 角色 id：1 学生 / 2 部门管理员 / 3 助学中心管理员 / 4 系统管理员 */
    @Column(nullable = false)
    private Integer roleId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStudentNo() {
        return studentNo;
    }

    public void setStudentNo(String studentNo) {
        this.studentNo = studentNo;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public Integer getRoleId() {
        return roleId;
    }

    public void setRoleId(Integer roleId) {
        this.roleId = roleId;
    }
}
