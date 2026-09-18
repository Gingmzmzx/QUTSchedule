package com.netessx.qutschedule.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.netessx.qutschedule.R;
import com.netessx.qutschedule.data.ScheduleRepository;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.model.Course;
import com.netessx.qutschedule.reminder.ReminderScheduler;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 课程管理：按课程名聚合查看 / 编辑，支持整门删除与课程调动。 */
public class CourseManageActivity extends BaseActivity {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private ScheduleStore store;
    private LinearLayout column;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = ScheduleStore.get(this);
        build();
    }

    private void build() {
        column = SettingsUi.column(this);
        List<String> names = ScheduleRepository.courseNames(this);
        SettingsUi.section(this, column, getString(R.string.course_manage_count_fmt, names.size()));
        for (final String name : names) {
            final List<Course> courses = ScheduleRepository.coursesOfName(this, name);
            TextView row = new TextView(this);
            row.setTextSize(15);
            row.setPadding(0, SettingsUi.dp(this, 12), 0, SettingsUi.dp(this, 12));
            row.setText(name + "\n" + summary(courses));
            row.setOnClickListener(v -> showActions(name, courses));
            column.addView(row);
        }
        column.addView(SettingsUi.label(this, getString(R.string.course_manage_move_hint)));
        column.addView(SettingsUi.buttonRow(this, getString(R.string.course_manage_move),
                v -> moveDay()));
        setPageTitle(getString(R.string.mine_course_manage));
        setPageContent(SettingsUi.scrollWrap(this, column));
    }

    private String summary(List<Course> courses) {
        Course first = courses.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append(courses.size()).append(" 个课次");
        if (!first.weekSpec.isEmpty()) {
            sb.append(" · ").append(first.weekSpec);
        }
        if (first.credit > 0) {
            sb.append(" · ").append(getString(R.string.course_manage_credit))
                    .append(' ').append(first.credit);
        }
        if (first.assessment != null && !first.assessment.isEmpty()) {
            sb.append(" · ").append(first.assessment);
        }
        if (first.lab) {
            sb.append(" · ").append(getString(R.string.course_manage_lab));
        }
        return sb.toString();
    }

    private void showActions(final String name, final List<Course> courses) {
        String[] actions = {
                getString(R.string.action_edit),
                getString(R.string.course_manage_rename),
                getString(R.string.course_manage_delete_all),
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(name)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0 && !courses.isEmpty()) {
                        startActivity(CourseEditActivity.intentFor(this, courses.get(0)));
                    } else if (which == 1) {
                        rename(name, courses);
                    } else if (which == 2) {
                        deleteAll(name, courses);
                    }
                })
                .show();
    }

    private void rename(final String oldName, final List<Course> courses) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(oldName);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.course_manage_rename)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty()) {
                        return;
                    }
                    for (Course course : courses) {
                        Course updated = course.copy();
                        updated.name = newName;
                        store.upsertCourse(updated);
                    }
                    afterChange();
                })
                .show();
    }

    private void deleteAll(String name, List<Course> courses) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.course_manage_delete_all)
                .setMessage(getString(R.string.confirm_delete, name))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    for (Course course : courses) {
                        store.removeCourse(course.id);
                    }
                    afterChange();
                })
                .show();
    }

    /** 把某一天的课整体调到另一天：原课次摘掉那一周，目标日生成一次单次课。 */
    private void moveDay() {
        final LocalDate today = LocalDate.now();
        new DatePickerDialog(this, (view, y1, m1, d1) -> {
            final LocalDate source = LocalDate.of(y1, m1 + 1, d1);
            new DatePickerDialog(this, (view2, y2, m2, d2) -> {
                LocalDate target = LocalDate.of(y2, m2 + 1, d2);
                int moved = applyMove(source, target);
                Toast.makeText(this, getString(R.string.course_manage_moved_fmt, moved),
                        Toast.LENGTH_SHORT).show();
                afterChange();
            }, today.getYear(), today.getMonthValue() - 1, today.getDayOfMonth()).show();
        }, today.getYear(), today.getMonthValue() - 1, today.getDayOfMonth()).show();
    }

    private int applyMove(LocalDate source, LocalDate target) {
        List<Course> occurrences = ScheduleRepository.coursesOn(this, source);
        int sourceWeek = ScheduleRepository.weekOf(this, source);
        int moved = 0;
        for (Course course : occurrences) {
            Course oneOff = course.copy();
            oneOff.id = UUID.randomUUID().toString();
            oneOff.date = target.toString();
            oneOff.weeks = new ArrayList<>();
            oneOff.weekSpec = "";
            oneOff.custom = true;
            oneOff.dayOfWeek = target.getDayOfWeek().getValue();
            oneOff.startTime = ScheduleRepository.timesOf(this, course, source)[0].format(HM);
            oneOff.endTime = ScheduleRepository.timesOf(this, course, source)[1].format(HM);
            store.upsertCourse(oneOff);

            if (course.isOneOff()) {
                store.removeCourse(course.id);
            } else if (course.weeks.contains(sourceWeek)) {
                Course trimmed = course.copy();
                trimmed.weeks = new ArrayList<>(course.weeks);
                trimmed.weeks.remove(Integer.valueOf(sourceWeek));
                store.upsertCourse(trimmed);
            }
            moved++;
        }
        return moved;
    }

    private void afterChange() {
        ReminderScheduler.sync(this);
        setResult(RESULT_OK);
        build();
    }
}
