package com.netessx.qutschedule.model;

import android.content.Context;

import com.netessx.qutschedule.R;

/** 日程类型。 */
public final class CourseType {

    public static final String COURSE = "COURSE";
    public static final String CLUB = "CLUB";
    public static final String RESEARCH = "RESEARCH";
    public static final String EXAM = "EXAM";
    public static final String OTHER = "OTHER";

    public static final String[] ALL = {COURSE, CLUB, RESEARCH, EXAM, OTHER};

    private CourseType() {
    }

    public static String label(Context ctx, String type) {
        if (type == null) {
            return ctx.getString(R.string.type_course);
        }
        switch (type) {
            case CLUB:
                return ctx.getString(R.string.type_club);
            case RESEARCH:
                return ctx.getString(R.string.type_research);
            case EXAM:
                return ctx.getString(R.string.type_exam);
            case OTHER:
                return ctx.getString(R.string.type_other);
            default:
                return ctx.getString(R.string.type_course);
        }
    }
}
