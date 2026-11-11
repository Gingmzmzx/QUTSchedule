package com.netessx.qutschedule.wear;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 手表主界面：某一天的完整课表，默认今天。
 *
 * <p>方形表盘，自绘一个 View 搞定：不引布局库与资源库，APK 才能压到极小。
 * 左右滑动切换日期，点一下回到今天；正在上的那节高亮并显示进度。
 * 课表可以从手机经蓝牙推送（{@link BtSyncService}），也可以 adb push。
 */
public class WearActivity extends Activity {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("M/d");
    private static final long TICK_MS = 20_000L;

    private static final int COLOR_BG = 0xFF000000;
    private static final int COLOR_TEXT = 0xFFE6E1E5;
    private static final int COLOR_MUTED = 0xFF9E9A9F;
    private static final int COLOR_ACCENT = 0xFFD0BCFF;
    private static final int COLOR_TITLE = 0xFF8E86A8;
    private static final int COLOR_TRACK = 0xFF2A2A2E;
    private static final int COLOR_CURRENT_BG = 0xFF2B2536;

    private static final String[] WEEK_CN = {"一", "二", "三", "四", "五", "六", "日"};

    private FaceView face;
    private WatchData data;
    /** 相对今天的天数：0 = 今天，1 = 明天，-1 = 昨天。 */
    private int dayOffset;

