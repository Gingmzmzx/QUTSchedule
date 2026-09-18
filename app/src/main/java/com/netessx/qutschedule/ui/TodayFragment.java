package com.netessx.qutschedule.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.netessx.qutschedule.MainActivity;
import com.netessx.qutschedule.R;
import com.netessx.qutschedule.data.ScheduleRepository;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.model.Course;
import com.netessx.qutschedule.model.Semester;
import com.netessx.qutschedule.model.Todo;
import com.netessx.qutschedule.util.ColorPalette;
import com.netessx.qutschedule.util.Dates;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 今日页：下节课卡片、今日课程时间轴（已结束 / 进行中 / 未开始）、今日待办、明日预告。 */
public class TodayFragment extends Fragment {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter MD_CN = DateTimeFormatter.ofPattern("M月d日");

    private TextView dateView;
    private TextView weekView;
    private TextView nextTitle;
    private TextView nextName;
    private TextView nextTime;
    private TextView nextPlace;
    private LinearLayout coursesBox;
    private LinearLayout todoBox;
    private LinearLayout tomorrowBox;

    public static TodayFragment newInstance() {
        return new TodayFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_today, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        dateView = view.findViewById(R.id.tv_date);
        weekView = view.findViewById(R.id.tv_week);
        nextTitle = view.findViewById(R.id.tv_next_title);
        nextName = view.findViewById(R.id.tv_next_name);
        nextTime = view.findViewById(R.id.tv_next_time);
        nextPlace = view.findViewById(R.id.tv_next_place);
        coursesBox = view.findViewById(R.id.courses_container);
        todoBox = view.findViewById(R.id.todo_container);
        tomorrowBox = view.findViewById(R.id.tomorrow_container);

        view.findViewById(R.id.fab_add).setOnClickListener(v ->
                startActivity(TodoEditActivity.intentFor(requireContext(), null, LocalDate.now())));

        SwipeRefreshLayout swipe = view.findViewById(R.id.swipe);
        swipe.setOnRefreshListener(() -> {
            ((MainActivity) requireActivity()).refreshAll();
            swipe.setRefreshing(false);
        });
        refresh();
    }

    /** 数据变化后由宿主调用。 */
    public void refresh() {
        if (!isAdded() || dateView == null) {
            return;
        }
        Context ctx = requireContext();
        LocalDate today = LocalDate.now();
        Semester semester = ScheduleRepository.currentSemester(ctx);

        dateView.setText(today.format(MD_CN) + " 周" + Dates.weekName(today));
        weekView.setText(semester.isConfigured()
                ? getString(R.string.week_of_fmt, semester.weekOf(today)) : "");

        List<Course> courses = ScheduleRepository.coursesOn(ctx, today);
        ColorPalette.refresh(ScheduleStore.get(ctx).coursesOf(semester.id));

        bindNextCard(ctx, courses, today);
        bindCourses(ctx, courses, today);
        bindTodos(ctx, ScheduleRepository.todosOn(ctx, today));
        bindTomorrow(ctx, today.plusDays(1));
    }

    private void bindNextCard(Context ctx, List<Course> courses, LocalDate today) {
        LocalDateTime now = LocalDateTime.now();
        Course current = null;
        Course next = null;
        for (Course course : courses) {
            LocalTime[] times = ScheduleRepository.timesOf(ctx, course, today);
            if (!now.toLocalTime().isBefore(times[0]) && now.toLocalTime().isBefore(times[1])) {
                current = course;
                break;
            }
            if (next == null && now.toLocalTime().isBefore(times[0])) {
                next = course;
            }
        }
        Course target = current != null ? current : next;
        if (target == null) {
            nextTitle.setText("");
            nextTime.setText("");
            nextPlace.setText("");
            nextName.setText(courses.isEmpty()
                    ? getString(R.string.today_no_class) : getString(R.string.today_none_left));
            return;
        }
        LocalTime[] times = ScheduleRepository.timesOf(ctx, target, today);
        nextTitle.setText(current != null ? R.string.today_ongoing_title : R.string.today_next_title);
        nextName.setText(target.name);
        nextTime.setText(HM.format(times[0]) + " - " + HM.format(times[1]) + " · "
                + (current != null
                ? getString(R.string.today_remaining_fmt, minutesBetween(now.toLocalTime(), times[1]))
                : getString(R.string.today_countdown_fmt, minutesBetween(now.toLocalTime(), times[0]))));
        StringBuilder place = new StringBuilder();
        if (target.location != null && !target.location.isEmpty()) {
            place.append(target.location);
        }
        if (target.teacher != null && !target.teacher.isEmpty()) {
            if (place.length() > 0) {
                place.append(" · ");
            }
            place.append(target.teacher);
        }
        nextPlace.setText(place);
    }

