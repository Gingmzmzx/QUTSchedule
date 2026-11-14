package com.netessx.qutschedule.auto;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.model.Prefs;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * 自动刷新的定时器。
 *
 * <p>用 {@link AlarmManager} 在用户设定的时间唤醒 {@link AutoRefreshReceiver}，由它把
 * 抓取任务交给 {@link AutoRefreshJobService}。每天 / 每周两种节奏共用一个 PendingIntent，
 * 改设置时先取消再重排，避免留下旧闹钟。
 */
public final class AutoRefreshScheduler {

    public static final String ACTION_REFRESH = "com.netessx.qutschedule.action.AUTO_REFRESH";

    private static final int REQUEST_CODE = 0x41555246; // "AURF"

    private AutoRefreshScheduler() {
    }

    /** 按当前设置重新排下一次刷新；关闭时只取消。 */
    public static void reschedule(Context context) {
        Context app = context.getApplicationContext();
        AlarmManager alarms = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) {
            return;
        }
        PendingIntent previous = PendingIntent.getBroadcast(app, REQUEST_CODE, intent(app),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (previous != null) {
            alarms.cancel(previous);
            previous.cancel();
        }
        Prefs prefs = ScheduleStore.get(app).data().prefs;
        if (!prefs.autoRefreshEnabled) {
            ZfKeepAliveJobService.cancel(app);
            return;
        }
        long triggerAt = nextTrigger(prefs, System.currentTimeMillis(), ZoneId.systemDefault());
        if (triggerAt <= 0) {
            return;
        }
        setExact(alarms, triggerAt, PendingIntent.getBroadcast(app, REQUEST_CODE, intent(app),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        // 定时刷新前先让会话别过期：开启自动刷新且抓过课表时才保活
        ZfKeepAliveJobService.sync(app);
    }

    /**
     * 下一次刷新时刻（epoch millis）。纯函数，便于单元测试。
     *
     * <p>每天：取今天的目标时刻，已过（或正好等于）就顺延到明天。
     * 每周：取下一个目标星期几的目标时刻，今天正好是那天且时间还没到就用今天。
     */
    public static long nextTrigger(Prefs prefs, long now, ZoneId zone) {
        LocalDateTime base = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone);
        LocalTime time = LocalTime.of(prefs.autoRefreshHour, prefs.autoRefreshMinute);
        if (Prefs.REFRESH_WEEKLY.equals(prefs.autoRefreshMode)) {
            for (int i = 0; i < 8; i++) {
                LocalDate day = base.toLocalDate().plusDays(i);
                if (day.getDayOfWeek().getValue() != prefs.autoRefreshWeekday) {
                    continue;
                }
                LocalDateTime at = LocalDateTime.of(day, time);
                if (at.isAfter(base)) {
                    return at.atZone(zone).toInstant().toEpochMilli();
                }
            }
            return 0;
        }
        LocalDateTime at = LocalDateTime.of(base.toLocalDate(), time);
        if (!at.isAfter(base)) {
            at = at.plusDays(1);
        }
        return at.atZone(zone).toInstant().toEpochMilli();
    }

    private static Intent intent(Context context) {
        return new Intent(context, AutoRefreshReceiver.class).setAction(ACTION_REFRESH);
    }

    private static void setExact(AlarmManager alarms, long triggerAt, PendingIntent pending) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) {
                // 没有精确闹钟权限时退化成窗口闹钟，仍然会触发，只是可能晚几分钟
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
            } else {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
            }
        } catch (SecurityException e) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
        }
    }
}
