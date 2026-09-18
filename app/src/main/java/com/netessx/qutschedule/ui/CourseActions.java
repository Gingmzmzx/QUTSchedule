package com.netessx.qutschedule.ui;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.netessx.qutschedule.R;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.model.Course;

import java.util.ArrayList;

/** 长按课程块后的快捷操作：改课、本周停课、删除。 */
public final class CourseActions {

    private CourseActions() {
    }

    public static void show(final Context ctx, final Course course,
                            final int weekNumber, final Runnable onChanged) {
        final ArrayList<String> labels = new ArrayList<>();
        final String edit = ctx.getString(R.string.action_edit);
        final String delete = ctx.getString(R.string.action_delete);
        final String skip = ctx.getString(R.string.action_skip_week, weekNumber);
        final boolean canSkip = !course.isOneOff() && weekNumber >= 1
                && course.weeks != null && course.weeks.contains(weekNumber);

        labels.add(edit);
        if (canSkip) {
            labels.add(skip);
        }
        labels.add(delete);

        new MaterialAlertDialogBuilder(ctx)
                .setTitle(course.name)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    String label = labels.get(which);
                    if (edit.equals(label)) {
                        ctx.startActivity(new Intent(ctx, CourseEditActivity.class)
                                .putExtra(CourseEditActivity.EXTRA_COURSE_ID, course.id));
                    } else if (skip.equals(label)) {
                        skipWeek(ctx, course, weekNumber, onChanged);
                    } else if (delete.equals(label)) {
                        confirmDelete(ctx, course, onChanged);
                    }
                })
                .show();
    }

    /** 只把这一周从周次里摘掉，其余周次照常。 */
    private static void skipWeek(Context ctx, Course course, int weekNumber, Runnable onChanged) {
        Course updated = course.copy();
        updated.weeks = new ArrayList<>(course.weeks);
        updated.weeks.remove(Integer.valueOf(weekNumber));
        // weeks 为空会被当成「每周都上」，所以最后一节课只能整门删，不能停这一周
        if (updated.weeks.isEmpty()) {
            Toast.makeText(ctx, R.string.skip_week_last, Toast.LENGTH_LONG).show();
            return;
        }
        updated.custom = true;
        ScheduleStore.get(ctx).upsertCourse(updated);
        if (onChanged != null) {
            onChanged.run();
        }
    }

    private static void confirmDelete(final Context ctx, final Course course,
                                      final Runnable onChanged) {
        new MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.delete)
                .setMessage(ctx.getString(R.string.confirm_delete, course.name))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    ScheduleStore.get(ctx).removeCourse(course.id);
                    if (onChanged != null) {
                        onChanged.run();
                    }
                })
                .show();
    }
}
