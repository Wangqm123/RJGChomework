package com.campus.job.common;

/**
 * 岗位状态常量 + 状态流转规则（对应需求文档 3.2「岗位状态流转」）：
 *
 *   草稿(0) → 待中心审核(1) → 已上架(2) / 已驳回(3) → 已招满(4) → 已下架(5)
 *
 * 说明：
 * - 本系统的岗位一经发布即进入"待中心审核"，因此 0 草稿 只作为状态字典保留；
 * - 已驳回 的岗位由部门管理员修改后重新回到 待中心审核；
 * - 已上架 的岗位在录用人数达到需求人数时自动变为 已招满；
 * - 已上架 / 已招满 的岗位可以被部门管理员下架。
 */
public final class JobStatus {

    /** 草稿（保留状态，当前流程未使用） */
    public static final int DRAFT = 0;
    /** 待助学中心审核 */
    public static final int PENDING_AUDIT = 1;
    /** 已上架（审核通过，学生可浏览、投递） */
    public static final int LISTED = 2;
    /** 已驳回（中心审核不通过，带驳回理由） */
    public static final int REJECTED = 3;
    /** 已招满（录用人数达到需求人数） */
    public static final int FULL = 4;
    /** 已下架（部门管理员主动下架） */
    public static final int OFFLINE = 5;

    private JobStatus() {
    }

    /** 状态码转中文名（返回给前端展示） */
    public static String nameOf(Integer status) {
        if (status == null) {
            return "未知状态";
        }
        return switch (status) {
            case DRAFT -> "草稿";
            case PENDING_AUDIT -> "待中心审核";
            case LISTED -> "已上架";
            case REJECTED -> "已驳回";
            case FULL -> "已招满";
            case OFFLINE -> "已下架";
            default -> "未知状态";
        };
    }
}
