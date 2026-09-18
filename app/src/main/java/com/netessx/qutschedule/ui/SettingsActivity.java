package com.netessx.qutschedule.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputLayout;
import com.netessx.qutschedule.R;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.model.Prefs;
import com.netessx.qutschedule.model.Semester;
import com.netessx.qutschedule.reminder.ReminderScheduler;
import com.netessx.qutschedule.util.Dates;

import java.time.LocalDate;

/** 学期设置：开学日期决定周次推算，另含课表显示选项。 */
public class SettingsActivity extends BaseActivity {

    private ScheduleStore store;
    private Semester semester;
    private Prefs prefs;
    private TextInputLayout nameInput;
    private TextInputLayout weeksInput;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = ScheduleStore.get(this);
        semester = store.currentSemester();
        prefs = store.data().prefs;

        LinearLayout column = SettingsUi.column(this);
        LocalDate today = LocalDate.now();

        SettingsUi.section(this, column, getString(R.string.mine_semester));
        column.addView(SettingsUi.label(this, getString(R.string.settings_term_name)));
        nameInput = SettingsUi.textRow(this, getString(R.string.settings_term_name), semester.name);
        column.addView(nameInput);

        column.addView(SettingsUi.label(this, getString(R.string.settings_term_start)));
        column.addView(SettingsUi.buttonRow(this, termStartText(), v -> pickStartDate()));

        column.addView(SettingsUi.label(this, getString(R.string.settings_total_weeks)));
        weeksInput = SettingsUi.numberRow(this, getString(R.string.settings_total_weeks),
                String.valueOf(semester.totalWeeks));
        column.addView(weeksInput);

        SettingsUi.section(this, column, getString(R.string.mine_group_pref));
        column.addView(SettingsUi.switchRow(this, "显示周末", prefs.showWeekend,
                (button, checked) -> prefs.showWeekend = checked));
        column.addView(SettingsUi.switchRow(this, "显示非本周课程", prefs.showNonCurrentWeek,
                (button, checked) -> prefs.showNonCurrentWeek = checked));

        setPageTitle(getString(R.string.mine_semester));
        setPageContent(SettingsUi.scrollWrap(this, column));
    }

    private String termStartText() {
        LocalDate start = semester.startMonday();
        if (start == null) {
            return getString(R.string.settings_term_start) + "（未设置）";
        }
        return start.format(Dates.YMD) + "（周" + Dates.weekName(start) + "）";
    }

    private void pickStartDate() {
        LocalDate today = LocalDate.now();
        LocalDate current = semester.startMonday();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            LocalDate picked = LocalDate.of(year, month + 1, dayOfMonth);
            LocalDate monday = Dates.mondayOf(picked);
            if (!monday.equals(picked)) {
                Toast.makeText(this, "已对齐到该周的周一", Toast.LENGTH_SHORT).show();
            }
            semester.startDate = monday.toString();
            recreate();
        }, current == null ? today.getYear() : current.getYear(),
                (current == null ? today.getMonthValue() : current.getMonthValue()) - 1,
                current == null ? today.getDayOfMonth() : current.getDayOfMonth()).show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nameInput != null) {
            semester.name = SettingsUi.text(nameInput);
        }
        if (weeksInput != null) {
            try {
                semester.totalWeeks = Math.max(1, Integer.parseInt(SettingsUi.text(weeksInput)));
            } catch (NumberFormatException ignored) {
                // 输入不是数字时保持原值
            }
        }
        semester.normalize();
        prefs.normalize();
        store.save();
        ReminderScheduler.sync(this);
    }
}
