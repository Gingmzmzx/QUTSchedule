package com.netessx.qutschedule.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 一条日程。既可以是按周重复的课程，也可以是单次的社团 / 研究院活动。
 *
 * <p>按周重复：{@code dayOfWeek} 为 1..7，{@code weeks} 为生效周次；{@code date} 为空。
 * 单次日程：{@code date} 为 yyyy-MM-dd，{@code startTime}/{@code endTime} 为 HH:mm。
 * {@code dayOfWeek == 0} 表示尚未安排到具体时间（来自课表底部的「其他课程」）。
 */
public class Course {

    public String id = UUID.randomUUID().toString();
    public String name = "";
    public String teacher = "";
    public String location = "";
    public String campus = "";
    public String teachingClass = "";
    public int dayOfWeek;
    public int startSlot = 1;
    public int endSlot = 2;
    public String weekSpec = "";
    public List<Integer> weeks = new ArrayList<>();
    public String type = CourseType.COURSE;
    public String date;
    public String startTime;
    public String endTime;
    public String note = "";
    public boolean custom = false;
    public boolean reminderEnabled = true;
    /** -1 表示使用全局默认提前量。 */
    public int reminderLeadMinutes = -1;
    /** 所属学期 id，为空表示当前学期。 */
    public String semesterId;
    /** 结构化字段：学分，-1 表示未知。 */
    public double credit = -1;
    /** 结构化字段：考核方式。 */
    public String assessment = "";
    /** 结构化字段：是否实验课。 */
    public boolean lab;

    public boolean isOneOff() {
        return date != null && !date.isEmpty();
    }

    public boolean isUnscheduled() {
        return !isOneOff() && dayOfWeek < 1;
    }

    /** 该日程是否落在给定日期上。 */
    public boolean occursOn(LocalDate day, int weekNumber) {
        return occursOn(day, weekNumber, day.getDayOfWeek().getValue());
    }

    /**
     * 该日程是否落在给定日期上，星期由调用方给出。
     *
     * <p>调休日要传调整后的星期：周六补周三的课时 {@code effectiveWeekday = 3}。
     * 单次日程挂在具体日期上，不受调休影响。
     */
    public boolean occursOn(LocalDate day, int weekNumber, int effectiveWeekday) {
        if (isOneOff()) {
            return date.equals(day.toString());
        }
        if (dayOfWeek < 1 || effectiveWeekday != dayOfWeek) {
            return false;
        }
        if (weeks == null || weeks.isEmpty()) {
            return true;
        }
        return weeks.contains(weekNumber);
    }

    public int leadMinutes(int fallback) {
        return reminderLeadMinutes >= 0 ? reminderLeadMinutes : fallback;
    }

    public Course copy() {
        Course c = new Course();
        c.id = id;
        c.name = name;
        c.teacher = teacher;
        c.location = location;
        c.campus = campus;
        c.teachingClass = teachingClass;
        c.dayOfWeek = dayOfWeek;
        c.startSlot = startSlot;
        c.endSlot = endSlot;
        c.weekSpec = weekSpec;
        c.weeks = weeks == null ? new ArrayList<>() : new ArrayList<>(weeks);
        c.type = type;
        c.date = date;
        c.startTime = startTime;
        c.endTime = endTime;
        c.note = note;
        c.custom = custom;
        c.reminderEnabled = reminderEnabled;
        c.reminderLeadMinutes = reminderLeadMinutes;
        c.semesterId = semesterId;
        c.credit = credit;
        c.assessment = assessment;
        c.lab = lab;
        return c;
    }
}
