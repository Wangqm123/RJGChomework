package com.campus.auth.dto;

/**
 * Token 校验返回体（接口文档约定）：
 * data = { valid, userId, roleId, role }
 *
 * 岗位审核服务、工时核算服务在处理请求前，
 * 先调用 /api/auth/checkToken 完成身份校验。
 */
public class CheckTokenResponse {

    /** Token 是否有效 */
    private boolean valid;
    private Long userId;
    private Integer roleId;
    private String role;

    /** 校验成功 */
    public static CheckTokenResponse of(Long userId, Integer roleId, String role) {
        CheckTokenResponse r = new CheckTokenResponse();
        r.valid = true;
        r.userId = userId;
        r.roleId = roleId;
        r.role = role;
        return r;
    }

    /** 校验失败（无效/过期） */
    public static CheckTokenResponse invalid() {
        CheckTokenResponse r = new CheckTokenResponse();
        r.valid = false;
        return r;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
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
