package com.netessx.qutschedule.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.fragment.app.Fragment;

import com.netessx.qutschedule.MainActivity;
import com.netessx.qutschedule.R;
import com.netessx.qutschedule.data.ScheduleRepository;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.model.Course;
import com.netessx.qutschedule.model.Semester;
import com.netessx.qutschedule.model.TimeScheme;
import com.netessx.qutschedule.util.ColorPalette;
import com.netessx.qutschedule.util.Dates;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** 课表页：整周网格，左右滑动切周，点标题快速跳转。 */
public class WeekFragment extends Fragment {

    private int weekNumber = 1;
    private WeekGridView grid;
    private TextView title;
    private TextView termView;

    public static WeekFragment newInstance() {
        return new WeekFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_week, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        title = view.findViewById(R.id.tv_title);
        termView = view.findViewById(R.id.tv_term);
        grid = view.findViewById(R.id.week_grid);

        grid.setOnCourseListener(new WeekGridView.OnCourseListener() {
            @Override
            public void onCourseClick(Course course) {
                ((MainActivity) requireActivity()).openEditor(course);
            }

            @Override
            public void onCourseLongClick(Course course, LocalDate date) {
                CourseActions.show(requireContext(), course, date,
                        () -> ((MainActivity) requireActivity()).refreshAll());
            }
        });
        grid.setOnWeekChangeListener(this::shiftWeek);
        grid.setOnTitleClickListener(this::showWeekPicker);
        grid.setOnEmptyLongClickListener(this::showAddMenu);

        ImageButton prev = view.findViewById(R.id.btn_prev);
        ImageButton next = view.findViewById(R.id.btn_next);
        prev.setOnClickListener(v -> shiftWeek(-1));
        next.setOnClickListener(v -> shiftWeek(1));

        Semester semester = ScheduleRepository.currentSemester(requireContext());
        weekNumber = semester.isConfigured() ? Math.max(1, semester.weekOf(LocalDate.now())) : 1;
        refresh();
    }

    private void shiftWeek(int delta) {
        Semester semester = ScheduleRepository.currentSemester(requireContext());
        int total = Math.max(1, semester.totalWeeks);
        weekNumber = Math.max(1, Math.min(total, weekNumber + delta));
        refresh();
    }

    /** 长按空白格：在这一天临时加一节课，或者加一条待办。 */
    private void showAddMenu(LocalDate date) {
        String[] actions = {
                getString(R.string.week_add_class),
                getString(R.string.week_add_todo),
        };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(Dates.format(date) + " 周" + Dates.weekName(date))
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        startActivity(CourseEditActivity.intentForNew(requireContext(), date));
                    } else {
                        startActivity(TodoEditActivity.intentFor(requireContext(), null, date));
                    }
                })
                .show();
    }

    /** 点标题弹出周次列表，直接跳到目标周。 */
    private void showWeekPicker() {
        Semester semester = ScheduleRepository.currentSemester(requireContext());
        if (!semester.isConfigured()) {
            return;
        }
        final int total = Math.max(1, semester.totalWeeks);
        String[] items = new String[total];
        for (int i = 1; i <= total; i++) {
            LocalDate monday = semester.dateOf(i, 1);
            items[i - 1] = getString(R.string.week_title_fmt, i,
                    monday.format(Dates.MD), monday.plusDays(6).format(Dates.MD));
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.jump_to_week)
                .setSingleChoiceItems(items, weekNumber - 1, (dialog, which) -> {
                    weekNumber = which + 1;
                    dialog.dismiss();
                    refresh();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** 数据变化后由宿主调用。 */
    public void refresh() {
        if (!isAdded() || grid == null) {
            return;
        }
        Semester semester = ScheduleRepository.currentSemester(requireContext());
        grid.setPrefs(ScheduleStore.get(requireContext()).data().prefs);
        if (!semester.isConfigured()) {
            title.setText(R.string.no_schedule_yet);
            termView.setText("");
            grid.setWeek(semester, null, 1, null, null, LocalDate.now());
            return;
        }
        weekNumber = Math.max(1, Math.min(weekNumber, semester.totalWeeks));

        LocalDate monday = semester.dateOf(weekNumber, 1);
        LocalDate sunday = monday.plusDays(6);
        title.setText(getString(R.string.week_title_fmt, weekNumber,
                monday.format(Dates.MD), sunday.format(Dates.MD)));

        long diff = ChronoUnit.WEEKS.between(Dates.mondayOf(LocalDate.now()), monday);
        String name = semester.name == null || semester.name.isEmpty() ? "" : semester.name;
        String relative = diff == 0 ? "本周" : (diff < 0 ? Math.abs(diff) + " 周前" : diff + " 周后");
        termView.setText(name.isEmpty() ? relative : name + " · " + relative);

        List<Course>[] weekGrid = ScheduleRepository.weekCourses(requireContext(), weekNumber, 7);
        ColorPalette.refresh(ScheduleStore.get(requireContext()).coursesOf(semester.id));
        TimeScheme scheme = ScheduleStore.get(requireContext()).schemeFor(monday);
        grid.setWeek(semester, scheme, weekNumber, monday, weekGrid, LocalDate.now());
    }
}
