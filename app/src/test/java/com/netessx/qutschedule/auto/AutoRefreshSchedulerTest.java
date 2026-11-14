package com.netessx.qutschedule.auto;

import static org.junit.Assert.assertEquals;

import com.netessx.qutschedule.model.Prefs;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** {@link AutoRefreshScheduler#nextTrigger} 的边界：每天/每周、当天未到点与刚好到点。 */
public class AutoRefreshSchedulerTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static long at(String text) {
        return LocalDateTime.parse(text).atZone(ZONE).toInstant().toEpochMilli();
    }

    private static Prefs prefs(String mode, int hour, int minute, int weekday) {
        Prefs prefs = new Prefs();
        prefs.autoRefreshMode = mode;
        prefs.autoRefreshHour = hour;
        prefs.autoRefreshMinute = minute;
        prefs.autoRefreshWeekday = weekday;
        return prefs;
    }

    @Test
    public void dailyUsesTodayWhenNotYetDue() {
        Prefs prefs = prefs(Prefs.REFRESH_DAILY, 8, 0, 1);
        assertEquals(at("2026-11-13T08:00"), AutoRefreshScheduler.nextTrigger(prefs, at("2026-11-13T07:00"), ZONE));
    }

    @Test
    public void dailyRollsToTomorrowWhenExactlyDue() {
        Prefs prefs = prefs(Prefs.REFRESH_DAILY, 8, 0, 1);
        assertEquals(at("2026-11-14T08:00"), AutoRefreshScheduler.nextTrigger(prefs, at("2026-11-13T08:00"), ZONE));
    }

    @Test
    public void dailyRollsToTomorrowWhenPast() {
        Prefs prefs = prefs(Prefs.REFRESH_DAILY, 23, 30, 1);
        assertEquals(at("2026-11-14T23:30"), AutoRefreshScheduler.nextTrigger(prefs, at("2026-11-13T23:45"), ZONE));
    }

    @Test
    public void weeklyPicksNextMondayFromFriday() {
        Prefs prefs = prefs(Prefs.REFRESH_WEEKLY, 8, 0, 1);
        assertEquals(at("2026-11-16T08:00"), AutoRefreshScheduler.nextTrigger(prefs, at("2026-11-13T09:00"), ZONE));
    }

    @Test
    public void weeklyUsesTodayWhenMondayBeforeTime() {
        Prefs prefs = prefs(Prefs.REFRESH_WEEKLY, 8, 0, 1);
        assertEquals(at("2026-11-16T08:00"), AutoRefreshScheduler.nextTrigger(prefs, at("2026-11-16T07:00"), ZONE));
    }

    @Test
    public void weeklySkipsToNextWeekWhenMondayPastTime() {
        Prefs prefs = prefs(Prefs.REFRESH_WEEKLY, 8, 0, 1);
        assertEquals(at("2026-11-23T08:00"), AutoRefreshScheduler.nextTrigger(prefs, at("2026-11-16T08:00"), ZONE));
    }

    @Test
    public void weeklySundayFromFriday() {
        Prefs prefs = prefs(Prefs.REFRESH_WEEKLY, 20, 0, 7);
        assertEquals(at("2026-11-15T20:00"), AutoRefreshScheduler.nextTrigger(prefs, at("2026-11-13T21:00"), ZONE));
    }
}
