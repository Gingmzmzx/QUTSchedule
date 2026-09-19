package com.netessx.qutschedule.data;

import android.content.Context;

import com.netessx.qutschedule.model.Course;
import com.netessx.qutschedule.model.CourseShift;
import com.netessx.qutschedule.model.DayRule;
import com.netessx.qutschedule.model.Semester;
import com.netessx.qutschedule.model.TimeScheme;
import com.netessx.qutschedule.model.Todo;
import com.netessx.qutschedule.util.RoomFloor;
import com.netessx.qutschedule.util.TimeSlots;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 课表查询：把「第几周」、作息方案与课程 / 待办匹配起来。 */
public final class ScheduleRepository {

    private ScheduleRepository() {
    }

    public static Semester currentSemester(Context ctx) {
        return ScheduleStore.get(ctx).currentSemester();
    }

    public static TimeScheme schemeFor(Context ctx, LocalDate date) {
        return ScheduleStore.get(ctx).schemeFor(date);
    }

    /** 没配置开学日期时返回 0。 */
    public static int weekOf(Context ctx, LocalDate day) {
        return currentSemester(ctx).weekOf(day);
    }

    public static List<Course> coursesOn(Context ctx, LocalDate day) {
        return coursesOn(ctx, day, weekOf(ctx, day));
    }

    private static final String[] WEEK_NAMES = {"一", "二", "三", "四", "五", "六", "日"};

    public static List<Course> coursesOn(Context ctx, LocalDate day, int weekNumber) {
        // 临时调整只能在 resolve 之外套：调休分支会提前递归返回，套在里面会漏掉调休日
        List<Course> out = resolve(ctx, day, weekNumber, 0);
        applyShifts(ctx, day, out);
        return out;
    }

    /** 应用这一天的临时调课 / 停课；挪进来的课要重新排一次序。 */
    private static void applyShifts(Context ctx, LocalDate day, List<Course> out) {
        ScheduleStore store = ScheduleStore.get(ctx);
        List<CourseShift> shifts = store.shifts();
        if (shifts.isEmpty()) {
            return;
        }
        int before = out.size();
        CourseShift.apply(out, shifts, day.toString(), store::findCourse);
        if (out.size() != before) {
            sort(out, ctx);
        }
    }

    /** 调休链最长跟这么多层，避免用户把 A 指向 B、B 又指回 A 时递归不停。 */
    private static final int MAX_SWAP_DEPTH = 4;

    private static List<Course> resolve(Context ctx, LocalDate day, int weekNumber, int depth) {
        // 只有直接查询某一天时才看放假。沿调休跟到参照日时，要的是那天「本该有的课」，
        // 否则把 10 月 6 日设成放假日，9 月 20 日补的课也会跟着消失。
        if (depth == 0 && isHoliday(ctx, day)) {
            return new ArrayList<>();
        }
        // 调休日直接照搬参照日那天的课表：用户说的是「9月20上10月6号的课」，
        // 而不是「9月20按星期二的规律排课」，后者还要受那门课自己的周次限制。
        DayRule swap = DayRule.swapRuleFor(ScheduleStore.get(ctx).data().dayRules, day.toString());
        if (swap != null && depth < MAX_SWAP_DEPTH) {
            LocalDate reference = swap.referenceDate();
            if (reference != null) {
                // 周次必须按参照日自己算：照搬的是「那天实际会上什么课」
                return resolve(ctx, reference, currentSemester(ctx).weekOf(reference), depth + 1);
            }
        }

        String semesterId = currentSemester(ctx).id;
        List<Course> out = new ArrayList<>();
        for (Course course : ScheduleStore.get(ctx).coursesOf(semesterId)) {
            if (!course.isUnscheduled() && course.occursOn(day, weekNumber)) {
                out.add(course);
            }
        }
        sort(out, ctx);
        return out;
    }

