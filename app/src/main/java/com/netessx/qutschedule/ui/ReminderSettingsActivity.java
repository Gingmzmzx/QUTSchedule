package com.netessx.qutschedule.ui;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;

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

        setPageContent(SettingsUi.scrollWrap(this, column));
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
