package com.netessx.qutschedule.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.netessx.qutschedule.R;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.live.LiveUpdateService;
import com.netessx.qutschedule.model.Prefs;
import com.netessx.qutschedule.reminder.DndController;
import com.netessx.qutschedule.reminder.ReminderScheduler;

import java.util.Arrays;

/** 课程提醒设置：提前量、免打扰、节假日、状态栏灵动岛。 */
public class ReminderSettingsActivity extends BaseActivity {

    private Prefs prefs;
    private TextInputLayout leadInput;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = ScheduleStore.get(this).data().prefs;

        LinearLayout column = SettingsUi.column(this);
        setPageTitle(getString(R.string.mine_reminder));

        SettingsUi.section(this, column, getString(R.string.mine_reminder));
        LinearLayout basic = SettingsUi.card(this, column);
        basic.addView(SettingsUi.switchRow(this, getString(R.string.settings_reminder),
                prefs.reminderEnabled, (button, checked) -> prefs.reminderEnabled = checked));
        // 输入框自带的 hint 就是标题，不用再加一行 label
        leadInput = SettingsUi.numberRow(this, getString(R.string.reminder_lead),
                String.valueOf(prefs.defaultReminderMinutes));
        basic.addView(leadInput);

        SettingsUi.section(this, column, getString(R.string.reminder_dnd));
        LinearLayout dnd = SettingsUi.card(this, column);
        dnd.addView(SettingsUi.switchRow(this, getString(R.string.reminder_dnd),
                prefs.dndEnabled, (button, checked) -> prefs.dndEnabled = checked));
        dnd.addView(SettingsUi.dropdown(this, getString(R.string.reminder_dnd_mode),
                Arrays.asList(getString(R.string.reminder_dnd_silent),
                        getString(R.string.reminder_dnd_full)),
                Prefs.DND_MODE.equals(prefs.dndMode) ? 1 : 0,
                position -> prefs.dndMode = position == 1 ? Prefs.DND_MODE : Prefs.DND_SILENT));
        if (!DndController.hasPolicyAccess(this)) {
            dnd.addView(SettingsUi.label(this, getString(R.string.reminder_dnd_permission)));
        }

        SettingsUi.section(this, column, getString(R.string.reminder_holiday));
        LinearLayout holiday = SettingsUi.card(this, column);
        holiday.addView(SettingsUi.switchRow(this, getString(R.string.reminder_holiday),
                prefs.holidaySkip, (button, checked) -> prefs.holidaySkip = checked));
        holiday.addView(SettingsUi.label(this, getString(R.string.reminder_holiday_hint)));

        SettingsUi.section(this, column, getString(R.string.reminder_live));
        LinearLayout live = SettingsUi.card(this, column);
        live.addView(SettingsUi.switchRow(this, getString(R.string.reminder_live),
                prefs.liveUpdateEnabled, (button, checked) -> prefs.liveUpdateEnabled = checked));
        live.addView(SettingsUi.label(this, getString(R.string.reminder_live_hint)));
        live.addView(SettingsUi.buttonRow(this, getString(R.string.reminder_keep_alive),
                v -> showKeepAliveOptions()));

        setPageContent(SettingsUi.scrollWrap(this, column));
    }

    /**
     * 直达厂商的保活开关。
     *
     * <p>从最近任务划掉应用，在小米这类 ROM 上等于「强制停止」：进程被杀，已排的闹钟也会被撤销，
     * 提醒和状态栏一起失效。这是系统行为，代码绕不过去，只能引导用户去开自启动与省电白名单。
     */
    private void showKeepAliveOptions() {
        String[] items = {
                getString(R.string.keep_alive_autostart),
                getString(R.string.keep_alive_battery),
                getString(R.string.keep_alive_app_info),
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reminder_keep_alive)
                .setMessage(R.string.reminder_keep_alive_hint)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        // 小米的自启动管理页；别的 ROM 没有这个组件，会退回应用信息页
                        openSafely(new Intent().setComponent(new ComponentName(
                                "com.miui.securitycenter",
                                "com.miui.permcenter.autostart.AutoStartManagementActivity")));
                    } else if (which == 1) {
                        openSafely(new Intent(
                                android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                    } else {
                        openAppInfo();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** 目标页面打不开就退到应用信息页 —— 各家 ROM 的设置项名字与位置都不一样。 */
    private void openSafely(Intent intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (RuntimeException e) {
            openAppInfo();
        }
    }

    private void openAppInfo() {
        try {
            startActivity(new Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            Toast.makeText(this, R.string.keep_alive_no_settings, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            prefs.defaultReminderMinutes = Math.max(0,
                    Integer.parseInt(SettingsUi.text(leadInput)));
        } catch (NumberFormatException ignored) {
            // 输入不是数字时保持原值
        }
        prefs.normalize();
        ScheduleStore.get(this).save();
        ReminderScheduler.sync(this);
        LiveUpdateService.refresh(this);
        // 只对「触发时刻还没到」的课生效，不说清楚用户会以为设置没保存上
        Toast.makeText(this, R.string.reminder_lead_saved, Toast.LENGTH_SHORT).show();
    }
}
