package com.netessx.qutschedule.backup;

import android.content.Context;
import android.net.Uri;

import com.netessx.qutschedule.data.ScheduleRepository;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.model.Course;
import com.netessx.qutschedule.model.CourseShift;
import com.netessx.qutschedule.model.Semester;
import com.netessx.qutschedule.model.Todo;
import com.netessx.qutschedule.util.Dates;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 导出 ICS 日历，可导入 iOS / Android / 桌面日历，自带课前提醒。
 *
 * <p>周次不连续的课程用 {@code DTSTART + RDATE} 展开，不必为每节课生成一条 VEVENT。
 * 时间统一换算成 UTC 输出，省掉 VTIMEZONE 的兼容麻烦。
 */
public final class IcsExporter {

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final String PRODID = "-//QUTSchedule//CN";
    private static final int FOLD_LIMIT = 73;

    private IcsExporter() {
    }

    public static void export(Context ctx, Uri target, int reminderMinutes) throws IOException {
        String content = build(ctx, reminderMinutes);
        try (OutputStream out = ctx.getContentResolver().openOutputStream(target, "wt")) {
            if (out == null) {
                throw new IOException("无法写入所选位置");
            }
            try (OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                writer.write(content);
            }
        }
    }

    static String build(Context ctx, int reminderMinutes) {
        Semester semester = ScheduleRepository.currentSemester(ctx);
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n")
                .append("VERSION:2.0\r\n")
                .append("PRODID:").append(PRODID).append("\r\n")
                .append("CALSCALE:GREGORIAN\r\n")
                .append("METHOD:PUBLISH\r\n");

        if (semester.isConfigured()) {
            List<CourseShift> shifts = ScheduleStore.get(ctx).shifts();
            for (Course course : ScheduleStore.get(ctx).coursesOf(semester.id)) {
                if (!course.isUnscheduled()) {
                    appendCourse(ctx, sb, course, semester, reminderMinutes, shifts);
                }
            }
            // 临时挪过来的课另起一条 VEVENT：原课那一次已经被过滤掉了
            for (CourseShift shift : shifts) {
                if (shift == null || !shift.isValid() || !shift.isMove()) {
                    continue;
                }
                Course origin = ScheduleStore.get(ctx).findCourse(shift.courseId);
                if (origin != null && !origin.isUnscheduled()) {
                    appendMoved(ctx, sb, origin, shift, reminderMinutes);
                }
            }
        }
        for (Todo todo : ScheduleStore.get(ctx).todos()) {
            appendTodo(ctx, sb, todo, reminderMinutes);
        }

        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    private static void appendCourse(Context ctx, StringBuilder sb, Course course,
                                     Semester semester, int reminderMinutes,
                                     List<CourseShift> shifts) {
        List<LocalDate> dates = new ArrayList<>();
        if (course.isOneOff()) {
            LocalDate date = Dates.parse(course.date);
            if (date != null) {
                dates.add(date);
            }
        } else {
            for (int week : course.weeks) {
                LocalDate date = semester.dateOf(week, course.dayOfWeek);
                if (date != null) {
                    dates.add(date);
                }
            }
        }
        // 被临时停掉 / 挪走的那几次不再出现在原日期上
        dates.removeIf(date -> CourseShift.onDate(shifts, course.id, date.toString()) != null);
        if (dates.isEmpty()) {
            return;
        }
        dates.sort(LocalDate::compareTo);
        LocalTime[] times = ScheduleRepository.timesOf(ctx, course, dates.get(0));

        sb.append("BEGIN:VEVENT\r\n");
        append(sb, "UID", course.id + "@qutschedule");
        append(sb, "DTSTAMP", utc(LocalDateTime.now()));
        append(sb, "DTSTART", utc(LocalDateTime.of(dates.get(0), times[0])));
        append(sb, "DTEND", utc(LocalDateTime.of(dates.get(0), times[1])));
        append(sb, "SUMMARY", course.name);
        if (course.location != null && !course.location.isEmpty()) {
            append(sb, "LOCATION", course.location);
        }
        StringBuilder description = new StringBuilder();
        if (course.teacher != null && !course.teacher.isEmpty()) {
            description.append(course.teacher);
        }
        if (course.weekSpec != null && !course.weekSpec.isEmpty()) {
            if (description.length() > 0) {
                description.append(" · ");
            }
            description.append(course.weekSpec);
        }
        if (description.length() > 0) {
            append(sb, "DESCRIPTION", description.toString());
        }
        if (dates.size() > 1) {
            StringBuilder rdate = new StringBuilder();
            for (int i = 1; i < dates.size(); i++) {
                if (rdate.length() > 0) {
                    rdate.append(',');
                }
                rdate.append(utc(LocalDateTime.of(dates.get(i), times[0])));
            }
            append(sb, "RDATE", rdate.toString());
        }
        appendAlarm(sb, course.name,
                course.leadMinutes(ScheduleStore.get(ctx).data().prefs.defaultReminderMinutes),
                reminderMinutes);
        sb.append("END:VEVENT\r\n");
    }

    /** 临时调课挪过去的那一次：单独一条 VEVENT，节次被覆盖时时间跟着变。 */
    private static void appendMoved(Context ctx, StringBuilder sb, Course origin, CourseShift shift,
                                    int reminderMinutes) {
        LocalDate target = Dates.parse(shift.toDate);
        if (target == null) {
            return;
        }
        Course moved = origin.copy();
        moved.startSlot = shift.startSlotOf(origin);
        moved.endSlot = shift.endSlotOf(origin);
        LocalTime[] times = ScheduleRepository.timesOf(ctx, moved, target);

        sb.append("BEGIN:VEVENT\r\n");
        append(sb, "UID", origin.id + "-shift-" + shift.id + "@qutschedule");
        append(sb, "DTSTAMP", utc(LocalDateTime.now()));
        append(sb, "DTSTART", utc(LocalDateTime.of(target, times[0])));
        append(sb, "DTEND", utc(LocalDateTime.of(target, times[1])));
        append(sb, "SUMMARY", origin.name + "（调课）");
        if (origin.location != null && !origin.location.isEmpty()) {
            append(sb, "LOCATION", origin.location);
        }
        if (origin.teacher != null && !origin.teacher.isEmpty()) {
            append(sb, "DESCRIPTION", origin.teacher);
        }
        appendAlarm(sb, origin.name,
                origin.leadMinutes(ScheduleStore.get(ctx).data().prefs.defaultReminderMinutes),
                reminderMinutes);
        sb.append("END:VEVENT\r\n");
    }

    private static void appendTodo(Context ctx, StringBuilder sb, Todo todo, int reminderMinutes) {
        LocalDate date = Dates.parse(todo.date);
        if (date == null) {
            return;
        }
        LocalTime[] times = ScheduleRepository.timesOf(todo);
        LocalDateTime start = times == null
                ? LocalDateTime.of(date, LocalTime.of(9, 0))
                : LocalDateTime.of(date, times[0]);
        LocalDateTime end = times == null ? start.plusHours(1) : LocalDateTime.of(date, times[1]);

        sb.append("BEGIN:VEVENT\r\n");
        append(sb, "UID", todo.id + "@qutschedule");
        append(sb, "DTSTAMP", utc(LocalDateTime.now()));
        append(sb, "DTSTART", utc(start));
        append(sb, "DTEND", utc(end));
        append(sb, "SUMMARY", todo.title);
        if (todo.note != null && !todo.note.isEmpty()) {
            append(sb, "DESCRIPTION", todo.note);
        }
        appendAlarm(sb, todo.title, reminderMinutes, reminderMinutes);
        sb.append("END:VEVENT\r\n");
    }

    private static void appendAlarm(StringBuilder sb, String title, int leadMinutes, int fallback) {
        int minutes = leadMinutes >= 0 ? leadMinutes : fallback;
        if (minutes <= 0) {
            return;
        }
        sb.append("BEGIN:VALARM\r\n")
                .append("ACTION:DISPLAY\r\n")
                .append("TRIGGER:-PT").append(minutes).append("M\r\n");
        append(sb, "DESCRIPTION", title);
        sb.append("END:VALARM\r\n");
    }

    private static void append(StringBuilder sb, String key, String value) {
        String line = key + ":" + escape(value);
        // RFC 5545 要求每行不超过 75 个八位组，超出部分折行续写
        while (line.getBytes(StandardCharsets.UTF_8).length > FOLD_LIMIT) {
            int split = line.length();
            while (split > 1
                    && line.substring(0, split).getBytes(StandardCharsets.UTF_8).length > FOLD_LIMIT) {
                split--;
            }
            sb.append(line, 0, split).append("\r\n ");
            line = line.substring(split);
        }
        sb.append(line).append("\r\n");
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n");
    }

    private static String utc(LocalDateTime local) {
        return local.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC)
                .format(UTC);
    }
}
