package com.netessx.qutschedule.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.netessx.qutschedule.live.LiveUpdateService;
import com.netessx.qutschedule.reminder.ReminderScheduler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * 小组件的每日更新。
 *
 * <p>桌面小组件与上课提醒都不需要打开 App：每天零点自动同步课表、重排提醒并刷新小组件。
 */
public final class WidgetScheduler {

    private static final int REQUEST_CODE = 0x71D6;
    private static final int REFRESH_HOUR = 0;

    private WidgetScheduler() {
    }

    public static void schedule(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) {
            return;
        }
        try {
            manager.setInexactRepeating(AlarmManager.RTC_WAKEUP, nextMidnight(),
                    AlarmManager.INTERVAL_DAY,
                    pending(context, PendingIntent.FLAG_UPDATE_CURRENT));
        } catch (RuntimeException ignored) {
            // 系统拒绝重复闹钟时忽略，用户打开 App 也会刷新
        }
    }

    public static void cancel(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) {
            return;
        }
        PendingIntent pending = pending(context, PendingIntent.FLAG_NO_CREATE);
        if (pending != null) {
            manager.cancel(pending);
        }
    }

    /** 到点后做的事：重排提醒、刷新小组件与状态栏。 */
    public static void updateAll(Context context) {
        ReminderScheduler.sync(context);
        LiveUpdateService.refresh(context);
        ScheduleWidgetProvider.updateAll(context);
    }

    private static PendingIntent pending(Context context, int flags) {
        Intent intent = new Intent(context, ScheduleWidgetProvider.class)
                .setAction(ScheduleWidgetProvider.ACTION_REFRESH);
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent,
                flags | PendingIntent.FLAG_IMMUTABLE);
    }

    private static long nextMidnight() {
        LocalDateTime next = LocalDate.now().plusDays(1).atTime(LocalTime.of(REFRESH_HOUR, 0));
        return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
