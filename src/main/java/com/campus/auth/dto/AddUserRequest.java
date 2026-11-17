package com.campus.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 新增用户请求体：POST /api/auth/addUser
 *
 * 仅系统管理员（roleId=4）可调用，操作者 Token 放在请求头 token 中。
 */
public class AddUserRequest {

    @NotBlank(message = "账号不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "姓名不能为空")
    private String name;

    /** 学号（新增学生时填写） */
    private String studentNo;

    /** 所属部门 id（新增部门管理员时填写） */
    private Long deptId;

    @NotNull(message = "角色不能为空")
    @Min(value = 1, message = "角色编号应为 1-4")
    @Max(value = 4, message = "角色编号应为 1-4")
    private Integer roleId;

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
