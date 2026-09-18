package com.netessx.qutschedule.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.netessx.qutschedule.R;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.model.AppData;
import com.netessx.qutschedule.model.DayRule;
import com.netessx.qutschedule.model.Prefs;
import com.netessx.qutschedule.reminder.ReminderScheduler;
import com.netessx.qutschedule.util.Dates;

import java.time.LocalDate;

/**
 * 放假与调休。
 *
 * <p>不内置任何节假日数据，全部由用户按自己学校的通知添加：
 * 「哪天放假没有课」和「哪天要上另一天的课」。
 */
public class HolidayActivity extends BaseActivity {

    private AppData data;
    private Prefs prefs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        data = ScheduleStore.get(this).data();
        prefs = data.prefs;
        setPageTitle(getString(R.string.mine_holiday));
        build();
    }

    private void build() {
        LinearLayout column = SettingsUi.column(this);

        LinearLayout toggle = SettingsUi.card(this, column);
        toggle.addView(SettingsUi.switchRow(this, getString(R.string.holiday_aware),
                prefs.holidayAware, (button, checked) -> {
                    prefs.holidayAware = checked;
                    save();
                }));
        toggle.addView(SettingsUi.label(this, getString(R.string.holiday_aware_hint)));

        SettingsUi.section(this, column, getString(R.string.mine_holiday));
        for (final DayRule rule : data.dayRules) {
            column.addView(ruleRow(rule));
        }
        if (data.dayRules.isEmpty()) {
            column.addView(SettingsUi.label(this, getString(R.string.holiday_empty)));
        }

        column.addView(SettingsUi.buttonRow(this, getString(R.string.holiday_add_off),
                v -> pickDate(true)));
        column.addView(SettingsUi.buttonRow(this, getString(R.string.holiday_add_swap),
                v -> pickDate(false)));

        setPageContent(SettingsUi.scrollWrap(this, column));
    }

    private TextView ruleRow(final DayRule rule) {
        TextView row = new TextView(this);
        row.setTextSize(15);
        row.setPadding(0, SettingsUi.dp(this, 12), 0, SettingsUi.dp(this, 12));
        row.setText(describe(rule));
        row.setOnClickListener(v -> confirmRemove(rule));
        return row;
    }

    private String describe(DayRule rule) {
        if (rule.isRange()) {
            return getString(R.string.holiday_item_off_range_fmt, rule.date, rule.endDate);
        }
        LocalDate date = Dates.parse(rule.date);
        String weekday = date == null ? "?" : Dates.weekName(date);
        if (!rule.isSwap()) {
            return getString(R.string.holiday_item_off_fmt, rule.date, weekday);
        }
        LocalDate reference = rule.referenceDate();
        String refWeekday = reference == null ? "?" : Dates.weekName(reference);
        return getString(R.string.holiday_item_swap_fmt, rule.date, weekday,
                rule.refDate + " 周" + refWeekday);
    }

    /** 先选日期；补课规则再选一个参照日。 */
    private void pickDate(final boolean off) {
        LocalDate today = LocalDate.now();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            LocalDate picked = LocalDate.of(year, month + 1, dayOfMonth);
            if (off) {
                pickEnd(picked);
            } else {
                pickReference(picked);
            }
        }, today.getYear(), today.getMonthValue() - 1, today.getDayOfMonth()).show();
    }

    /** 放假既可以是单日，也可以是一段区间，避免国庆这种连放七天要录七次。 */
    private void pickEnd(final LocalDate start) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.holiday_off_start))
                .setMessage(start + " 周" + Dates.weekName(start))
                .setPositiveButton(R.string.holiday_single_day, (dialog, which) ->
                        addRule(new DayRule(DayRule.OFF, start.toString(), start.toString())))
                .setNeutralButton(R.string.holiday_pick_end, (dialog, which) -> {
                    LocalDate today = LocalDate.now();
                    new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                        LocalDate end = LocalDate.of(year, month + 1, dayOfMonth);
                        if (end.isBefore(start)) {
                            end = start;
                        }
                        addRule(new DayRule(DayRule.OFF, start.toString(), end.toString()));
                    }, today.getYear(), today.getMonthValue() - 1, today.getDayOfMonth()).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void pickReference(final LocalDate swapDate) {
        LocalDate today = LocalDate.now();
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.holiday_pick_ref))
                .setMessage(getString(R.string.holiday_swap_pick_date) + "：" + swapDate)
                .setPositiveButton(R.string.add, (dialog, which) ->
                        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                            LocalDate reference = LocalDate.of(year, month + 1, dayOfMonth);
                            addRule(DayRule.swap(swapDate.toString(), reference.toString()));
                        }, today.getYear(), today.getMonthValue() - 1, today.getDayOfMonth()).show())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void addRule(DayRule rule) {
        // 同一天只保留一条规则，重复添加视为覆盖
        data.dayRules.removeIf(item -> item.matches(rule.date));
        data.dayRules.add(rule);
        save();
        Toast.makeText(this, R.string.backup_ok, Toast.LENGTH_SHORT).show();
        build();
    }

    private void confirmRemove(final DayRule rule) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.holiday_remove)
                .setMessage(describe(rule))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    data.dayRules.remove(rule);
                    save();
                    build();
                })
                .show();
    }

    private void save() {
        ScheduleStore.get(this).save();
        ReminderScheduler.sync(this);
        setResult(RESULT_OK);
    }
}
