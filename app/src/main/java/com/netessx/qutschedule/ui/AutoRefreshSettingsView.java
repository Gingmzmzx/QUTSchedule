package com.netessx.qutschedule.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;
import com.netessx.qutschedule.R;
import com.netessx.qutschedule.model.Prefs;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 自动刷新设置面板：引导页第 4 步和「我的 → 自动刷新课表」共用。
 *
 * <p>只负责把设置写回 {@link Prefs}，保存与重排闹钟由宿主通过 {@link Listener} 完成。
 */
public class AutoRefreshSettingsView extends LinearLayout {

    /** 宿主回调。 */
    public interface Listener {
        /** 任一设置项发生变化，宿主应保存并重排闹钟。 */
        void onChanged();

        /** 用户点了「立即更新」。 */
        void onRefreshNow();
    }

    private final MaterialSwitch enabledSwitch;
    private final MaterialButtonToggleGroup modeGroup;
    private final MaterialButton dailyButton;
    private final MaterialButton weeklyButton;
    private final MaterialButton timeButton;
    private final LinearLayout weekdayBox;
    private final TextInputLayout weekdayField;
    private final TextView statusView;
    private final MaterialButton refreshNowButton;
    private final TextView hintView;

    private Prefs prefs;
    private Listener listener;
    private boolean hasCapture;
    private boolean binding;

    public AutoRefreshSettingsView(Context context) {
        this(context, null);
    }

    public AutoRefreshSettingsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);

        enabledSwitch = SettingsUi.switchRow(context,
                context.getString(R.string.auto_refresh_enable), false, (button, checked) -> {
                    if (binding || prefs == null) {
                        return;
                    }
                    prefs.autoRefreshEnabled = checked;
                    notifyChanged();
                });
        addView(enabledSwitch);

        hintView = SettingsUi.label(context, context.getString(R.string.auto_refresh_need_capture));
        addView(hintView);

        addView(SettingsUi.label(context, context.getString(R.string.auto_refresh_mode)));
        modeGroup = new MaterialButtonToggleGroup(context);
        modeGroup.setSingleSelection(true);
        modeGroup.setSelectionRequired(true);
        dailyButton = outlined(context, R.string.auto_refresh_daily);
        weeklyButton = outlined(context, R.string.auto_refresh_weekly);
        modeGroup.addView(dailyButton, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        modeGroup.addView(weeklyButton, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        modeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || binding || prefs == null) {
                return;
            }
            prefs.autoRefreshMode = checkedId == weeklyButton.getId()
                    ? Prefs.REFRESH_WEEKLY : Prefs.REFRESH_DAILY;
            applyState();
            notifyChanged();
        });
        addView(modeGroup);

        timeButton = SettingsUi.buttonRow(context, "", v -> pickTime());
        addView(timeButton);

        weekdayBox = new LinearLayout(context);
        weekdayBox.setOrientation(VERTICAL);
        weekdayBox.addView(SettingsUi.label(context, context.getString(R.string.auto_refresh_weekday)));
        List<String> weekdays = Arrays.asList(
                context.getResources().getStringArray(R.array.auto_refresh_weekdays));
        weekdayField = SettingsUi.dropdown(context, context.getString(R.string.auto_refresh_weekday_hint),
                weekdays, 0, position -> {
                    if (binding || prefs == null) {
                        return;
                    }
                    prefs.autoRefreshWeekday = position + 1;
                    notifyChanged();
                });
        weekdayBox.addView(weekdayField);
        addView(weekdayBox);

        statusView = SettingsUi.label(context, "");
        addView(statusView);

        refreshNowButton = SettingsUi.buttonRow(context,
                context.getString(R.string.auto_refresh_now), v -> {
                    if (listener != null) {
                        listener.onRefreshNow();
                    }
                });
        addView(refreshNowButton);

        addView(SettingsUi.label(context, context.getString(R.string.auto_refresh_note)));
    }

    private MaterialButton outlined(Context context, int textRes) {
        MaterialButton button = new MaterialButton(context, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(textRes);
        button.setAllCaps(false);
        button.setId(View.generateViewId());
        return button;
    }

    /** 绑定当前设置；{@code hasCapture} 为 false 时整块面板置灰。 */
    public void bind(Prefs prefs, boolean hasCapture, Listener listener) {
        this.prefs = prefs;
        this.hasCapture = hasCapture;
        this.listener = listener;
        binding = true;
        enabledSwitch.setChecked(prefs.autoRefreshEnabled);
        modeGroup.check(Prefs.REFRESH_WEEKLY.equals(prefs.autoRefreshMode)
                ? weeklyButton.getId() : dailyButton.getId());
        int weekday = Math.max(0, Math.min(prefs.autoRefreshWeekday - 1, 6));
        MaterialAutoCompleteTextView weekdayInput =
                (MaterialAutoCompleteTextView) weekdayField.getEditText();
        if (weekdayInput != null) {
            String[] names = getResources().getStringArray(R.array.auto_refresh_weekdays);
            weekdayInput.setText(names[weekday], false);
        }
        updateTimeText();
        refreshStatus();
        binding = false;
        applyState();
    }

    /** 上次自动刷新的结果，页面回到前台时刷新一下。 */
    public void refreshStatus() {
        if (prefs == null) {
            return;
        }
        if (prefs.autoRefreshLastAt <= 0) {
            statusView.setText(R.string.auto_refresh_last_never);
            return;
        }
        String time = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.getDefault())
                .format(Instant.ofEpochMilli(prefs.autoRefreshLastAt)
                        .atZone(ZoneId.systemDefault()));
        boolean failed = Prefs.REFRESH_FAILED.equals(prefs.autoRefreshLastResult);
        statusView.setText(getContext().getString(
                failed ? R.string.auto_refresh_last_failed_fmt : R.string.auto_refresh_last_fmt, time));
    }

    private void applyState() {
        boolean ready = hasCapture;
        enabledSwitch.setEnabled(ready);
        dailyButton.setEnabled(ready);
        weeklyButton.setEnabled(ready);
        timeButton.setEnabled(ready);
        weekdayField.setEnabled(ready);
        refreshNowButton.setEnabled(ready);
        hintView.setVisibility(ready ? GONE : VISIBLE);
        boolean weekly = prefs != null && Prefs.REFRESH_WEEKLY.equals(prefs.autoRefreshMode);
        weekdayBox.setVisibility(weekly ? VISIBLE : GONE);
    }

    private void updateTimeText() {
        if (prefs == null) {
            return;
        }
        timeButton.setText(getContext().getString(R.string.auto_refresh_time_fmt,
                String.format(Locale.ROOT, "%02d:%02d", prefs.autoRefreshHour, prefs.autoRefreshMinute)));
    }

    private void pickTime() {
        if (prefs == null) {
            return;
        }
        android.app.TimePickerDialog dialog = new android.app.TimePickerDialog(getContext(),
                (view, hour, minute) -> {
                    prefs.autoRefreshHour = hour;
                    prefs.autoRefreshMinute = minute;
                    updateTimeText();
                    notifyChanged();
                }, prefs.autoRefreshHour, prefs.autoRefreshMinute, true);
        dialog.show();
    }

    private void notifyChanged() {
        if (prefs != null) {
            prefs.normalize();
        }
        if (listener != null) {
            listener.onChanged();
        }
    }
}
