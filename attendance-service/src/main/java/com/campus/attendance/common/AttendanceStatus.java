package com.campus.attendance.common;

/**
 * 签到记录状态字典（与 schema.sql 的 status 列对应）：
 * 0 = 已签到未签退
 * 1 = 已签退（正常，参与补贴结算）
 * 2 = 异常待确认（例如单次工时超过 12 小时、跨天未签退，需管理员核实后才计薪）
 */
public final class AttendanceStatus {

    public static final int CHECKED_IN = 0;
    public static final int CHECKED_OUT = 1;
    public static final int ABNORMAL = 2;

    private AttendanceStatus() {
    }

    public static String nameOf(int status) {
        return switch (status) {
            case CHECKED_IN -> "已签到未签退";
            case CHECKED_OUT -> "已签退";
            case ABNORMAL -> "异常待确认";
            default -> "未知状态";
        };
    }
}
