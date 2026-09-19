package com.netessx.qutschedule.ui;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import com.netessx.qutschedule.R;
import com.netessx.qutschedule.data.ScheduleRepository;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.model.Course;
import com.netessx.qutschedule.model.CourseShift;
import com.netessx.qutschedule.reminder.ReminderScheduler;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 长按课程块后的快捷操作：改课、本周停课、临时调课 / 停课、删除。 */
public final class CourseActions {

    private static final int SLOT_COUNT = 10;

    private CourseActions() {
    }

    /**
     * @param date 被长按的那节课所在的日期，临时调整以它为基准。
     */
    public static void show(final Context ctx, final Course course, final LocalDate date,
                            final Runnable onChanged) {
        ScheduleStore store = ScheduleStore.get(ctx);
        CourseShift shifted = CourseShift.findById(store.shifts(), course.shiftId);
        if (shifted != null) {
            // 临时挪过来的这一条：原课程数据在别处，这里只给撤销和「去改原课程」
            showShiftedMenu(ctx, course, shifted, onChanged);
            return;
        }

        final int weekNumber = ScheduleRepository.weekOf(ctx, date);
        final ArrayList<String> labels = new ArrayList<>();
        final String edit = ctx.getString(R.string.action_edit);
        final String moveTo = ctx.getString(R.string.action_shift_move);
        final String cancelOnce = ctx.getString(R.string.action_shift_cancel);
        final String skip = ctx.getString(R.string.action_skip_week, weekNumber);
        final String delete = ctx.getString(R.string.action_delete);
        final boolean canSkip = !course.isOneOff() && weekNumber >= 1
                && course.weeks != null && course.weeks.contains(weekNumber);

        labels.add(edit);
        labels.add(moveTo);
        labels.add(cancelOnce);
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
                    } else if (moveTo.equals(label)) {
                        pickTargetDate(ctx, course, date, onChanged);
                    } else if (cancelOnce.equals(label)) {
                        cancelOnce(ctx, course, date, onChanged);
                    } else if (skip.equals(label)) {
                        skipWeek(ctx, course, weekNumber, onChanged);
                    } else if (delete.equals(label)) {
                        confirmDelete(ctx, course, onChanged);
                    }
                })
                .show();
    }

    /** 临时挪过来的那一条：只有撤销和「去改原课程」两个出路。 */
    private static void showShiftedMenu(final Context ctx, final Course course,
                                        final CourseShift shift, final Runnable onChanged) {
        String[] actions = {
                ctx.getString(R.string.action_shift_undo),
                ctx.getString(R.string.action_edit),
        };
        new MaterialAlertDialogBuilder(ctx)
                .setTitle(course.name)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        ScheduleStore.get(ctx).removeShift(shift.id);
                        afterChange(ctx, onChanged);
                        Toast.makeText(ctx, R.string.shift_undone, Toast.LENGTH_SHORT).show();
                    } else {
                        ctx.startActivity(new Intent(ctx, CourseEditActivity.class)
                                .putExtra(CourseEditActivity.EXTRA_COURSE_ID, course.id));
                    }
                })
                .show();
    }

    /** 先选目标日期，再选节次（默认沿用原课程），两步都过完才落库。 */
    private static void pickTargetDate(final Context ctx, final Course course,
                                       final LocalDate from, final Runnable onChanged) {
        new DatePickerDialog(ctx, (view, year, month, day) -> {
            LocalDate target = LocalDate.of(year, month + 1, day);
            if (target.equals(from)) {
                Toast.makeText(ctx, R.string.shift_pick_target, Toast.LENGTH_SHORT).show();
                return;
            }
            if (course.isOneOff()) {
                // 单次日程改的是哪天，节次对它没有意义
                moveTo(ctx, course, from, target, 0, 0, onChanged);
                return;
            }
            pickSlots(ctx, course, from, target, onChanged);
        }, from.getYear(), from.getMonthValue() - 1, from.getDayOfMonth()).show();
    }

    private static void pickSlots(final Context ctx, final Course course, final LocalDate from,
                                  final LocalDate target, final Runnable onChanged) {
        List<String> items = new ArrayList<>();
        items.add(ctx.getString(R.string.shift_keep_slot_fmt, course.startSlot, course.endSlot));
        for (int i = 1; i <= SLOT_COUNT; i++) {
            items.add(ctx.getString(R.string.slot_fmt, i));
        }
        // 下标 0 表示不覆盖，沿用原课程自己的节次
        final int[] picked = {0, 0};
        LinearLayout column = new LinearLayout(ctx);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(SettingsUi.dropdown(ctx, ctx.getString(R.string.slot_start_hint),
                items, 0, position -> picked[0] = position));
        column.addView(SettingsUi.dropdown(ctx, ctx.getString(R.string.slot_end_hint),
                items, 0, position -> picked[1] = position));

        new MaterialAlertDialogBuilder(ctx)
                .setTitle(ctx.getString(R.string.shift_pick_target_title) + " · " + target)
                .setView(SettingsUi.dialogWrap(ctx, column))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) ->
                        moveTo(ctx, course, from, target, picked[0], picked[1], onChanged))
                .show();
    }

    private static void moveTo(final Context ctx, final Course course, final LocalDate from,
                               final LocalDate target, final int startSlot, final int endSlot,
                               final Runnable onChanged) {
        ScheduleStore store = ScheduleStore.get(ctx);
        CourseShift shift = CourseShift.move(course.id, from.toString(), target.toString());
        shift.startSlot = startSlot;
        shift.endSlot = endSlot;
        store.upsertShift(shift);
        afterChange(ctx, onChanged);
        showUndo(ctx, ctx.getString(R.string.shift_moved_fmt, target.toString()), () -> {
            store.removeShift(shift.id);
            afterChange(ctx, onChanged);
        });
    }

    private static void cancelOnce(final Context ctx, final Course course, final LocalDate date,
                                   final Runnable onChanged) {
        ScheduleStore store = ScheduleStore.get(ctx);
        CourseShift shift = CourseShift.cancel(course.id, date.toString());
        store.upsertShift(shift);
        afterChange(ctx, onChanged);
        // 停课后这门课当天不再显示，没地方再长按它，撤销只能靠这条提示
        showUndo(ctx, ctx.getString(R.string.shift_cancelled_fmt, course.name), () -> {
            store.removeShift(shift.id);
            afterChange(ctx, onChanged);
        });
    }

    private static void showUndo(Context ctx, String message, final Runnable undo) {
        View root = rootView(ctx);
        if (root == null) {
            Toast.makeText(ctx, message, Toast.LENGTH_LONG).show();
            return;
        }
        Snackbar.make(root, message, Snackbar.LENGTH_LONG)
                .setAction(R.string.undo, v -> undo.run())
                .show();
    }

    private static View rootView(Context ctx) {
        Context base = ctx;
        while (base instanceof ContextWrapper) {
            if (base instanceof Activity) {
                return ((Activity) base).findViewById(android.R.id.content);
            }
            base = ((ContextWrapper) base).getBaseContext();
        }
        return null;
    }

    private static void afterChange(Context ctx, Runnable onChanged) {
        ReminderScheduler.sync(ctx);
        if (onChanged != null) {
            onChanged.run();
        }
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
        afterChange(ctx, onChanged);
    }

    private static void confirmDelete(final Context ctx, final Course course,
                                      final Runnable onChanged) {
        new MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.delete)
                .setMessage(ctx.getString(R.string.confirm_delete, course.name))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    ScheduleStore.get(ctx).removeCourse(course.id);
                    afterChange(ctx, onChanged);
                })
                .show();
    }
}
