package com.netessx.qutschedule.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** 日期工具：全应用统一使用 ISO 格式 yyyy-MM-dd 存储。 */
public final class Dates {

    public static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("M/d");
    public static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String[] WEEK_CN = {"一", "二", "三", "四", "五", "六", "日"};

    private Dates() {
    }

    public static LocalDate parse(String iso) {
        if (iso == null || iso.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(iso, YMD);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static String format(LocalDate date) {
        return date == null ? null : date.format(YMD);
    }

    /** 取所在周的周一。 */
    public static LocalDate mondayOf(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() - 1L);
    }

    /** 中文星期，返回「一」..「日」。 */
    public static String weekName(LocalDate date) {
        return WEEK_CN[date.getDayOfWeek().getValue() - 1];
    }

    public static boolean isWeekend(LocalDate date) {
        DayOfWeek d = date.getDayOfWeek();
        return d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY;
    }
}
