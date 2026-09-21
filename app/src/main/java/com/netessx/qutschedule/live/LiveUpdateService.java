package com.netessx.qutschedule.live;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.netessx.qutschedule.MainActivity;
import com.netessx.qutschedule.R;
import com.netessx.qutschedule.data.ScheduleRepository;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.model.Course;
import com.netessx.qutschedule.util.ColorPalette;
import com.netessx.qutschedule.util.Countdown;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

/**
 * 状态栏 / 灵动岛上的「当前课程」。
 *
 * <p>做法是把这节课包装成一个媒体会话（见 {@link ClassMediaSession}），通知用
 * {@code MediaStyle}：系统的灵动岛、锁屏、状态栏媒体卡片认的都是这个，标题、副标题和进度条
 * 都能直接显示，不用逐个适配厂商的私有接口。
 *
 * <p>前台服务只在有课进行中时运行，下课后自行停止；即使服务没能起来（例如从后台广播触发被系统
 * 拒绝），前面 post 的通知仍然会显示。
 */
public class LiveUpdateService extends Service {

    private static final String TAG = "LiveUpdateService";
    private static final String CHANNEL_LIVE = "class_live";
    private static final int NOTIFICATION_ID = 2001;
    private static final long TICK_MS = 30_000L;
    /** 快上课前多久开始显示状态。 */
    private static final int UPCOMING_MINUTES = 30;

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            boolean keepGoing;
            try {
                keepGoing = updateNow();
            } catch (Throwable t) {
                Log.w(TAG, "刷新状态栏失败", t);
                keepGoing = false;
            }
            if (!keepGoing) {
                stopSelf();
                return;
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    /** 按当前时间重算状态栏内容，必要时启停前台服务。 */
    public static void refresh(Context context) {
        try {
            refreshInner(context);
        } catch (Throwable t) {
            // 这个方法会被广播（开机、闹钟）直接调用，漏出去的异常会让整个应用闪退
            Log.w(TAG, "刷新状态栏失败", t);
        }
    }

    private static void refreshInner(Context context) {
        Context app = context.getApplicationContext();
        if (!ScheduleStore.get(app).data().prefs.liveUpdateEnabled) {
            cancel(app);
            ClassMediaSession.stop();
            app.stopService(new Intent(app, LiveUpdateService.class));
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (!inTimeWindow(app, now)) {
            // 只在当天第一节课前到最后一节课后之间常驻，其余时间零占用
            cancel(app);
            ClassMediaSession.stop();
            app.stopService(new Intent(app, LiveUpdateService.class));
            return;
        }
        Course current = ScheduleRepository.currentCourse(app, now);
        if (current != null) {
            post(app, build(app, current, now));
            if (isForeground(app)) {
                try {
                    ContextCompat.startForegroundService(app,
                            new Intent(app, LiveUpdateService.class));
                } catch (RuntimeException ignored) {
                    // 通知已经发出，服务起不来不影响
                }
            }
            // 不在前台就不起前台服务：Android 12 起后台启动会抛
            // ForegroundServiceStartNotAllowedException，且在 Service 里没人接得住，直接闪退
            return;
        }

        Course next = ScheduleRepository.nextCourse(app, now);
        if (next != null && minutesUntil(app, next, now) <= UPCOMING_MINUTES) {
            // 还没上课：只在推送里预告，不进媒体卡片
            post(app, build(app, next, now, false));
        } else {
            cancel(app);
            ClassMediaSession.stop();
        }
        app.stopService(new Intent(app, LiveUpdateService.class));
    }

    /** 应用是否在前台；只有前台才允许启动前台服务。 */
    private static boolean isForeground(Context ctx) {
        android.app.ActivityManager manager =
                (android.app.ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        java.util.List<android.app.ActivityManager.RunningAppProcessInfo> processes =
                manager.getRunningAppProcesses();
        if (processes == null) {
            return false;
        }
        String pkg = ctx.getPackageName();
        for (android.app.ActivityManager.RunningAppProcessInfo process : processes) {
            if (pkg.equals(process.processName)) {
                return process.importance
                        <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
            }
        }
        return false;
    }

    /** 当天第一节课前 {@link #UPCOMING_MINUTES} 分钟到最后一节课结束之间才常驻。 */
    private static boolean inTimeWindow(Context ctx, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalTime first = ScheduleRepository.firstClassStart(ctx, today);
        LocalTime last = ScheduleRepository.lastClassEnd(ctx, today);
        if (first == null || last == null) {
            return false;
        }
        LocalTime from = first.minusMinutes(UPCOMING_MINUTES);
        return !now.toLocalTime().isBefore(from) && !now.toLocalTime().isAfter(last);
    }

    private static long minutesUntil(Context ctx, Course course, LocalDateTime now) {
        LocalTime[] times = ScheduleRepository.timesOf(ctx, course);
        return Duration.between(now.toLocalTime(), times[0]).toMinutes();
    }

    private static void post(Context ctx, Notification notification) {
        ensureChannel(ctx);
        try {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notification);
        } catch (SecurityException ignored) {
            // 没有通知权限
        }
    }

    private static void cancel(Context ctx) {
        NotificationManagerCompat.from(ctx).cancel(NOTIFICATION_ID);
    }

    /** 上课中的那条：走媒体会话，系统会把它显示成媒体卡片 / 灵动岛。 */
    private static Notification build(Context ctx, Course course, LocalDateTime now) {
        return build(ctx, course, now, true);
    }

    /**
     * @param media 是否挂到媒体会话上。上课前的预告不用挂 —— 那会让系统以为已经开始播放了。
     */
    private static Notification build(Context ctx, Course course, LocalDateTime now, boolean media) {
        LocalTime[] times = ScheduleRepository.timesOf(ctx, course);
        int startMinutes = times[0].getHour() * 60 + times[0].getMinute();
        int endMinutes = times[1].getHour() * 60 + times[1].getMinute();
        int nowMinutes = now.getHour() * 60 + now.getMinute();
        int total = Math.max(1, endMinutes - startMinutes);
        int percent = Countdown.percent(startMinutes, endMinutes, nowMinutes);

        boolean ongoing = Countdown.isOngoing(startMinutes, endMinutes, nowMinutes);
        // 「还剩多久下课」和「还有多久上课」是两个量，不能混用
        int minutesLeft = ongoing
                ? Countdown.minutesToEnd(endMinutes, nowMinutes)
                : Countdown.minutesToStart(startMinutes, nowMinutes);
        String title = ongoing
                ? ctx.getString(R.string.notif_now_fmt, course.name)
                : ctx.getString(R.string.notif_next_fmt, course.name);
        String remainingText = ongoing
                ? ctx.getString(R.string.notif_remaining_fmt, minutesLeft)
                : ctx.getString(R.string.notif_minutes_fmt, minutesLeft);

        StringBuilder text = new StringBuilder();
        text.append(HM.format(times[0])).append(" - ").append(HM.format(times[1]));
        if (course.location != null && !course.location.isEmpty()) {
            text.append(" · ").append(course.location);
        }
        text.append(" · ").append(remainingText);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent content = PendingIntent.getActivity(ctx, NOTIFICATION_ID,
                new Intent(ctx, MainActivity.class), flags);

        int color = ColorPalette.colorOf(ctx, course);

        Notification.Builder builder = new Notification.Builder(ctx, CHANNEL_LIVE)
                .setSmallIcon(R.drawable.ic_day)
                .setContentTitle(title)
                .setContentText(text.toString())
                .setContentIntent(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setColor(color)
                .setVisibility(Notification.VISIBILITY_PUBLIC);

        if (media) {
            // 把进度喂给媒体会话：系统据此渲染锁屏 / 状态栏卡片与灵动岛，
            // 速度给 1.0，两次刷新之间进度条由系统自己往前走
            long durationMs = Math.max(1, total) * 60_000L;
            long positionMs = Math.min(durationMs,
                    Math.max(0, nowMinutes - startMinutes) * 60_000L);
            ClassMediaSession.update(ctx, course.name, text.toString(), positionMs, durationMs);
            builder.setStyle(new Notification.MediaStyle()
                    .setMediaSession(ClassMediaSession.token(ctx)));
        } else {
            builder.setStyle(new Notification.BigTextStyle().bigText(text.toString()));
        }
        return builder.build();
    }

    private static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = ctx.getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_LIVE) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_LIVE,
                ctx.getString(R.string.channel_live_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(ctx.getString(R.string.channel_live_desc));
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LocalDateTime now = LocalDateTime.now();
        try {
            Course course = ScheduleRepository.currentCourse(this, now);
            if (course == null) {
                stopSelf();
                return START_NOT_STICKY;
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, build(this, course, now),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } catch (Throwable t) {
            // 后台启动受限、通知被禁、厂商 ROM 拦截，都会在这里抛异常。
            // 它发生在 Service 里，没人接住就是整个应用闪退，所以必须自己兜住。
            Log.w(TAG, "状态栏常驻启动失败", t);
            stopSelf();
            return START_NOT_STICKY;
        }
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, TICK_MS);
        return START_STICKY;
    }

    /** 返回 false 表示已经没有需要展示的课程。 */
    private boolean updateNow() {
        if (!ScheduleStore.get(this).data().prefs.liveUpdateEnabled) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        Course current = ScheduleRepository.currentCourse(this, now);
        if (current == null) {
            return false;
        }
        post(this, build(this, current, now));
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(ticker);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        // 用户从最近任务里划掉应用：会话留着会变成一条没人管的媒体卡片
        ClassMediaSession.stop();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
