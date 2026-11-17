package com.campus.auth.dto;

/**
 * 登录成功返回体（接口文档约定）：
 * data = { token, userId, roleId, role }
 */
public class LoginResponse {

    private String token;
    private Long userId;
    private Integer roleId;
    private String role;

    public LoginResponse(String token, Long userId, Integer roleId, String role) {
        this.token = token;
        this.userId = userId;
        this.roleId = roleId;
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getRoleId() {
        return roleId;
    }

    public void setRoleId(Integer roleId) {
        this.roleId = roleId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
