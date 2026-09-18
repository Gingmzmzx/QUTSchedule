package com.netessx.qutschedule.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import com.netessx.qutschedule.MainActivity;
import com.netessx.qutschedule.R;
import com.netessx.qutschedule.data.ScheduleRepository;
import com.netessx.qutschedule.model.Course;
import com.netessx.qutschedule.model.Semester;
import com.netessx.qutschedule.util.ColorPalette;
import com.netessx.qutschedule.util.Dates;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 桌面小组件：今天 / 明天的课程概览与当日课程数。 */
public class ScheduleWidgetProvider extends AppWidgetProvider {

    /** 每日零点与课程数据变化后用它手动刷新。 */
    public static final String ACTION_REFRESH = "com.netessx.qutschedule.action.WIDGET_REFRESH";

    private static final String TAG = "ScheduleWidget";

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_ROWS = 4;

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            manager.updateAppWidget(id, render(context));
        }
    }

    /**
     * 渲染失败时退回最简样式。
     *
     * <p>小组件里抛异常，桌面只会显示「无法加载」并从此不再刷新，比样式降级糟糕得多。
     */
    private static RemoteViews render(Context context) {
        try {
            return build(context);
        } catch (RuntimeException e) {
            Log.e(TAG, "渲染小组件失败，退回最简样式", e);
            RemoteViews fallback = new RemoteViews(context.getPackageName(),
                    R.layout.widget_schedule);
            fallback.removeAllViews(R.id.widget_rows);
            fallback.setTextViewText(R.id.widget_title, context.getString(R.string.app_name));
            fallback.setTextViewText(R.id.widget_footer, context.getString(R.string.widget_no_class));
            return fallback;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent != null && ACTION_REFRESH.equals(intent.getAction())) {
            updateAll(context);
        }
    }

    @Override
    public void onEnabled(Context context) {
        WidgetScheduler.schedule(context);
    }

    @Override
    public void onDisabled(Context context) {
        WidgetScheduler.cancel(context);
    }

    /** 供调度器与 App 内刷新调用。 */
    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, ScheduleWidgetProvider.class));
        for (int id : ids) {
            manager.updateAppWidget(id, render(context));
        }
    }

    private static RemoteViews build(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_schedule);
        views.removeAllViews(R.id.widget_rows);

        LocalDate today = LocalDate.now();
        Semester semester = ScheduleRepository.currentSemester(context);
        List<Course> courses = ScheduleRepository.coursesOn(context, today);

        StringBuilder title = new StringBuilder(context.getString(R.string.widget_today))
                .append(" · ").append(today.format(Dates.MD))
                .append(" 周").append(Dates.weekName(today));
        if (semester.isConfigured()) {
            title.append(" · 第 ").append(Math.max(1, semester.weekOf(today))).append(" 周");
        }
        views.setTextViewText(R.id.widget_title, title.toString());

        if (courses.isEmpty()) {
            views.addView(R.id.widget_rows, row(context, null,
                    context.getString(R.string.widget_no_class), ""));
        } else {
            int shown = Math.min(courses.size(), MAX_ROWS);
            for (int i = 0; i < shown; i++) {
                Course course = courses.get(i);
                LocalTime[] times = ScheduleRepository.timesOf(context, course, today);
                views.addView(R.id.widget_rows, row(context, course,
                        HM.format(times[0]) + "  " + course.name,
                        course.location == null ? "" : course.location));
            }
        }

        List<Course> tomorrow = ScheduleRepository.coursesOn(context, today.plusDays(1));
        String tomorrowText = tomorrow.isEmpty()
                ? context.getString(R.string.widget_no_class)
                : context.getString(R.string.widget_count_fmt, tomorrow.size());
        views.setTextViewText(R.id.widget_footer,
                context.getString(R.string.widget_count_fmt, courses.size())
                        + " · " + context.getString(R.string.widget_tomorrow) + " " + tomorrowText);

        PendingIntent pending = PendingIntent.getActivity(context, 0,
                new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pending);
        return views;
    }

    private static RemoteViews row(Context context, Course course, String main, String sub) {
        RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_row);
        row.setTextViewText(R.id.row_main, main);
        row.setTextViewText(R.id.row_sub, sub);
        row.setViewVisibility(R.id.row_sub, sub == null || sub.isEmpty() ? View.GONE : View.VISIBLE);
        if (course == null) {
            row.setInt(R.id.row_bar, "setBackgroundColor", Color.TRANSPARENT);
        } else {
            row.setInt(R.id.row_bar, "setBackgroundColor", ColorPalette.colorOf(context, course));
        }
        return row;
    }
}
