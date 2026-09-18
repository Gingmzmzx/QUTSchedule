package com.netessx.qutschedule.reminder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.netessx.qutschedule.MainActivity;
import com.netessx.qutschedule.R;
import com.netessx.qutschedule.data.ScheduleRepository;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.live.LiveUpdateService;
import com.netessx.qutschedule.model.Course;
import com.netessx.qutschedule.util.Dates;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** 收到闹钟后弹出上课提醒，并顺手刷新状态栏里的当前课程。 */
public class ReminderReceiver extends BroadcastReceiver {

    static final String CHANNEL_REMINDER = "class_reminder";

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        if (ReminderScheduler.ACTION_CLASS_END.equals(intent.getAction())) {
            // 下课恢复免打扰；连堂时下一节课的提醒会再次打开
            DndController.restore(context);
            LiveUpdateService.refresh(context);
            return;
        }
        if (!ReminderScheduler.ACTION_REMIND.equals(intent.getAction())) {
            return;
        }
        DndController.enable(context, ScheduleStore.get(context).data().prefs);
        String courseId = intent.getStringExtra(ReminderScheduler.EXTRA_COURSE_ID);
        Course course = ScheduleStore.get(context).findCourse(courseId);
        if (course == null) {
            return;
        }
        LocalDate day = Dates.parse(intent.getStringExtra(ReminderScheduler.EXTRA_DATE));
        if (day == null) {
            day = LocalDate.now();
        }
        showReminder(context, course);
        LiveUpdateService.refresh(context);
    }

    private void showReminder(Context context, Course course) {
        ensureChannel(context);
        LocalTime[] times = ScheduleRepository.timesOf(context, course);

        StringBuilder body = new StringBuilder();
        body.append(HM.format(times[0])).append(" - ").append(HM.format(times[1]));
        if (course.location != null && !course.location.isEmpty()) {
            body.append(" · ").append(course.location);
        }
        if (course.teacher != null && !course.teacher.isEmpty()) {
            body.append(" · ").append(course.teacher);
        }

        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent content = PendingIntent.getActivity(context, course.id.hashCode(),
                new Intent(context, MainActivity.class), flags);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_REMINDER)
                .setSmallIcon(R.drawable.ic_day)
                .setContentTitle(context.getString(R.string.notif_reminder_title_fmt, course.name))
                .setContentText(body.toString())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body.toString()))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(content)
                .setWhen(System.currentTimeMillis())
                .build();

        try {
            NotificationManagerCompat.from(context).notify(course.id.hashCode(), notification);
        } catch (SecurityException ignored) {
            // 用户关掉了通知权限，忽略即可
        }
    }

    static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_REMINDER) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_REMINDER,
                context.getString(R.string.channel_reminder_name),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(R.string.channel_reminder_desc));
        channel.enableVibration(true);
        manager.createNotificationChannel(channel);
    }
}
