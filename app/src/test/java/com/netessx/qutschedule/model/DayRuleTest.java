package com.netessx.qutschedule.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 用户自建的放假 / 调休规则解析。 */
public class DayRuleTest {

    private static List<DayRule> rules(DayRule... items) {
        List<DayRule> list = new ArrayList<>(Arrays.asList(items));
        for (DayRule rule : list) {
            rule.normalize();
        }
        return list;
    }

    @Test
    public void offRuleMeansNoClass() {
        List<DayRule> list = rules(new DayRule(DayRule.OFF, "2026-10-01"));
        assertTrue(DayRule.isOff(list, "2026-10-01"));
        assertFalse(DayRule.isOff(list, "2026-10-02"));
        assertEquals(0, DayRule.effectiveWeekday(list, "2026-10-01"));
    }

    @Test
    public void offRuleCoversAWholeRange() {
        List<DayRule> list = rules(new DayRule(DayRule.OFF, "2026-10-01", "2026-10-07"));
        assertTrue(DayRule.isOff(list, "2026-10-01"));
        assertTrue(DayRule.isOff(list, "2026-10-04"));
        assertTrue(DayRule.isOff(list, "2026-10-07"));
        assertFalse(DayRule.isOff(list, "2026-09-30"));
        assertFalse(DayRule.isOff(list, "2026-10-08"));
        assertTrue(list.get(0).isRange());
    }

    @Test
    public void reversedRangeFallsBackToASingleDay() {
        DayRule rule = new DayRule(DayRule.OFF, "2026-10-07", "2026-10-01");
        rule.normalize();
        assertFalse(rule.isRange());
        assertTrue(rule.isOffOn("2026-10-07"));
        assertFalse(rule.isOffOn("2026-10-01"));
    }

    @Test
    public void aHolidayReferenceStillProvidesItsCourses() {
        // 10 月 6 日放假，但 9 月 20 日补的是「10 月 6 日本该上的课」，
        // 所以调休规则不能因为参照日放假就失效
        List<DayRule> list = rules(
                new DayRule(DayRule.OFF, "2026-10-01", "2026-10-07"),
                DayRule.swap("2026-09-20", "2026-10-06"));
        assertTrue(DayRule.isOff(list, "2026-10-06"));
        assertEquals("2026-10-06", DayRule.swapRuleFor(list, "2026-09-20").refDate);
    }

    @Test
    public void swapRuleFollowsTheReferenceDay() {
        // 2026-10-11 是周日，参照 2026-10-08（周四）
        List<DayRule> list = rules(DayRule.swap("2026-10-11", "2026-10-08"));
        assertEquals(4, DayRule.effectiveWeekday(list, "2026-10-11"));
        assertEquals(0, DayRule.effectiveWeekday(list, "2026-10-12"));
        assertFalse(DayRule.isOff(list, "2026-10-11"));
    }

    @Test
    public void laterRuleWinsWhenTheSameDayIsAddedTwice() {
        List<DayRule> list = new ArrayList<>();
        list.add(DayRule.swap("2026-10-11", "2026-10-08"));
        list.add(DayRule.swap("2026-10-11", "2026-10-09"));
        assertEquals(5, DayRule.effectiveWeekday(list, "2026-10-11"));
    }

    @Test
    public void swapWithoutAReferenceDegradesToOff() {
        DayRule broken = DayRule.swap("2026-10-11", "");
        broken.normalize();
        assertEquals(DayRule.OFF, broken.type);
        assertFalse(broken.isSwap());
    }

    @Test
    public void swapRuleForPicksTheReferenceDay() {
        List<DayRule> list = rules(DayRule.swap("2026-09-20", "2026-10-06"));
        DayRule rule = DayRule.swapRuleFor(list, "2026-09-20");
        assertEquals("2026-10-06", rule.refDate);
        assertEquals(null, DayRule.swapRuleFor(list, "2026-09-21"));
    }

    @Test
    public void swapRuleForPrefersTheLaterRule() {
        List<DayRule> list = new ArrayList<>();
        list.add(DayRule.swap("2026-09-20", "2026-10-06"));
        list.add(DayRule.swap("2026-09-20", "2026-10-07"));
        assertEquals("2026-10-07", DayRule.swapRuleFor(list, "2026-09-20").refDate);
    }

    @Test
    public void offRulesAreNotSwapRules() {
        List<DayRule> list = rules(new DayRule(DayRule.OFF, "2026-09-20"));
        assertEquals(null, DayRule.swapRuleFor(list, "2026-09-20"));
    }

    @Test
    public void emptyRuleListIsSafe() {
        assertFalse(DayRule.isOff(null, "2026-10-01"));
        assertEquals(0, DayRule.effectiveWeekday(Collections.emptyList(), "2026-10-01"));
    }
}
