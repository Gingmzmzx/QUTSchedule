package com.netessx.qutschedule.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.netessx.qutschedule.R;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.model.AppData;
import com.netessx.qutschedule.model.TimeScheme;
import com.netessx.qutschedule.reminder.ReminderScheduler;
import com.netessx.qutschedule.util.TimeSlots;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 自定义时间段：多套作息方案、按日期自动切换、改结束时间后后节自动顺延。 */
public class SlotSchemeActivity extends BaseActivity {

    private AppData data;
    private LinearLayout slotBox;
    private TextView rangeLabel;
    private EditText nameInput;
    private TimeScheme scheme;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        data = ScheduleStore.get(this).data();
        scheme = data.schemeFor(data.currentSemester(), LocalDate.now());
        build();
    }

    private void build() {
        LinearLayout column = SettingsUi.column(this);

        SettingsUi.section(this, column, getString(R.string.mine_slots));
        List<String> names = new ArrayList<>();
        for (TimeScheme item : data.schemes) {
            names.add(item.name == null || item.name.isEmpty() ? item.id : item.name);
        }
        column.addView(SettingsUi.dropdown(this, getString(R.string.mine_slots), names,
                Math.max(0, data.schemes.indexOf(scheme)), position -> {
                    scheme = data.schemes.get(position);
                    data.currentSchemeId = scheme.id;
                    if (nameInput != null) {
                        nameInput.setText(scheme.name);
                    }
                    rebuildSlots();
                    updateRangeLabel();
                }));

        LinearLayout schemeButtons = new LinearLayout(this);
        schemeButtons.setOrientation(LinearLayout.HORIZONTAL);
        schemeButtons.addView(SettingsUi.buttonRow(this, getString(R.string.slot_add_scheme),
                v -> addScheme()), weight());
        schemeButtons.addView(SettingsUi.buttonRow(this, getString(R.string.delete),
                v -> removeScheme()), weight());
        column.addView(schemeButtons);

        column.addView(SettingsUi.label(this, getString(R.string.slot_name)));
        nameInput = SettingsUi.textRow(this, getString(R.string.slot_name), scheme.name);
        column.addView(nameInput);

        column.addView(SettingsUi.label(this, getString(R.string.slot_range)));
        rangeLabel = SettingsUi.label(this, "");
        column.addView(rangeLabel);
        LinearLayout rangeButtons = new LinearLayout(this);
        rangeButtons.setOrientation(LinearLayout.HORIZONTAL);
        rangeButtons.addView(SettingsUi.buttonRow(this, "起点", v -> pickRange(true)), weight());
        rangeButtons.addView(SettingsUi.buttonRow(this, "终点", v -> pickRange(false)), weight());
        rangeButtons.addView(SettingsUi.buttonRow(this, "清除", v -> {
            scheme.fromMonthDay = null;
            scheme.toMonthDay = null;
            updateRangeLabel();
        }), weight());
        column.addView(rangeButtons);
        column.addView(SettingsUi.label(this, getString(R.string.slot_range_hint)));
        column.addView(SettingsUi.label(this, getString(R.string.slot_shift_hint)));

        slotBox = new LinearLayout(this);
        slotBox.setOrientation(LinearLayout.VERTICAL);
        column.addView(slotBox);

        LinearLayout slotButtons = new LinearLayout(this);
        slotButtons.setOrientation(LinearLayout.HORIZONTAL);
        slotButtons.addView(SettingsUi.buttonRow(this, getString(R.string.slot_add_row), v -> {
            scheme.slots.add(new TimeScheme.Slot("08:00", "09:40"));
            rebuildSlots();
        }), weight());
        slotButtons.addView(SettingsUi.buttonRow(this, getString(R.string.slot_remove_row), v -> {
            if (scheme.slots.size() > 1) {
                scheme.slots.remove(scheme.slots.size() - 1);
                rebuildSlots();
            }
        }), weight());
        column.addView(slotButtons);

        setPageTitle(getString(R.string.mine_slots));
        setPageContent(SettingsUi.scrollWrap(this, column));
        rebuildSlots();
        updateRangeLabel();
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private void rebuildSlots() {
        slotBox.removeAllViews();
        for (int i = 0; i < scheme.slots.size(); i++) {
            final int index = i;
            final TimeScheme.Slot slot = scheme.slots.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);

            TextView label = new TextView(this);
            label.setText((i * 2 + 1) + "-" + (i * 2 + 2) + " 节");
            label.setWidth(SettingsUi.dp(this, 64));
            row.addView(label);

            row.addView(SettingsUi.buttonRow(this, slot.start,
                    v -> pickTime(index, true, (Button) v)), weight());
            row.addView(SettingsUi.buttonRow(this, slot.end,
                    v -> pickTime(index, false, (Button) v)), weight());
            slotBox.addView(row);
        }
    }

    private void pickTime(final int index, final boolean isStart, final Button target) {
        final TimeScheme.Slot slot = scheme.slots.get(index);
        int minutes = TimeSlots.minutes(target.getText().toString());
        if (minutes < 0) {
            minutes = 8 * 60;
        }
        new TimePickerDialog(this, (view, hour, minute) -> {
            String value = TimeSlots.format(hour * 60 + minute);
            if (isStart) {
                slot.start = value;
                target.setText(value);
                return;
            }
            int before = TimeSlots.minutes(slot.end);
            slot.end = value;
            target.setText(value);
            // 结束时间变了，后面的节次整体顺延
            int shifted = scheme.shiftAfter(index, TimeSlots.minutes(value) - before);
            if (shifted != 0) {
                Toast.makeText(this,
                        getString(R.string.slot_shifted_fmt, scheme.slots.size() - index - 1),
                        Toast.LENGTH_SHORT).show();
                rebuildSlots();
            }
        }, minutes / 60, minutes % 60, true).show();
    }

    private void pickRange(final boolean isStart) {
        LocalDate today = LocalDate.now();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            String value = String.format(Locale.CHINA, "%02d-%02d", month + 1, dayOfMonth);
            if (isStart) {
                scheme.fromMonthDay = value;
            } else {
                scheme.toMonthDay = value;
            }
            updateRangeLabel();
        }, today.getYear(), today.getMonthValue() - 1, today.getDayOfMonth()).show();
    }

    private void updateRangeLabel() {
        if (rangeLabel == null) {
            return;
        }
        boolean empty = scheme.fromMonthDay == null || scheme.toMonthDay == null;
        rangeLabel.setText(empty
                ? "未设置（始终生效）"
                : scheme.fromMonthDay + " ~ " + scheme.toMonthDay);
    }

    private void addScheme() {
        TimeScheme created = new TimeScheme();
        created.name = getString(R.string.slot_add_scheme);
        created.slots.addAll(TimeScheme.defaults().slots);
        data.schemes.add(created);
        data.currentSchemeId = created.id;
        scheme = created;
        build();
    }

    private void removeScheme() {
        if (data.schemes.size() <= 1) {
            Toast.makeText(this, R.string.semester_delete_last, Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete)
                .setMessage(getString(R.string.confirm_delete, scheme.name))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    data.schemes.remove(scheme);
                    scheme = data.schemes.get(0);
                    data.currentSchemeId = scheme.id;
                    build();
                })
                .show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nameInput != null) {
            scheme.name = nameInput.getText().toString().trim();
        }
        scheme.normalize();
        ScheduleStore.get(this).save();
        ReminderScheduler.sync(this);
    }

}
