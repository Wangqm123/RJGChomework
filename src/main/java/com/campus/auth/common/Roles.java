package com.campus.auth.common;

/**
 * 角色编号约定（全组三个服务统一使用，不要各自发明）：
 * 1 = 学生
 * 2 = 用工部门管理员
 * 3 = 勤工助学中心管理员
 * 4 = 系统管理员
 */
public final class Roles {

    public static final int STUDENT = 1;
    public static final int DEPT_ADMIN = 2;
    public static final int CENTER_ADMIN = 3;
    public static final int SYSTEM_ADMIN = 4;

    private Roles() {
    }

    /** 角色编号转中文名（登录/查询时返回给前端展示） */
    public static String nameOf(int roleId) {
        return switch (roleId) {
            case STUDENT -> "学生";
            case DEPT_ADMIN -> "用工部门管理员";
            case CENTER_ADMIN -> "助学中心管理员";
            case SYSTEM_ADMIN -> "系统管理员";
            default -> "未知角色";
        };
    }
}
