package com.netessx.qutschedule.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 临时调课 / 停课：套用到某一天的课程列表上的行为。 */
public class CourseShiftTest {

    private static final String WEDNESDAY = "2026-09-30";
    private static final String FRIDAY = "2026-10-02";

    private static Course wednesdayCourse() {
        Course course = new Course();
        course.id = "c1";
        course.name = "数据结构";
        course.dayOfWeek = 3;
        course.startSlot = 1;
        course.endSlot = 2;
        course.weeks = Arrays.asList(4, 5, 6);
        return course;
    }

    private static Map<String, Course> lookup(Course... courses) {
        Map<String, Course> map = new HashMap<>();
        for (Course course : courses) {
            map.put(course.id, course);
        }
        return map;
    }

    private static List<Course> onWednesday(Course course, CourseShift... shifts) {
        List<Course> courses = new ArrayList<>();
        courses.add(course);
        CourseShift.apply(courses, Arrays.asList(shifts), WEDNESDAY, lookup(course)::get);
        return courses;
    }

    @Test
    public void cancelRemovesTheOccurrence() {
        Course course = wednesdayCourse();
        List<Course> courses = onWednesday(course, CourseShift.cancel("c1", WEDNESDAY));
        assertTrue(courses.isEmpty());
    }

    @Test
    public void moveTakesItOffTheSourceDate() {
        Course course = wednesdayCourse();
        List<Course> courses = onWednesday(course, CourseShift.move("c1", WEDNESDAY, FRIDAY));
        assertTrue(courses.isEmpty());
    }

    @Test
    public void movePutsItOnTheTargetDateWithOriginalSlots() {
        Course course = wednesdayCourse();
        CourseShift shift = CourseShift.move("c1", WEDNESDAY, FRIDAY);
        List<Course> courses = new ArrayList<>();
        CourseShift.apply(courses, Arrays.asList(shift), FRIDAY, lookup(course)::get);

        assertEquals(1, courses.size());
        assertEquals("数据结构", courses.get(0).name);
        assertEquals(1, courses.get(0).startSlot);
        assertEquals(2, courses.get(0).endSlot);
        assertEquals(shift.id, courses.get(0).shiftId);
        // 原课程没有被改动
        assertEquals(3, course.dayOfWeek);
        assertNull(course.shiftId);
    }

    @Test
    public void slotOverrideIsApplied() {
        Course course = wednesdayCourse();
        CourseShift shift = CourseShift.move("c1", WEDNESDAY, FRIDAY);
        shift.startSlot = 5;
        shift.endSlot = 4;
        List<Course> courses = new ArrayList<>();
        CourseShift.apply(courses, Arrays.asList(shift), FRIDAY, lookup(course)::get);

        assertEquals(1, courses.size());
        assertEquals(5, courses.get(0).startSlot);
        // 结束节次不能早于起始节次
        assertEquals(5, courses.get(0).endSlot);
    }

    @Test
    public void shiftsOnOtherDatesAreIgnored() {
        Course course = wednesdayCourse();
        List<Course> courses = onWednesday(course, CourseShift.move("c1", "2026-09-23", FRIDAY));
        assertEquals(1, courses.size());
        assertNull(courses.get(0).shiftId);
    }

    @Test
    public void brokenShiftIsDropped() {
        CourseShift broken = CourseShift.move("c1", "不是日期", FRIDAY);
        assertFalse(broken.isValid());
        List<Course> courses = onWednesday(wednesdayCourse(), broken);
        assertEquals(1, courses.size());
    }

    @Test
    public void missingCourseIsSkippedInsteadOfCrashing() {
        CourseShift shift = CourseShift.move("c1", WEDNESDAY, FRIDAY);
        List<Course> courses = new ArrayList<>();
        CourseShift.apply(courses, Arrays.asList(shift), FRIDAY, id -> null);
        assertTrue(courses.isEmpty());
    }

    @Test
    public void lookupFindsTheShiftOnItsOwnDate() {
        CourseShift shift = CourseShift.cancel("c1", WEDNESDAY);
        List<CourseShift> shifts = Arrays.asList(shift);
        assertNotNull(CourseShift.onDate(shifts, "c1", WEDNESDAY));
        assertNull(CourseShift.onDate(shifts, "c1", FRIDAY));
        assertNull(CourseShift.onDate(shifts, "c2", WEDNESDAY));
    }

    @Test
    public void cancelDropsTheMoveTarget() {
        CourseShift shift = CourseShift.cancel("c1", WEDNESDAY);
        shift.toDate = FRIDAY;
        shift.normalize();
        assertEquals("", shift.toDate);
        assertTrue(CourseShift.intoDate(Arrays.asList(shift), FRIDAY).isEmpty());
    }
}