    private final BroadcastReceiver syncReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // 手机刚通过蓝牙传完：立刻换成本地这份
            data = WatchData.load(WearActivity.this);
            if (face != null) {
                face.invalidate();
            }
        }
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (face != null) {
                face.invalidate();
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    private float downX;
    private float downY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 抬腕看课时别让它黑屏
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        face = new FaceView();
        setContentView(face);
        data = WatchData.load(this);
        // 打开应用就开始等手机推课表，收完会广播回来
        try {
            startService(new Intent(this, BtSyncService.class));
        } catch (Throwable ignored) {
            // 蓝牙不可用/被系统限制时静默，adb push 仍然能用
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 回到前台重新读一次：手机端改了课表、adb push 过新文件都能生效
        data = WatchData.load(this);
        registerReceiver(syncReceiver, new IntentFilter(BtSyncService.ACTION_SYNCED));
        face.invalidate();
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, TICK_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
        try {
            unregisterReceiver(syncReceiver);
        } catch (Throwable ignored) {
            // 没注册成功过
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                return true;
            case MotionEvent.ACTION_UP:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (Math.abs(dx) > getResources().getDisplayMetrics().widthPixels * 0.12f
                        && Math.abs(dx) > Math.abs(dy)) {
                    // 左滑看后一天，右滑看前一天
                    shiftDay(dx < 0 ? 1 : -1);
                } else if (Math.abs(dx) < 20 && Math.abs(dy) < 20 && dayOffset != 0) {
                    // 轻点回到今天
                    dayOffset = 0;
                    face.invalidate();
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void shiftDay(int delta) {
        int next = Math.max(-7, Math.min(14, dayOffset + delta));
        if (next != dayOffset) {
            dayOffset = next;
            face.invalidate();
        }
    }

    /** 自绘的一屏：日期头 + 当天所有课。 */
    private class FaceView extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bar = new RectF();

        FaceView() {
            super(WearActivity.this);
            paint.setTypeface(Typeface.DEFAULT);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(COLOR_BG);
            float w = getWidth();
            float h = getHeight();
            float pad = w * 0.08f;

            if (data == null || !data.configured()) {
                drawCentered(canvas, "还没有课表数据", h * 0.30f, w * 0.10f, COLOR_TEXT);
                drawCentered(canvas, "手机：我的 → 备份与恢复", h * 0.47f, w * 0.048f, COLOR_MUTED);
                drawCentered(canvas, "→ 蓝牙发送到手表", h * 0.56f, w * 0.048f, COLOR_MUTED);
                drawCentered(canvas, "也可以用 adb push", h * 0.67f, w * 0.048f, COLOR_MUTED);
                drawCentered(canvas, "点屏幕重新读取", h * 0.82f, w * 0.044f, COLOR_MUTED);
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDate today = now.toLocalDate();
            LocalDate date = today.plusDays(dayOffset);
            LocalTime nowTime = now.toLocalTime();

            // 最顶部：正在上什么 / 马上要上什么 —— 抬腕一眼就要看到的信息
            List<WatchData.Occurrence> todays = data.occurrences(today);
            WatchData.Occurrence current = null;
            WatchData.Occurrence nextToday = null;
            for (WatchData.Occurrence item : todays) {
                if (item.ongoing(nowTime)) {
                    current = item;
                } else if (nextToday == null && item.start.isAfter(nowTime)) {
                    nextToday = item;
                }
            }
            LocalDate nextDate = today;
            WatchData.Occurrence next = nextToday;
            if (next == null) {
                for (int offset = 1; offset <= 7 && next == null; offset++) {
                    List<WatchData.Occurrence> later = data.occurrences(today.plusDays(offset));
                    if (!later.isEmpty()) {
                        next = later.get(0);
                        nextDate = today.plusDays(offset);
                    }
                }
            }
            // 标题：一眼知道这是哪个应用
            drawCentered(canvas, "QUT课表", pad + h * 0.030f, w * 0.050f, COLOR_TITLE);

            drawCentered(canvas, statusText(current, next, nextDate, nowTime),
                    pad + h * 0.088f, w * 0.060f, COLOR_ACCENT);

            // 日期 + 周几 + 第几周：一行写完，给列表腾地方
            String head = relativeLabel() + " · " + MD.format(date) + " 周"
                    + WEEK_CN[date.getDayOfWeek().getValue() - 1];
            int week = data.weekOf(date);
            if (week >= 1) {
                head += " · 第 " + week + " 周";
            }
            drawCentered(canvas, head, pad + h * 0.145f, w * 0.046f, COLOR_MUTED);

            List<WatchData.Occurrence> items = data.occurrences(date);
            if (items.isEmpty()) {
                drawCentered(canvas, "这天没有课", h * 0.54f, w * 0.100f, COLOR_MUTED);
                drawCentered(canvas, "左右滑动看别的日期", h * 0.66f, w * 0.050f, COLOR_MUTED);
                return;
            }

            float top = pad + h * 0.195f;
            float bottom = h - pad;
            float rowH = Math.min(h * 0.190f, (bottom - top) / items.size());
            boolean isToday = dayOffset == 0;
            for (int i = 0; i < items.size(); i++) {
                WatchData.Occurrence item = items.get(i);
                boolean ongoing = isToday && item.ongoing(nowTime);
                boolean upcoming = isToday && !ongoing && item.start.isAfter(nowTime);
                drawRow(canvas, item, top + rowH * i, rowH, w, pad,
                        ongoing, ongoing ? nowTime : null, upcoming);
            }
        }

        /** 顶部那行：正在上的课优先，其次是最近一节要上的课。 */
        private String statusText(WatchData.Occurrence current, WatchData.Occurrence next,
                                  LocalDate nextDate, LocalTime now) {
            if (current != null) {
                long left = Math.max(0, Duration.between(now, current.end).toMinutes());
                return "正在上 · " + current.course.name + " · 还剩 " + left + " 分";
            }
            if (next == null) {
                return "最近没有课";
            }
            String when;
            if (nextDate.equals(LocalDate.now())) {
                long minutes = Duration.between(now, next.start).toMinutes();
                when = minutes <= 0 ? "马上" : minutes + " 分后";
            } else {
                when = MD.format(nextDate) + " " + HM.format(next.start);
            }
            return "下一节 · " + when + " · " + next.course.name;
        }

        private String relativeLabel() {
            if (dayOffset == 0) {
                return "今天";
            }
            if (dayOffset == 1) {
                return "明天";
            }
            if (dayOffset == -1) {
                return "昨天";
            }
            return dayOffset > 0 ? (dayOffset + " 天后") : (-dayOffset + " 天前");
        }

        /** 一行课：课名、时间地点；正在上的那行加底色、进度条与剩余时间。 */
        private void drawRow(Canvas canvas, WatchData.Occurrence item, float top, float rowH,
                             float w, float pad, boolean ongoing, LocalTime now, boolean upcoming) {
            float left = pad;
            float right = w - pad;

            if (ongoing) {
                bar.set(left, top + rowH * 0.06f, right, top + rowH * 0.94f);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(COLOR_CURRENT_BG);
                canvas.drawRoundRect(bar, rowH * 0.18f, rowH * 0.18f, paint);
            }

            float textLeft = left + rowH * 0.16f;
            float textRight = right - rowH * 0.05f;

            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setColor(ongoing ? COLOR_ACCENT : (upcoming ? COLOR_TEXT : COLOR_MUTED));
            float nameSize = rowH * 0.38f;
            paint.setTextSize(nameSize);
            String name = item.course.name;
            while (nameSize > rowH * 0.22f && paint.measureText(name) > textRight - textLeft) {
                nameSize -= rowH * 0.02f;
                paint.setTextSize(nameSize);
            }
            canvas.drawText(name, textLeft, top + rowH * 0.40f, paint);

            paint.setTextSize(rowH * 0.26f);
            paint.setColor(COLOR_MUTED);
            String sub = HM.format(item.start) + " - " + HM.format(item.end);
            if (item.course.location != null && !item.course.location.isEmpty()) {
                sub += " · " + item.course.location;
            }
            // 课名与时间之间多留一点，两行不要贴在一起
            canvas.drawText(sub, textLeft, top + rowH * 0.74f, paint);

            if (ongoing && now != null) {
                long total = Math.max(1, Duration.between(item.start, item.end).toMinutes());
                long passed = Math.max(0, Duration.between(item.start, now).toMinutes());
                float ratio = Math.min(1f, passed / (float) total);
                float y = top + rowH * 0.88f;
                bar.set(textLeft, y, textRight, y + rowH * 0.06f);
                paint.setColor(COLOR_TRACK);
                canvas.drawRoundRect(bar, bar.height() / 2, bar.height() / 2, paint);
                bar.right = textLeft + (textRight - textLeft) * ratio;
                paint.setColor(COLOR_ACCENT);
                canvas.drawRoundRect(bar, bar.height() / 2, bar.height() / 2, paint);

                long minutesLeft = Math.max(0, Duration.between(now, item.end).toMinutes());
                paint.setTextAlign(Paint.Align.RIGHT);
                paint.setTextSize(rowH * 0.23f);
                paint.setColor(COLOR_ACCENT);
                canvas.drawText("还剩 " + minutesLeft + " 分",
                        textRight, top + rowH * 0.42f, paint);
                paint.setTextAlign(Paint.Align.LEFT);
            }
        }

        /** 居中写一行，字号过大时自动缩小，避免长课名被裁掉。 */
        private void drawCentered(Canvas canvas, String text, float y, float size, int color) {
            if (text == null || text.isEmpty()) {
                return;
            }
            float maxWidth = getWidth() * 0.88f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            paint.setTextAlign(Paint.Align.CENTER);
            float current = size;
            paint.setTextSize(current);
            while (current > size * 0.55f && paint.measureText(text) > maxWidth) {
                current -= size * 0.05f;
                paint.setTextSize(current);
            }
            canvas.drawText(text, getWidth() / 2f, y, paint);
        }
    }
}
