package com.netessx.qutschedule.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.netessx.qutschedule.R;
import com.netessx.qutschedule.data.ScheduleRepository;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.model.Course;
import com.netessx.qutschedule.model.CourseShift;
import com.netessx.qutschedule.reminder.ReminderScheduler;

import java.time.LocalDate;
import java.util.List;

/** 课程管理：按课程名聚合查看 / 编辑，支持整门删除与课程调动。 */
public class CourseManageActivity extends BaseActivity {

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
        LinearLayout card = SettingsUi.card(this, column);
        boolean first = true;
        for (final String name : names) {
            final List<Course> courses = ScheduleRepository.coursesOfName(this, name);
            if (!first) {
                card.addView(divider());
            }
            first = false;
            card.addView(courseRow(name, courses));
        }
        column.addView(SettingsUi.label(this, getString(R.string.course_manage_move_hint)));
        column.addView(SettingsUi.buttonRow(this, getString(R.string.course_manage_move),
                v -> moveDay()));
        setPageTitle(getString(R.string.mine_course_manage));
        setPageContent(SettingsUi.scrollWrap(this, column));
    }

    private View divider() {
        View line = new View(this);
        line.setBackgroundColor(getColor(R.color.colorOutlineVariant));
        line.setAlpha(0.5f);
        line.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, SettingsUi.dp(this, 1))));
        return line;
    }

    private View courseRow(final String name, final List<Course> courses) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, SettingsUi.dp(this, 12), 0, SettingsUi.dp(this, 12));
        row.setBackgroundResource(android.R.drawable.list_selector_background);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(name);
        title.setTextSize(15);
        title.setTextColor(getColor(R.color.colorOnSurface));
        texts.addView(title);
        TextView meta = new TextView(this);
        meta.setText(summary(courses));
        meta.setTextSize(12);
        meta.setTextColor(getColor(R.color.colorOnSurfaceVariant));
        meta.setPadding(0, SettingsUi.dp(this, 2), 0, 0);
        texts.addView(meta);
        row.addView(texts, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        android.widget.ImageView chevron = new android.widget.ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron_right);
        chevron.setColorFilter(getColor(R.color.colorOnSurfaceVariant));
        chevron.setAlpha(0.6f);
        row.addView(chevron, new LinearLayout.LayoutParams(SettingsUi.dp(this, 18),
                SettingsUi.dp(this, 18)));

        row.setOnClickListener(v -> showActions(name, courses));
        return row;
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
                        pickCourse(courses);
                    } else if (which == 1) {
                        rename(name, courses);
                    } else if (which == 2) {
                        deleteAll(name, courses);
                    }
                })
                .show();
    }

    /**
     * 编辑哪一次课。
     *
     * <p>同一门课可能一周上多次（周一 1-2 节、周三 3-4 节各是一条记录），
     * 原来固定编辑第一条，其余课次改不了。这里多课次时先让用户选。
     */
    private void pickCourse(final List<Course> courses) {
        if (courses.size() == 1) {
            startActivity(CourseEditActivity.intentFor(this, courses.get(0)));
            return;
        }
        String[] items = new String[courses.size()];
        for (int i = 0; i < courses.size(); i++) {
            items[i] = describeOccurrence(courses.get(i));
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.course_manage_pick_occurrence)
                .setItems(items, (dialog, which) ->
                        startActivity(CourseEditActivity.intentFor(this, courses.get(which))))
                .show();
    }

    /** 一次课的简要描述：日期或周几 + 节次 + 地点 + 周次。 */
    private String describeOccurrence(Course course) {
        StringBuilder text = new StringBuilder();
        LocalDate date = com.netessx.qutschedule.util.Dates.parse(course.date);
        if (date != null) {
            text.append(date.format(com.netessx.qutschedule.util.Dates.YMD))
                    .append(" 周").append(com.netessx.qutschedule.util.Dates.weekName(date));
        } else {
            text.append("周").append(com.netessx.qutschedule.util.Dates.weekName(course.dayOfWeek));
        }
        text.append("  ").append(course.startSlot).append('-').append(course.endSlot)
                .append(" 节");
        if (course.location != null && !course.location.isEmpty()) {
            text.append(" · ").append(course.location);
        }
        if (course.weekSpec != null && !course.weekSpec.isEmpty()) {
            text.append(" · ").append(course.weekSpec);
        }
        return text.toString();
    }

    private void rename(final String oldName, final List<Course> courses) {
        final TextInputLayout input = SettingsUi.dialogField(this,
                getString(R.string.course_manage_rename), oldName);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.course_manage_rename)
                .setView(SettingsUi.dialogWrap(this, input))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String newName = SettingsUi.text(input);
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

    /** 把某一天的课整体调到另一天：给当天每门课各写一条临时调整，随时可撤销。 */
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
        int moved = 0;
        for (Course course : occurrences) {
            store.upsertShift(CourseShift.move(course.id, source.toString(), target.toString()));
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