    private void bindCourses(Context ctx, List<Course> courses, LocalDate today) {
        coursesBox.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(ctx);
        for (final Course course : courses) {
            View row = inflater.inflate(R.layout.item_timeline, coursesBox, false);
            LocalTime[] times = ScheduleRepository.timesOf(ctx, course, today);
            ((TextView) row.findViewById(R.id.tv_start)).setText(HM.format(times[0]));
            ((TextView) row.findViewById(R.id.tv_end)).setText(HM.format(times[1]));
            ((TextView) row.findViewById(R.id.tv_name)).setText(course.name);
            ((TextView) row.findViewById(R.id.tv_place)).setText(
                    course.location == null ? "" : course.location);
            row.findViewById(R.id.color_bar)
                    .setBackgroundColor(ColorPalette.colorOf(ctx, course));

            LocalTime now = LocalTime.now();
            boolean past = now.isAfter(times[1]);
            TextView state = row.findViewById(R.id.tv_state);
            if (past) {
                state.setText(R.string.today_state_finished);
            } else if (!now.isBefore(times[0])) {
                state.setText(R.string.today_state_ongoing);
            } else {
                state.setText(R.string.today_state_upcoming);
            }
            row.setAlpha(past ? 0.5f : 1f);

            row.setOnClickListener(v -> ((MainActivity) requireActivity()).openEditor(course));
            row.setOnLongClickListener(v -> {
                CourseActions.show(ctx, course, ScheduleRepository.weekOf(ctx, today),
                        () -> ((MainActivity) requireActivity()).refreshAll());
                return true;
            });
            coursesBox.addView(row);
        }
        if (courses.isEmpty()) {
            coursesBox.addView(hint(ctx, getString(R.string.today_no_class)));
        }
    }

    private void bindTodos(Context ctx, List<Todo> todos) {
        todoBox.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(ctx);
        for (final Todo todo : todos) {
            View row = inflater.inflate(R.layout.item_todo, todoBox, false);
            CheckBox done = row.findViewById(R.id.cb_done);
            ((TextView) row.findViewById(R.id.tv_title)).setText(todo.title);
            LocalTime[] times = ScheduleRepository.timesOf(todo);
            ((TextView) row.findViewById(R.id.tv_time)).setText(times == null
                    ? getString(R.string.todo_all_day)
                    : HM.format(times[0]) + " - " + HM.format(times[1]));
            done.setChecked(todo.done);
            row.setAlpha(todo.done ? 0.5f : 1f);
            done.setOnCheckedChangeListener((button, checked) -> {
                Todo updated = todo.copy();
                updated.done = checked;
                ScheduleStore.get(ctx).upsertTodo(updated);
                ((MainActivity) requireActivity()).refreshAll();
            });
            row.setOnClickListener(v -> startActivity(
                    TodoEditActivity.intentFor(ctx, todo.id, LocalDate.parse(todo.date))));
            todoBox.addView(row);
        }
        if (todos.isEmpty()) {
            todoBox.addView(hint(ctx, getString(R.string.today_no_todo)));
        }
    }

    private void bindTomorrow(Context ctx, LocalDate tomorrow) {
        tomorrowBox.removeAllViews();
        List<Course> courses = ScheduleRepository.coursesOn(ctx, tomorrow);
        LayoutInflater inflater = LayoutInflater.from(ctx);
        for (Course course : courses) {
            LocalTime[] times = ScheduleRepository.timesOf(ctx, course, tomorrow);
            View row = inflater.inflate(R.layout.item_timeline, tomorrowBox, false);
            ((TextView) row.findViewById(R.id.tv_start)).setText(HM.format(times[0]));
            ((TextView) row.findViewById(R.id.tv_end)).setText(HM.format(times[1]));
            ((TextView) row.findViewById(R.id.tv_name)).setText(course.name);
            ((TextView) row.findViewById(R.id.tv_place)).setText(
                    course.location == null ? "" : course.location);
            row.findViewById(R.id.tv_state).setVisibility(View.GONE);
            row.findViewById(R.id.color_bar)
                    .setBackgroundColor(ColorPalette.colorOf(ctx, course));
            tomorrowBox.addView(row);
        }
        if (courses.isEmpty()) {
            tomorrowBox.addView(hint(ctx, getString(R.string.today_tomorrow_none)));
        }
    }

    private TextView hint(Context ctx, String text) {
        TextView view = new TextView(ctx);
        view.setText(text);
        view.setTextSize(13);
        view.setPadding(0, (int) (getResources().getDisplayMetrics().density * 6), 0, 0);
        view.setTextColor(ctx.getColor(R.color.text_secondary));
        return view;
    }

    private static long minutesBetween(LocalTime from, LocalTime to) {
        return Math.max(0, Duration.between(from, to).toMinutes());
    }
}
