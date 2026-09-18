package com.netessx.qutschedule.model;

import com.netessx.qutschedule.util.Dates;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** 一个学期：开学日期决定「第几周」的推算基准，可同时保存多个。 */
public class Semester {

    public static final int NOT_STARTED = 0;
    public static final int RUNNING = 1;
    public static final int FINISHED = 2;

    public String id = UUID.randomUUID().toString();
    public String name = "";
    /** 第 1 周周一，格式 yyyy-MM-dd。 */
    public String startDate = "";
    public int totalWeeks = 20;
    /** 关联的作息方案 id，null 表示用默认方案。 */
    public String schemeId;

    public LocalDate startMonday() {
        return Dates.parse(startDate);
    }

    public boolean isConfigured() {
        return startMonday() != null;
    }

    /** 第 1 周为 1；开学前返回小于 1 的值。 */
    public int weekOf(LocalDate day) {
        LocalDate start = startMonday();
        if (start == null) {
            return 0;
        }
        return (int) Math.floorDiv(ChronoUnit.DAYS.between(start, day), 7L) + 1;
    }

    public LocalDate dateOf(int week, int dayOfWeek) {
        LocalDate start = startMonday();
        if (start == null) {
            return null;
        }
        return start.plusWeeks(week - 1L).plusDays(dayOfWeek - 1L);
    }

    public LocalDate endDate() {
        LocalDate start = startMonday();
        return start == null ? null : start.plusWeeks(totalWeeks).minusDays(1);
    }

    /** 未开始 / 进行中 / 已完成。 */
    public int status(LocalDate today) {
        LocalDate start = startMonday();
        if (start == null) {
            return NOT_STARTED;
        }
        if (today.isBefore(start)) {
            return NOT_STARTED;
        }
        LocalDate end = endDate();
        if (end != null && today.isAfter(end)) {
            return FINISHED;
        }
        return RUNNING;
    }

    /** 学期进度百分比，未配置或未开学时为 0。 */
    public int progressPercent(LocalDate today) {
        LocalDate start = startMonday();
        if (start == null || today.isBefore(start)) {
            return 0;
        }
        long total = Math.max(1, ChronoUnit.DAYS.between(start, start.plusWeeks(totalWeeks)));
        long passed = Math.min(total, ChronoUnit.DAYS.between(start, today));
        return (int) (passed * 100 / total);
    }

    public void normalize() {
        if (totalWeeks < 1 || totalWeeks > 40) {
            totalWeeks = 20;
        }
        if (name == null) {
            name = "";
        }
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }
    }

    public Semester copy() {
        Semester s = new Semester();
        s.id = id;
        s.name = name;
        s.startDate = startDate;
        s.totalWeeks = totalWeeks;
        s.schemeId = schemeId;
        return s;
    }
}
