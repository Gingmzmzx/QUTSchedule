package com.netessx.qutschedule.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.google.android.material.card.MaterialCardView;
import com.netessx.qutschedule.R;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.model.AppData;
import com.netessx.qutschedule.model.Semester;
import com.netessx.qutschedule.reminder.ReminderScheduler;
import com.netessx.qutschedule.util.Dates;

import java.time.LocalDate;

/** 管理课表：多学期并排，点击设为当前学期，长按重命名。 */
public class SemesterListActivity extends BaseActivity {

    private AppData data;
    private LinearLayout column;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        data = ScheduleStore.get(this).data();
        build();
    }

    private void build() {
        column = SettingsUi.column(this);
        LocalDate today = LocalDate.now();
        for (final Semester semester : data.semesters) {
            column.addView(card(semester, today));
        }
        column.addView(SettingsUi.buttonRow(this, getString(R.string.semester_add),
                v -> createSemester()));
        setPageTitle(getString(R.string.mine_semester_list));
        setPageContent(SettingsUi.scrollWrap(this, column));
    }

    private MaterialCardView card(final Semester semester, LocalDate today) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = SettingsUi.dp(this, 8);
        card.setLayoutParams(params);
        card.setRadius(SettingsUi.dp(this, 12));
        card.setCardElevation(SettingsUi.dp(this, 1));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = SettingsUi.dp(this, 14);
        box.setPadding(pad, pad, pad, pad);

        TextView name = new TextView(this);
        name.setText(semester.name + (semester.id.equals(data.currentSemesterId)
                ? "（" + getString(R.string.semester_current) + "）" : ""));
        name.setTextSize(16);
        box.addView(name);

        TextView status = new TextView(this);
        status.setText(statusText(semester, today));
        status.setTextSize(12);
        status.setTextColor(getColor(R.color.text_secondary));
        box.addView(status);

        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(semester.progressPercent(today));
        box.addView(progress);

        card.addView(box);
        card.setOnClickListener(v -> {
            data.setCurrentSemester(semester.id);
            ScheduleStore.get(this).save();
            ReminderScheduler.sync(this);
            setResult(RESULT_OK);
            Toast.makeText(this, R.string.semester_set_current, Toast.LENGTH_SHORT).show();
            build();
        });
        card.setOnLongClickListener(v -> {
            rename(semester);
            return true;
        });
        return card;
    }

    private String statusText(Semester semester, LocalDate today) {
        String status;
        switch (semester.status(today)) {
            case Semester.RUNNING:
                status = getString(R.string.semester_status_running);
                break;
            case Semester.FINISHED:
                status = getString(R.string.semester_status_finished);
                break;
            default:
                status = getString(R.string.semester_status_not_started);
                break;
        }
        if (!semester.isConfigured()) {
            return status;
        }
        return status + " · " + getString(R.string.semester_progress_fmt,
                Math.max(1, semester.weekOf(today)), semester.totalWeeks,
                semester.progressPercent(today));
    }

    private void rename(final Semester semester) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(semester.name);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.semester_rename)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    semester.name = input.getText().toString().trim();
                    ScheduleStore.get(this).save();
                    build();
                })
                .show();
    }

    private void createSemester() {
        final Semester created = new Semester();
        created.name = getString(R.string.semester_add);
        LocalDate today = LocalDate.now();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            created.startDate = Dates.mondayOf(LocalDate.of(year, month + 1, dayOfMonth)).toString();
            data.semesters.add(created);
            data.setCurrentSemester(created.id);
            ScheduleStore.get(this).save();
            ReminderScheduler.sync(this);
            build();
        }, today.getYear(), today.getMonthValue() - 1, today.getDayOfMonth()).show();
    }
}
