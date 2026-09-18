package com.netessx.qutschedule.backup;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import androidx.core.content.ContextCompat;

import com.netessx.qutschedule.data.ScheduleRepository;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.model.Course;
import com.netessx.qutschedule.model.Semester;
import com.netessx.qutschedule.util.Dates;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * 把当前课表写进系统日历账号。
 *
 * <p>重复同步时先按「标题 + 开始时间」查重，避免越同步越多。
 */
public final class SystemCalendarSync {

    private static final String ACCOUNT_NAME = "QUTSchedule";
    private static final String ACCOUNT_TYPE = "com.netessx.qutschedule";

    private SystemCalendarSync() {
    }

    public static boolean hasPermission(Context ctx) {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 写入当前学期的全部课程。
     *
     * @return 实际新增的事件条数；无权限或无法创建日历时返回 -1
     */
    public static int sync(Context ctx, int reminderMinutes) {
        if (!hasPermission(ctx)) {
            return -1;
        }
        long calendarId = ensureCalendar(ctx);
        if (calendarId <= 0) {
            return -1;
        }
        Semester semester = ScheduleRepository.currentSemester(ctx);
        if (!semester.isConfigured()) {
            return 0;
        }
        int added = 0;
        for (Course course : ScheduleStore.get(ctx).coursesOf(semester.id)) {
            if (course.isUnscheduled()) {
                continue;
            }
            for (LocalDate date : datesOf(course, semester)) {
                LocalTime[] times = ScheduleRepository.timesOf(ctx, course, date);
                if (!exists(ctx, course.name, date, times[0])) {
                    insert(ctx, calendarId, course, date, times, reminderMinutes);
                    added++;
                }
            }
        }
        return added;
    }

    private static List<LocalDate> datesOf(Course course, Semester semester) {
        List<LocalDate> dates = new ArrayList<>();
        if (course.isOneOff()) {
            LocalDate date = Dates.parse(course.date);
            if (date != null) {
                dates.add(date);
            }
            return dates;
        }
        for (int week : course.weeks) {
            LocalDate date = semester.dateOf(week, course.dayOfWeek);
            if (date != null) {
                dates.add(date);
            }
        }
        return dates;
    }

    private static long ensureCalendar(Context ctx) {
        ContentResolver resolver = ctx.getContentResolver();
        String selection = CalendarContract.Calendars.ACCOUNT_NAME + "=? AND "
                + CalendarContract.Calendars.ACCOUNT_TYPE + "=?";
        try (Cursor cursor = resolver.query(CalendarContract.Calendars.CONTENT_URI,
                new String[]{CalendarContract.Calendars._ID}, selection,
                new String[]{ACCOUNT_NAME, ACCOUNT_TYPE}, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        } catch (RuntimeException e) {
            return -1;
        }

        ContentValues values = new ContentValues();
        values.put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME);
        values.put(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE);
        values.put(CalendarContract.Calendars.NAME, "课程表");
        values.put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "课程表");
        values.put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF6200EE);
        values.put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.CAL_ACCESS_OWNER);
        values.put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME);
        values.put(CalendarContract.Calendars.SYNC_EVENTS, 1);

        Uri uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
                .build();
        try {
            Uri created = resolver.insert(uri, values);
            return created == null ? -1 : ContentUris.parseId(created);
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static boolean exists(Context ctx, String title, LocalDate date, LocalTime start) {
        String selection = CalendarContract.Events.TITLE + "=? AND "
                + CalendarContract.Events.DTSTART + "=?";
        try (Cursor cursor = ctx.getContentResolver().query(CalendarContract.Events.CONTENT_URI,
                new String[]{CalendarContract.Events._ID}, selection,
                new String[]{title, String.valueOf(millis(date, start))}, null)) {
            return cursor != null && cursor.getCount() > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void insert(Context ctx, long calendarId, Course course, LocalDate date,
                               LocalTime[] times, int reminderMinutes) {
        ContentValues values = new ContentValues();
        values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
        values.put(CalendarContract.Events.TITLE, course.name);
        values.put(CalendarContract.Events.EVENT_LOCATION, course.location);
        values.put(CalendarContract.Events.DESCRIPTION, course.teacher);
        values.put(CalendarContract.Events.DTSTART, millis(date, times[0]));
        values.put(CalendarContract.Events.DTEND, millis(date, times[1]));
        values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
        if (reminderMinutes > 0) {
            values.put(CalendarContract.Events.HAS_ALARM, 1);
        }
        try {
            Uri created = ctx.getContentResolver()
                    .insert(CalendarContract.Events.CONTENT_URI, values);
            if (created == null || reminderMinutes <= 0) {
                return;
            }
            ContentValues reminder = new ContentValues();
            reminder.put(CalendarContract.Reminders.EVENT_ID, ContentUris.parseId(created));
            reminder.put(CalendarContract.Reminders.MINUTES, reminderMinutes);
            reminder.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
            ctx.getContentResolver().insert(CalendarContract.Reminders.CONTENT_URI, reminder);
        } catch (RuntimeException ignored) {
            // 单条写入失败不影响其它课程
        }
    }

    private static long millis(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
