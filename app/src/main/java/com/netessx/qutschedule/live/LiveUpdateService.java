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
import com.netessx.qutschedule.reminder.ReminderScheduler;
import com.netessx.qutschedule.util.ColorPalette;
import com.netessx.qutschedule.util.Countdown;
import com.netessx.qutschedule.widget.ScheduleWidgetProvider;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 常驻后台服务：上课那一刻状态栏 / 灵动岛能立刻出内容，不必先打开应用。
 *
 * <p>之前只在课点前后启动、其余时间自我结束；一旦被系统清掉就没人推送了，只能等用户打开应用
 * 才刷新。现在改成常驻：只要「状态栏 / 灵动岛」开关是开的，服务就一直活着，自己按节拍更新
 * 通知与媒体进度条，并顺手重建提醒闹钟。
 *
 * <p>上课中的那条通知做成媒体会话（见 {@link ClassMediaSession}），系统的媒体卡片、锁屏
 * 与各家灵动岛都认它，不用逐家适配私有接口。
 */
public class LiveUpdateService extends Service {

    private static final String TAG = "LiveUpdateService";
    private static final String CHANNEL_LIVE = "class_live";
    private static final int NOTIFICATION_ID = 2001;
    /** 上课时刷新密一点，进度和「还剩多久」才跟得上。 */
    private static final long TICK_IN_CLASS_MS = 15_000L;
    private static final long TICK_IDLE_MS = 60_000L;
    /** 快上课前多久开始预告。 */
    private static final int UPCOMING_MINUTES = 30;
    /** 每隔多久重建一次提醒闹钟，防止系统在某些机型上把闹钟丢掉。 */
    private static final long REMINDER_RESYNC_MS = 10 * 60_000L;

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter MD_HM = DateTimeFormatter.ofPattern("M/d HH:mm");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastReminderSync;
    private boolean inClass;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            long delay = TICK_IDLE_MS;
            try {
                if (!ScheduleStore.get(LiveUpdateService.this).data().prefs.liveUpdateEnabled) {
                    stopSelf();
                    return;
                }
                updateContent(LiveUpdateService.this);
                // 小组件跟着这个节拍走，进度条才会动
                ScheduleWidgetProvider.updateAll(LiveUpdateService.this);
                resyncRemindersIfDue(LiveUpdateService.this);
                delay = inClass ? TICK_IN_CLASS_MS : TICK_IDLE_MS;
            } catch (Throwable t) {
                // 服务里漏出的异常会直接闪退，这里全部兜住
                Log.w(TAG, "刷新状态栏失败", t);
            }
            handler.postDelayed(this, delay);
        }
    };

    /** 从应用前台调用：拉起常驻服务。后台进程调它会被系统拒绝，所以只在能的时候试。 */
    public static void start(Context context) {
        Context app = context.getApplicationContext();
        if (!ScheduleStore.get(app).data().prefs.liveUpdateEnabled) {
            return;
        }
        try {
            ContextCompat.startForegroundService(app, new Intent(app, LiveUpdateService.class));
        } catch (Throwable t) {
            // Android 12 起后台启动前台服务受限，起不来不影响已有通知
            Log.w(TAG, "启动常驻服务失败", t);
        }
    }

    /** 数据或设置变化后刷新一次内容；开关关掉时同时停掉服务。 */
    public static void refresh(Context context) {
        try {
            Context app = context.getApplicationContext();
            if (!ScheduleStore.get(app).data().prefs.liveUpdateEnabled) {
                cancel(app);
                ClassMediaSession.stop();
                app.stopService(new Intent(app, LiveUpdateService.class));
                return;
            }
            updateContent(app);
        } catch (Throwable t) {
            Log.w(TAG, "刷新状态栏失败", t);
        }
    }

    /** 重算通知内容与媒体会话；不负责启停服务。 */
    private static void updateContent(Context ctx) {
        LocalDateTime now = LocalDateTime.now();
        Course current = ScheduleRepository.currentCourse(ctx, now);
        if (current != null) {
            post(ctx, build(ctx, current, now, true));
            setInClass(ctx, true);
            return;
        }

        ClassMediaSession.stop();
        setInClass(ctx, false);

        Course next = ScheduleRepository.nextCourse(ctx, now);
        if (next != null && minutesUntil(ctx, next, now) <= UPCOMING_MINUTES) {
            post(ctx, build(ctx, next, now, false));
        } else {
            post(ctx, idle(ctx, next, now));
        }
    }

    /** ticker 靠它决定刷新频率，所以状态记在服务实例上。 */
    private static void setInClass(Context ctx, boolean value) {
        if (ctx instanceof LiveUpdateService) {
            ((LiveUpdateService) ctx).inClass = value;
        }
    }

    /** 空闲时的常驻通知：没有课也要有一条，前台服务必须带通知。 */
    private static Notification idle(Context ctx, Course next, LocalDateTime now) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent content = PendingIntent.getActivity(ctx, NOTIFICATION_ID,
                new Intent(ctx, MainActivity.class), flags);

        String text = ctx.getString(R.string.notif_idle_none);
        if (next != null) {
            LocalDate today = now.toLocalDate();
            LocalTime[] times = ScheduleRepository.timesOf(ctx, next, today);
            LocalDateTime at = LocalDateTime.of(today, times[0]);
            if (!at.isAfter(now)) {
                // 今天的课都上完了，下一节在明天
                at = at.plusDays(1);
            }
            text = ctx.getString(R.string.notif_idle_next_fmt, at.format(MD_HM),
                    next.name == null ? "" : next.name);
        }
        return new Notification.Builder(ctx, CHANNEL_LIVE)
                .setSmallIcon(R.drawable.ic_day)
                .setContentTitle(ctx.getString(R.string.notif_idle_title))
                .setContentText(text)
                .setContentIntent(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .build();
    }

    private static long minutesUntil(Context ctx, Course course, LocalDateTime now) {
        LocalTime[] times = ScheduleRepository.timesOf(ctx, course);
        return Duration.between(now.toLocalTime(), times[0]).toMinutes();
    }

    /** 定期重建提醒闹钟：部分机型会把已排的精确闹钟丢掉，靠常驻服务补回来。 */
    private static void resyncRemindersIfDue(Context ctx) {
        LiveUpdateService service = ctx instanceof LiveUpdateService
                ? (LiveUpdateService) ctx : null;
        if (service == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - service.lastReminderSync < REMINDER_RESYNC_MS) {
            return;
        }
        service.lastReminderSync = now;
        ReminderScheduler.sync(ctx);
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

    /**
     * @param media 是否挂到媒体会话上。上课前的预告不用挂 —— 那会让系统以为已经开始播放了。
     */
    private static Notification build(Context ctx, Course course, LocalDateTime now, boolean media) {
        LocalTime[] times = ScheduleRepository.timesOf(ctx, course);
        int startMinutes = times[0].getHour() * 60 + times[0].getMinute();
        int endMinutes = times[1].getHour() * 60 + times[1].getMinute();
        int nowMinutes = now.getHour() * 60 + now.getMinute();
        int total = Math.max(1, endMinutes - startMinutes);

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

        Notification.Builder builder = new Notification.Builder(ctx, CHANNEL_LIVE)
                .setSmallIcon(R.drawable.ic_day)
                .setContentTitle(title)
                .setContentText(text.toString())
                .setContentIntent(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setColor(ColorPalette.colorOf(ctx, course))
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
        // 前台服务必须在 5 秒内带上通知，哪怕是空闲状态的那条
        try {
            if (!ScheduleStore.get(this).data().prefs.liveUpdateEnabled) {
                stopSelf();
                return START_NOT_STICKY;
            }
            updateContent(this);
            ServiceCompat.startForeground(this, NOTIFICATION_ID, currentNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } catch (Throwable t) {
            // 后台启动受限、通知被禁、厂商 ROM 拦截都会在这里抛，没人接住就是整个应用闪退
            Log.w(TAG, "常驻服务启动失败", t);
            stopSelf();
            return START_NOT_STICKY;
        }
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, inClass ? TICK_IN_CLASS_MS : TICK_IDLE_MS);
        return START_STICKY;
    }

    /** 刚要展示的那条通知；startForeground 需要一个非空通知。 */
    private Notification currentNotification() {
        LocalDateTime now = LocalDateTime.now();
        Course current = ScheduleRepository.currentCourse(this, now);
        if (current != null) {
            return build(this, current, now, true);
        }
        return idle(this, ScheduleRepository.nextCourse(this, now), now);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(ticker);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        // 用户从最近任务里划掉应用：会话留着会变成一条没人管的媒体卡片。
        // 服务本身是 START_STICKY，系统会把它拉回来继续常驻。
        ClassMediaSession.stop();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
