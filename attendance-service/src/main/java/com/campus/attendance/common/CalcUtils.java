package com.campus.attendance.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 工时与金额计算工具。
 *
 * 统一在这里算，避免各处四舍五入方式不一致导致"明细加总 ≠ 汇总"：
 * - 工时（小时）= 分钟 / 60，保留 1 位小数，仅用于展示；
 * - 金额（元）  = 分钟 × 时薪 / 60，保留 2 位小数，**用精确分钟计算**，
 *   不用"先四舍五入工时再乘时薪"，否则 10 分钟的零头会累计出偏差。
 */
public final class CalcUtils {

    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);

    private CalcUtils() {
    }

    /** 分钟 → 小时（保留 1 位小数，用于展示） */
    public static BigDecimal workHours(Integer minutes) {
        if (minutes == null || minutes <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(minutes).divide(SIXTY, 1, RoundingMode.HALF_UP);
    }

    /** 分钟 + 时薪 → 金额（保留 2 位小数） */
    public static BigDecimal amount(Integer minutes, BigDecimal hourSalary) {
        if (minutes == null || minutes <= 0 || hourSalary == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(minutes).multiply(hourSalary)
                .divide(SIXTY, 2, RoundingMode.HALF_UP);
    }

    /** 金额统一保留 2 位（累加后使用） */
    public static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
