package com.netessx.qutschedule.model;

import java.util.UUID;

/**
 * 一条待办 / 活动 / 考试 / 作业。始终落在某一天，时间可留空。
 *
 * <p>与 {@link Course} 的区别：课程按周重复、挂在节次上；待办是单日的，可勾选完成。
 */
public class Todo {

    public static final String TODO = "TODO";
    public static final String ACTIVITY = "ACTIVITY";
    public static final String EXAM = "EXAM";
    public static final String HOMEWORK = "HOMEWORK";
    public static final String OTHER = "OTHER";

    public static final String[] ALL = {TODO, ACTIVITY, EXAM, HOMEWORK, OTHER};

    public String id = UUID.randomUUID().toString();
    public String title = "";
    public String type = TODO;
    /** 所属日期，yyyy-MM-dd。 */
    public String date = "";
    /** HH:mm，可空表示当天不限时。 */
    public String startTime;
    public String endTime;
    public boolean done;
    public String note = "";

    public boolean hasTime() {
        return startTime != null && !startTime.isEmpty();
    }

    public void normalize() {
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }
        if (title == null) {
            title = "";
        }
        if (type == null) {
            type = TODO;
        }
        if (date == null) {
            date = "";
        }
        if (note == null) {
            note = "";
        }
    }

    public Todo copy() {
        Todo t = new Todo();
        t.id = id;
        t.title = title;
        t.type = type;
        t.date = date;
        t.startTime = startTime;
        t.endTime = endTime;
        t.done = done;
        t.note = note;
        return t;
    }
}
