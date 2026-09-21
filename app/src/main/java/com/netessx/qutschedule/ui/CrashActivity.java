package com.netessx.qutschedule.ui;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.netessx.qutschedule.MainActivity;
import com.netessx.qutschedule.R;
import com.netessx.qutschedule.util.CrashLog;

/**
 * 崩溃界面：闪退后由 {@link CrashLog} 用闹钟拉起。
 *
 * <p>刻意不碰 {@code ScheduleStore} 等应用内部状态 —— 崩溃的原因可能就在那里，
 * 这里只用最基础的控件，保证「一启动就崩」时这个页面仍然打得开。
 */
public class CrashActivity extends AppCompatActivity {

    public static final String EXTRA_LOG = "crash_log";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String log = getIntent().getStringExtra(EXTRA_LOG);
        if (log == null || log.isEmpty()) {
            log = CrashLog.read(this);
        }
        final String report = log;
        // 已经看到日志了，下次启动正常进应用；日志本身留着，等用户导出或删除
        CrashLog.clearPending(this);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        column.setPadding(pad, pad, pad, pad);

        column.addView(title(getString(R.string.crash_title)));
        column.addView(body(getString(R.string.crash_hint)));

        column.addView(button(getString(R.string.crash_export), v -> share(report)));
        column.addView(button(getString(R.string.crash_restart), v -> restart()));
        column.addView(button(getString(R.string.crash_clear), v -> confirmClearData()));
        column.addView(button(getString(R.string.crash_close), v -> finish()));

        TextView logView = body(report.isEmpty()
                ? getString(R.string.crash_log_empty) : report);
        logView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        logView.setTextIsSelectable(true);
        logView.setPadding(0, dp(16), 0, 0);
        column.addView(logView);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(column);
        setContentView(scroll);
        setTitle(R.string.crash_title);
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private TextView title(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView body(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        view.setLineSpacing(0, 1.2f);
        return view;
    }

    private Button button(String text, android.view.View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(10);
        button.setLayoutParams(params);
        return button;
    }

    private void share(String report) {
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
                .putExtra(Intent.EXTRA_TEXT, report.isEmpty()
                        ? getString(R.string.crash_log_empty) : report);
        try {
            startActivity(Intent.createChooser(send, getString(R.string.crash_export)));
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.update_no_browser, Toast.LENGTH_LONG).show();
        }
    }

    private void restart() {
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }

    private void confirmClearData() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.crash_clear)
                .setMessage(R.string.crash_clear_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> clearData())
                .show();
    }

    /** 清数据会立刻结束进程，所以先把重启安排好，再执行清除。 */
    private void clearData() {
        scheduleRestart();
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null || !manager.clearApplicationUserData()) {
            Toast.makeText(this, R.string.crash_clear_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void scheduleRestart() {
        try {
            PendingIntent pending = PendingIntent.getActivity(this, 0,
                    new Intent(this, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager alarms = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarms != null) {
                alarms.setExact(AlarmManager.RTC, System.currentTimeMillis() + 800, pending);
            }
        } catch (RuntimeException e) {
            // 安排不上就由用户手动再打开一次
        }
    }
}
