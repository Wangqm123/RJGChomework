package com.campus.job.common;

/**
 * 申请状态常量（对应需求文档 3.2「申请审核」）：
 *
 *   待审核(0) → 已录用(1) / 已拒绝(2)
 *
 * 一个学生对同一岗位只允许有一条申请记录，状态只能从"待审核"流转一次，
 * 避免部门管理员重复审核导致数据错乱。
 */
public final class ApplyStatus {

    /** 待审核（学生刚投递） */
    public static final int PENDING = 0;
    /** 已录用（工时服务据此判断学生能否打卡） */
    public static final int HIRED = 1;
    /** 已拒绝 */
    public static final int REFUSED = 2;

    private ApplyStatus() {
    }

    /** 状态码转中文名（返回给前端展示） */
    public static String nameOf(Integer status) {
        if (status == null) {
            return "未知状态";
        }
        return switch (status) {
            case PENDING -> "待审核";
            case HIRED -> "已录用";
            case REFUSED -> "已拒绝";
            default -> "未知状态";
        };
    }
}
