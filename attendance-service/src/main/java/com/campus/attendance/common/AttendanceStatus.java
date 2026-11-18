package com.campus.attendance.common;

/**
 * 签到记录状态字典（与 schema.sql 的 status 列对应）：
 * 0 = 已签到未签退
 * 1 = 已签退（正常，参与补贴结算；异常记录核实通过后也落到这个状态）
 * 2 = 异常待确认（例如单次工时超过 12 小时、跨天未签退，需管理员核实后才计薪）
 * 3 = 核实不通过（管理员判定这段工时不成立，不计薪；记录保留用于追溯）
 */
public final class AttendanceStatus {

    public static final int CHECKED_IN = 0;
    public static final int CHECKED_OUT = 1;
    public static final int ABNORMAL = 2;
    public static final int REJECTED = 3;

    private AttendanceStatus() {
    }

    public static String nameOf(Integer status) {
        if (status == null) {
            return "未知状态";
        }
        return switch (status) {
            case CHECKED_IN -> "已签到未签退";
            case CHECKED_OUT -> "已签退";
            case ABNORMAL -> "异常待确认";
            case REJECTED -> "核实不通过";
            default -> "未知状态";
        };
    }
}
