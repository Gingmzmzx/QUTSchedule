package com.netessx.qutschedule.model;

import com.netessx.qutschedule.util.Dates;
import com.netessx.qutschedule.util.TimeSlots;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** 学期配置：开学日期决定「第几周」的推算基准。 */
public class TermConfig {

    public String termName = "";
    /** 第 1 周周一，格式 yyyy-MM-dd。 */
    public String startDate = "";
    public int totalWeeks = 20;
    public int defaultReminderMinutes = 15;
    public boolean reminderEnabled = true;
    public boolean liveUpdateEnabled = true;
    public String[] slotStarts = TimeSlots.DEFAULT_START.clone();
    public String[] slotEnds = TimeSlots.DEFAULT_END.clone();

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
        long days = ChronoUnit.DAYS.between(start, day);
        return (int) Math.floorDiv(days, 7L) + 1;
    }

    public LocalDate dateOf(int week, int dayOfWeek) {
        LocalDate start = startMonday();
        if (start == null) {
            return null;
        }
        return start.plusWeeks(week - 1L).plusDays(dayOfWeek - 1L);
    }

    public int slotStartMinutes(int slot) {
        return TimeSlots.minutes(slotStart(slot));
    }

    public int slotEndMinutes(int slot) {
        return TimeSlots.minutes(slotEnd(slot));
    }

    public String slotStart(int slot) {
        int band = TimeSlots.bandOf(slot);
        return safe(slotStarts, band, TimeSlots.DEFAULT_START[band]);
    }

    public String slotEnd(int slot) {
        int band = TimeSlots.bandOf(slot);
        return safe(slotEnds, band, TimeSlots.DEFAULT_END[band]);
    }

    private static String safe(String[] arr, int index, String fallback) {
        if (arr == null || index < 0 || index >= arr.length || arr[index] == null) {
            return fallback;
        }
        return arr[index];
    }

    public void normalize() {
        slotStarts = normalize(slotStarts, TimeSlots.DEFAULT_START);
        slotEnds = normalize(slotEnds, TimeSlots.DEFAULT_END);
        if (totalWeeks < 1 || totalWeeks > 40) {
            totalWeeks = 20;
        }
        if (defaultReminderMinutes < 0 || defaultReminderMinutes > 240) {
            defaultReminderMinutes = 15;
        }
    }

    private static String[] normalize(String[] arr, String[] fallback) {
        String[] out = fallback.clone();
        if (arr != null) {
            for (int i = 0; i < out.length && i < arr.length; i++) {
                if (arr[i] != null && TimeSlots.minutes(arr[i]) >= 0) {
                    out[i] = arr[i];
                }
            }
        }
        return out;
    }
}
