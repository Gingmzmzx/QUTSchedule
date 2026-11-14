package com.netessx.qutschedule.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.netessx.qutschedule.R;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.util.CrashLog;
import com.netessx.qutschedule.util.Trace;

/** 关于：版本与数据说明。 */
public class AboutActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout column = SettingsUi.column(this);

        // 顶部品牌区：图标 + 名称 + 版本
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(0, SettingsUi.dp(this, 20), 0, SettingsUi.dp(this, 22));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher_round);
        hero.addView(icon, new LinearLayout.LayoutParams(
                SettingsUi.dp(this, 76), SettingsUi.dp(this, 76)));
        TextView name = new TextView(this);
        name.setText(R.string.app_name);
        name.setTextSize(22);
        name.setTypeface(null, Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, SettingsUi.dp(this, 12), 0, 0);
        hero.addView(name);
        TextView version = new TextView(this);
        version.setText(getString(R.string.about_version_fmt, versionName(), versionCode()));
        version.setTextSize(13);
        version.setTextColor(getColor(R.color.colorOnSurfaceVariant));
        version.setGravity(Gravity.CENTER);
        version.setPadding(0, SettingsUi.dp(this, 4), 0, 0);
        hero.addView(version);
        column.addView(hero);

        column.addView(SettingsUi.label(this, getString(R.string.more_about_text)));

        LinearLayout card = SettingsUi.card(this, column);
        card.addView(SettingsUi.buttonRow(this, getString(R.string.about_check_update),
                v -> {
                    UpdatePrompt.checkManual(this);
                    // 手动检查时顺带看一眼公告，有新公告就一起弹
                    NoticePrompt.check(this);
                }));
        card.addView(SettingsUi.switchRow(this, getString(R.string.about_auto_check_update),
                ScheduleStore.get(this).data().prefs.autoCheckUpdate,
                (button, checked) -> {
                    ScheduleStore.get(this).data().prefs.autoCheckUpdate = checked;
                    ScheduleStore.get(this).save();
                }));
        card.addView(SettingsUi.buttonRow(this, getString(R.string.about_replay_onboarding),
                v -> startActivity(OnboardingActivity.newIntent(this))));
        // 崩溃堆栈 + 运行轨迹：有才显示，没有就不占地方
        if (CrashLog.exists(this) || Trace.exists(this)) {
            card.addView(SettingsUi.buttonRow(this, getString(R.string.about_crash_log),
                    v -> showCrashLog()));
        }
        setPageTitle(getString(R.string.mine_about));
        setPageContent(SettingsUi.scrollWrap(this, column));
    }

    /** 分享或删除诊断信息：崩溃堆栈 + 运行轨迹。 */
    private void showCrashLog() {
        String crash = CrashLog.read(this);
        String trace = Trace.read(this);
        StringBuilder all = new StringBuilder();
        if (!crash.isEmpty()) {
            all.append("===== 崩溃堆栈 =====\n").append(crash).append("\n\n");
        }
        if (!trace.isEmpty()) {
            all.append("===== 运行轨迹 =====\n").append(trace);
        }
        final String log = all.toString();
        if (log.isEmpty()) {
            Toast.makeText(this, R.string.about_crash_log_none, Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.about_crash_log)
                .setMessage(getString(R.string.about_crash_log_hint))
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.about_crash_log_clear, (dialog, which) -> {
                    CrashLog.clear(this);
                    Trace.clear(this);
                    recreate();
                })
                .setPositiveButton(R.string.about_crash_log_share, (dialog, which) -> {
                    Intent send = new Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
                            .putExtra(Intent.EXTRA_TEXT, log);
                    try {
                        startActivity(Intent.createChooser(send,
                                getString(R.string.about_crash_log_share)));
                    } catch (RuntimeException e) {
                        Toast.makeText(this, R.string.update_no_browser, Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private String versionName() {
        android.content.pm.PackageInfo info = packageInfo();
        return info == null || info.versionName == null ? "" : info.versionName;
    }

    /** versionCode 在 API 28 起是 long，低版本只能读已废弃的 int 字段。 */
    @SuppressWarnings("deprecation")
    private String versionCode() {
        android.content.pm.PackageInfo info = packageInfo();
        if (info == null) {
            return "";
        }
        long code = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P
                ? info.getLongVersionCode()
                : info.versionCode;
        return String.valueOf(code);
    }

    private android.content.pm.PackageInfo packageInfo() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