    /** 这一天实际按周几的课表上课；用户设了调休就返回调整后的星期。 */
    public static int effectiveWeekday(Context ctx, LocalDate day) {
        int swapped = DayRule.effectiveWeekday(ScheduleStore.get(ctx).data().dayRules, day.toString());
        return swapped == 0 ? day.getDayOfWeek().getValue() : swapped;
    }

    /** 这一天是否被用户设为放假（无课）。 */
    public static boolean isHoliday(Context ctx, LocalDate day) {
        return DayRule.isOff(ScheduleStore.get(ctx).data().dayRules, day.toString());
    }

    /** 当天状态说明：放假 / 调休；普通日子返回 null。 */
    public static String dayNote(Context ctx, LocalDate day) {
        if (isHoliday(ctx, day)) {
            return "放假，无课";
        }
        DayRule swap = DayRule.swapRuleFor(ScheduleStore.get(ctx).data().dayRules, day.toString());
        if (swap != null) {
            return "调休 · 上 " + swap.refDate + " 的课";
        }
        return null;
    }

    /** 某一周每天的全部日程，下标 0 为周一。 */
    @SuppressWarnings("unchecked")
    public static List<Course>[] weekCourses(Context ctx, int weekNumber, int days) {
        List<Course>[] grid = new List[days];
        LocalDate monday = currentSemester(ctx).dateOf(weekNumber, 1);
        for (int i = 0; i < days; i++) {
            grid[i] = monday == null
                    ? new ArrayList<>()
                    : coursesOn(ctx, monday.plusDays(i), weekNumber);
        }
        return grid;
    }

    public static List<Todo> todosOn(Context ctx, LocalDate day) {
        List<Todo> out = new ArrayList<>();
        String key = day.toString();
        for (Todo todo : ScheduleStore.get(ctx).todos()) {
            if (key.equals(todo.date)) {
                out.add(todo);
            }
        }
        out.sort(Comparator
                .comparing((Todo t) -> t.done ? 1 : 0)
                .thenComparingInt(t -> {
                    int minutes = TimeSlots.minutes(t.startTime);
                    return minutes < 0 ? Integer.MAX_VALUE : minutes;
                })
                .thenComparing(t -> t.title));
        return out;
    }

    public static List<Todo> todosBetween(Context ctx, LocalDate from, LocalDate to) {
        List<Todo> out = new ArrayList<>();
        for (Todo todo : ScheduleStore.get(ctx).todos()) {
            LocalDate date = com.netessx.qutschedule.util.Dates.parse(todo.date);
            if (date != null && !date.isBefore(from) && !date.isAfter(to)) {
                out.add(todo);
            }
        }
        return out;
    }

    public static List<Course> unscheduled(Context ctx) {
        String semesterId = currentSemester(ctx).id;
        List<Course> out = new ArrayList<>();
        for (Course course : ScheduleStore.get(ctx).coursesOf(semesterId)) {
            if (course.isUnscheduled()) {
                out.add(course);
            }
        }
        return out;
    }

    /** 按课程名聚合，供课程管理页使用。 */
    public static List<Course> coursesOfName(Context ctx, String name) {
        List<Course> out = new ArrayList<>();
        for (Course course : ScheduleStore.get(ctx).coursesOf(currentSemester(ctx).id)) {
            if (course.name != null && course.name.equals(name)) {
                out.add(course);
            }
        }
        return out;
    }

    public static List<String> courseNames(Context ctx) {
        List<String> names = new ArrayList<>();
        for (Course course : ScheduleStore.get(ctx).coursesOf(currentSemester(ctx).id)) {
            if (course.name != null && !course.name.isEmpty() && !names.contains(course.name)) {
                names.add(course.name);
            }
        }
        names.sort(Comparator.naturalOrder());
        return names;
    }

    public static boolean hasAnyCourse(Context ctx) {
        return !ScheduleStore.get(ctx).coursesOf(currentSemester(ctx).id).isEmpty();
    }

    public static LocalTime[] timesOf(Context ctx, Course course) {
        return timesOf(ctx, course, LocalDate.now());
    }

