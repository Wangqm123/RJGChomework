package com.campus.attendance.common;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/**
 * 月份参数解析：把 "2026-11" 解析成 [月初 00:00, 下月初 00:00) 的半开区间。
 *
 * 为什么用半开区间而不是 BETWEEN '2026-11-01' AND '2026-11-30 23:59:59'：
 * 后者会漏掉 23:59:59 之后、次日 00:00 之前的记录（数据库时间精度可能是微秒），
 * 半开区间是统计类查询的标准写法，不会漏也不会重复。
 */
public record MonthRange(String month, LocalDateTime from, LocalDateTime to) {

    /** 月份格式错误时抛 400；不传则默认当月 */
    public static MonthRange of(String month) {
        String value = (month == null || month.isBlank()) ? YearMonth.now().toString() : month.trim();
        YearMonth ym;
        try {
            ym = YearMonth.parse(value);
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, "月份格式应为 yyyy-MM，例如 2026-11");
        }
        return new MonthRange(
                ym.toString(),
                ym.atDay(1).atStartOfDay(),
                ym.plusMonths(1).atDay(1).atStartOfDay());
    }
}
