package com.netessx.qutschedule.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import com.netessx.qutschedule.data.ScheduleRepository;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.model.Course;
import com.netessx.qutschedule.model.Semester;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

/**
 * 上课提醒：只为未来一周内的课程排精确闹钟，每次数据变化整体重建。
 *
 * <p>已排的闹钟键记在 SharedPreferences 里，重建时按同样的 requestCode 先取消，避免残留。
 */
public final class ReminderScheduler {

    public static final String ACTION_REMIND = "com.netessx.qutschedule.action.REMIND";
    /** 下课：用来恢复免打扰。 */
    public static final String ACTION_CLASS_END = "com.netessx.qutschedule.action.CLASS_END";
    public static final String EXTRA_COURSE_ID = "course_id";
    public static final String EXTRA_DATE = "date";

    private static final String[] ACTIONS = {ACTION_REMIND, ACTION_CLASS_END};

    private static final String PREFS = "reminders";
    private static final String KEY_SCHEDULED = "scheduled_keys";
    private static final int WINDOW_DAYS = 7;

    private ReminderScheduler() {
    }

    public static void sync(Context context) {
        Context app = context.getApplicationContext();
        AlarmManager alarmManager = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        cancelAll(app, alarmManager, prefs.getStringSet(KEY_SCHEDULED, new HashSet<>()));

        Set<String> scheduled = new HashSet<>();
        Semester semester = ScheduleRepository.currentSemester(app);
        com.netessx.qutschedule.model.Prefs appPrefs = ScheduleStore.get(app).data().prefs;
        if (appPrefs.reminderEnabled && semester.isConfigured()) {
            LocalDateTime now = LocalDateTime.now();
            for (int offset = 0; offset < WINDOW_DAYS; offset++) {
                LocalDate date = now.toLocalDate().plusDays(offset);
                int week = semester.weekOf(date);
                if (isHoliday(app, date, appPrefs)) {
                    continue;
                }
                for (Course course : ScheduleRepository.coursesOn(app, date, week)) {
                    if (!course.reminderEnabled) {
                        continue;
                    }
                    LocalTime[] times = ScheduleRepository.timesOf(app, course);
                    long trigger = LocalDateTime.of(date, times[0])
                            .minusMinutes(course.leadMinutes(appPrefs.defaultReminderMinutes))
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli();
                    if (trigger <= System.currentTimeMillis()) {
                        continue;
                    }
                    String key = course.id + "|" + date + "|" + trigger;
                    PendingIntent pending = pendingIntent(app, key, ACTION_REMIND, course.id,
                            date.toString(), PendingIntent.FLAG_UPDATE_CURRENT);
                    if (setExact(alarmManager, trigger, pending)) {
                        scheduled.add(key);
                    }

                    // 下课闹钟只负责恢复免打扰，不弹通知
                    if (appPrefs.dndEnabled) {
                        long endTrigger = LocalDateTime.of(date, times[1])
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli();
                        if (endTrigger > System.currentTimeMillis()) {
                            String endKey = course.id + "|" + date + "|end";
                            setExact(alarmManager, endTrigger, pendingIntent(app, endKey,
                                    ACTION_CLASS_END, course.id, date.toString(),
                                    PendingIntent.FLAG_UPDATE_CURRENT));
                            scheduled.add(endKey);
                        }
                    }
                }
            }
        }
        prefs.edit().putStringSet(KEY_SCHEDULED, scheduled).apply();
    }

    /** 节假日不打扰，除非用户在提醒设置里关掉了这个开关。 */
    private static boolean isHoliday(Context ctx, LocalDate date,
                                     com.netessx.qutschedule.model.Prefs prefs) {
        if (!prefs.holidaySkip) {
            return false;
        }
        return com.netessx.qutschedule.model.DayRule.isOff(
                ScheduleStore.get(ctx).data().dayRules, date.toString());
    }

    private static void cancelAll(Context app, AlarmManager alarmManager, Set<String> keys) {
        for (String key : keys) {
            for (String action : ACTIONS) {
                PendingIntent pending = PendingIntent.getBroadcast(app, key.hashCode(),
                        new Intent(app, ReminderReceiver.class).setAction(action),
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (pending != null) {
                    alarmManager.cancel(pending);
                    pending.cancel();
                }
            }
        }
    }

    private static PendingIntent pendingIntent(Context app, String key, String action,
                                               String courseId, String date, int flags) {
        Intent intent = new Intent(app, ReminderReceiver.class)
                .setAction(action)
                .putExtra(EXTRA_COURSE_ID, courseId)
                .putExtra(EXTRA_DATE, date);
        return PendingIntent.getBroadcast(app, key.hashCode(), intent,
                flags | PendingIntent.FLAG_IMMUTABLE);
    }

    /** 没有精确闹钟权限时退化为非精确闹钟，至少不会漏掉提醒。 */
    private static boolean setExact(AlarmManager alarmManager, long trigger, PendingIntent pending) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending);
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending);
            }
            return true;
        } catch (SecurityException e) {
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending);
                return true;
            } catch (RuntimeException ignored) {
                return false;
            }
        } catch (RuntimeException e) {
            return false;
        }
    }
}