    /** 课程起止时间；按节次的课取当天生效的作息方案。 */
    public static LocalTime[] timesOf(Context ctx, Course course, LocalDate date) {
        if (course.isOneOff()) {
            LocalTime start = parseTime(course.startTime);
            LocalTime end = parseTime(course.endTime);
            if (start == null) {
                start = LocalTime.of(9, 0);
            }
            if (end == null) {
                end = start.plusHours(1);
            }
            return new LocalTime[]{start, end};
        }
        TimeScheme scheme = schemeFor(ctx, date);
        // 同一节课不同楼层可能错峰上下课，楼层从教室编号推（A302 → 3 楼），认不出按普通时间
        int floor = RoomFloor.of(course.location);
        int start = TimeSlots.minutes(scheme.startOf(course.startSlot, floor));
        int end = TimeSlots.minutes(scheme.endOf(course.endSlot, floor));
        if (start < 0) {
            start = TimeSlots.minutes(TimeSlots.DEFAULT_START[0]);
        }
        if (end < 0) {
            end = TimeSlots.minutes(TimeSlots.DEFAULT_END[0]);
        }
        return new LocalTime[]{LocalTime.of(start / 60, start % 60), LocalTime.of(end / 60, end % 60)};
    }

    /** 待办起止时间；不限时的返回 null。 */
    public static LocalTime[] timesOf(Todo todo) {
        LocalTime start = parseTime(todo.startTime);
        if (start == null) {
            return null;
        }
        LocalTime end = parseTime(todo.endTime);
        return new LocalTime[]{start, end == null ? start.plusHours(1) : end};
    }

    private static LocalTime parseTime(String hhmm) {
        int minutes = TimeSlots.minutes(hhmm);
        return minutes < 0 ? null : LocalTime.of(minutes / 60, minutes % 60);
    }

    public static Course currentCourse(Context ctx, LocalDateTime now) {
        for (Course course : coursesOn(ctx, now.toLocalDate())) {
            LocalTime[] times = timesOf(ctx, course, now.toLocalDate());
            if (now.toLocalTime().compareTo(times[0]) >= 0 && now.toLocalTime().isBefore(times[1])) {
                return course;
            }
        }
        return null;
    }

    public static Course nextCourse(Context ctx, LocalDateTime now) {
        for (Course course : coursesOn(ctx, now.toLocalDate())) {
            LocalTime[] times = timesOf(ctx, course, now.toLocalDate());
            if (now.toLocalTime().isBefore(times[0])) {
                return course;
            }
        }
        return null;
    }

    /** 当天第一节课的开始时间，没有课返回 null；用于灵动岛的时间窗口。 */
    public static LocalTime firstClassStart(Context ctx, LocalDate date) {
        LocalTime earliest = null;
        for (Course course : coursesOn(ctx, date)) {
            LocalTime start = timesOf(ctx, course, date)[0];
            if (earliest == null || start.isBefore(earliest)) {
                earliest = start;
            }
        }
        return earliest;
    }

    /** 当天最后一节课的结束时间，没有课返回 null。 */
    public static LocalTime lastClassEnd(Context ctx, LocalDate date) {
        LocalTime latest = null;
        for (Course course : coursesOn(ctx, date)) {
            LocalTime end = timesOf(ctx, course, date)[1];
            if (latest == null || end.isAfter(latest)) {
                latest = end;
            }
        }
        return latest;
    }

    public static void sort(List<Course> list, final Context ctx) {
        final LocalDate today = LocalDate.now();
        list.sort(Comparator
                .comparingInt((Course c) -> {
                    int minutes = c.isOneOff()
                            ? TimeSlots.minutes(c.startTime)
                            : TimeSlots.minutes(schemeFor(ctx, today)
                                    .startOf(c.startSlot, RoomFloor.of(c.location)));
                    return minutes < 0 ? 0 : minutes;
                })
                .thenComparing(c -> c.name == null ? "" : c.name));
    }
}
