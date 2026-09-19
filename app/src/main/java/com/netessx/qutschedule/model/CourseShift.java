package com.netessx.qutschedule.model;

import com.netessx.qutschedule.util.Dates;
import com.netessx.qutschedule.util.TimeSlots;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * 一次性的临时调整：把某门课在某一天的这一次挪走或停掉。
 *
 * <p>和 {@link DayRule} 的区别：{@code DayRule} 是按天生效、不分课程的（放假、调休），
 * 这里精确到「某门课的某一次课」，而且完全不动原课程 —— 撤销只是删掉这条记录。
 *
 * <ul>
 *   <li>{@link #MOVE}：{@code fromDate} 这次不上，改到 {@code toDate} 上。</li>
 *   <li>{@link #CANCEL}：{@code fromDate} 这次不上。</li>
 * </ul>
 *
 * <p>节次默认沿用原课程；{@code startSlot > 0} 时才覆盖，用于「周三第 1 节挪到周五第 3 节」。
 */
public class CourseShift {

    public static final String MOVE = "MOVE";
    public static final String CANCEL = "CANCEL";

    public String id = UUID.randomUUID().toString();
    public String type = MOVE;
    /** 被调整的原课程 id。 */
    public String courseId = "";
    /** 被调整的那一次课的日期，yyyy-MM-dd。 */
    public String fromDate = "";
    /** 调整到的目标日期，yyyy-MM-dd；{@link #CANCEL} 时为空。 */
    public String toDate;
    /** 覆盖后的起始节次；0 表示沿用原课程。 */
    public int startSlot;
    /** 覆盖后的结束节次；0 表示沿用原课程。 */
    public int endSlot;

    public CourseShift() {
    }

    public CourseShift(String type, String courseId, String fromDate) {
        this.type = type;
        this.courseId = courseId;
        this.fromDate = fromDate;
    }

    public static CourseShift move(String courseId, String fromDate, String toDate) {
        CourseShift shift = new CourseShift(MOVE, courseId, fromDate);
        shift.toDate = toDate;
        return shift;
    }

    public static CourseShift cancel(String courseId, String fromDate) {
        return new CourseShift(CANCEL, courseId, fromDate);
    }

    public boolean isMove() {
        return MOVE.equals(type);
    }

    /** 日期或课程缺失、目标日无法解析的记录一律作废，避免留下一条永远算不出来的调整。 */
    public boolean isValid() {
        if (courseId == null || courseId.isEmpty() || Dates.parse(fromDate) == null) {
            return false;
        }
        return isMove() ? Dates.parse(toDate) != null : true;
    }

    /** 调整后这一节用的节次；没覆盖就沿用原课程的。 */
    public int startSlotOf(Course course) {
        return startSlot > 0 ? TimeSlots.clamp(startSlot) : course.startSlot;
    }

    public int endSlotOf(Course course) {
        int start = startSlotOf(course);
        if (endSlot <= 0) {
            return Math.max(start, course.endSlot);
        }
        return Math.max(start, TimeSlots.clamp(endSlot));
    }

    public void normalize() {
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }
        if (courseId == null) {
            courseId = "";
        }
        if (fromDate == null) {
            fromDate = "";
        }
        if (toDate == null) {
            toDate = "";
        }
        if (!CANCEL.equals(type)) {
            type = MOVE;
        }
        if (CANCEL.equals(type)) {
            toDate = "";
        }
    }

    public CourseShift copy() {
        CourseShift shift = new CourseShift(type, courseId, fromDate);
        shift.id = id;
        shift.toDate = toDate;
        shift.startSlot = startSlot;
        shift.endSlot = endSlot;
        return shift;
    }

    /** 命中「这门课在这一天」的那条调整；没有则返回 null。同一天有多条时以最后一条为准。 */
    public static CourseShift onDate(List<CourseShift> shifts, String courseId, String isoDate) {
        if (shifts == null || courseId == null || isoDate == null) {
            return null;
        }
        CourseShift found = null;
        for (CourseShift shift : shifts) {
            if (shift == null || !shift.isValid()) {
                continue;
            }
            if (courseId.equals(shift.courseId) && isoDate.equals(shift.fromDate)) {
                found = shift;
            }
        }
        return found;
    }

    public static CourseShift findById(List<CourseShift> shifts, String id) {
        if (shifts == null || id == null) {
            return null;
        }
        for (CourseShift shift : shifts) {
            if (shift != null && id.equals(shift.id)) {
                return shift;
            }
        }
        return null;
    }

    /** 挪到这一天的全部调整。 */
    public static List<CourseShift> intoDate(List<CourseShift> shifts, String isoDate) {
        List<CourseShift> out = new ArrayList<>();
        if (shifts == null || isoDate == null) {
            return out;
        }
        for (CourseShift shift : shifts) {
            if (shift != null && shift.isValid() && shift.isMove() && isoDate.equals(shift.toDate)) {
                out.add(shift);
            }
        }
        return out;
    }

    /**
     * 把调整套用到某一天已经算好的课程列表上：原地删掉被停掉 / 挪走的，追加挪进来的。
     *
     * <p>纯函数，{@code lookup} 负责按 id 找原课程（仓库传 {@code ScheduleStore::findCourse}，
     * 单测传一个 Map 就够）。原课程对象不会被改动，挪进来的是一份 {@link Course#copy()}，
     * 并带上 {@link Course#shiftId} 好让界面知道这是临时调整来的。
     */
    public static void apply(List<Course> courses, List<CourseShift> shifts, String isoDate,
                             Function<String, Course> lookup) {
        if (courses == null || shifts == null || shifts.isEmpty() || isoDate == null) {
            return;
        }
        courses.removeIf(course -> course != null && onDate(shifts, course.id, isoDate) != null);

        for (CourseShift shift : intoDate(shifts, isoDate)) {
            Course origin = lookup == null ? null : lookup.apply(shift.courseId);
            if (origin == null) {
                // 原课程已经被删了，这条调整没有意义，直接跳过
                continue;
            }
            Course moved = origin.copy();
            moved.startSlot = shift.startSlotOf(origin);
            moved.endSlot = shift.endSlotOf(origin);
            moved.shiftId = shift.id;
            courses.add(moved);
        }
    }
}
