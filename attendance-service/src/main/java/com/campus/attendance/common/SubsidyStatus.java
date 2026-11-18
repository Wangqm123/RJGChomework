package com.campus.attendance.common;

/**
 * 月度补贴发放单状态字典（与 schema.sql 的 t_subsidy.status 列对应）：
 * 0 = 待发放（已核算出金额，等助学中心打款）
 * 1 = 已发放（已打款，金额冻结，不再随补录工时的变化而变动）
 *
 * 为什么要把"核算"和"发放"分成两步：
 *   月度补贴是"先核算、后打款"的业务，核算结果需要留存凭证（谁、哪个月、多少钱、几条工时），
 *   只有生成发放单之后才能对账；直接实时算金额的话，补录一条工时就会让已打款的金额对不上。
 */
public final class SubsidyStatus {

    public static final int PENDING = 0;
    public static final int PAID = 1;

    private SubsidyStatus() {
    }

    /** 状态转中文名；null 表示该月尚未结算（还没有发放单） */
    public static String nameOf(Integer status) {
        if (status == null) {
            return "未结算";
        }
        return switch (status) {
            case PENDING -> "待发放";
            case PAID -> "已发放";
            default -> "未知状态";
        };
    }
}
