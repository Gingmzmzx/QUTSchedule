package com.netessx.qutschedule.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;

/** 调休日按调整后的星期匹配课程。 */
public class CourseOccursTest {

    /** 周三 1-2 节的课，第 4-8 周。 */
    private static Course wednesdayCourse() {
        Course course = new Course();
        course.dayOfWeek = 3;
        course.startSlot = 1;
        course.endSlot = 2;
        course.weeks = Arrays.asList(4, 5, 6, 7, 8);
        return course;
    }

    @Test
    public void matchesOnItsOwnWeekday() {
        LocalDate wednesday = LocalDate.of(2026, 9, 30);
        assertTrue(wednesdayCourse().occursOn(wednesday, 5));
    }

    @Test
    public void doesNotMatchOnAPlainSaturday() {
        LocalDate saturday = LocalDate.of(2026, 10, 3);
        assertFalse(wednesdayCourse().occursOn(saturday, 5));
    }

    @Test
    public void matchesOnASaturdaySwappedToWednesday() {
        LocalDate saturday = LocalDate.of(2026, 10, 3);
        assertTrue(wednesdayCourse().occursOn(saturday, 5, 3));
    }

    @Test
    public void weekRangeStillAppliesOnMakeupDays() {
        LocalDate saturday = LocalDate.of(2026, 10, 3);
        assertFalse(wednesdayCourse().occursOn(saturday, 2, 3));
    }

    @Test
    public void oneOffEventsIgnoreTheSwap() {
        Course exam = new Course();
        exam.date = "2026-10-03";
        exam.dayOfWeek = 6;
        assertTrue(exam.occursOn(LocalDate.of(2026, 10, 3), 5, 3));
        assertFalse(exam.occursOn(LocalDate.of(2026, 10, 10), 5, 3));
    }
}
