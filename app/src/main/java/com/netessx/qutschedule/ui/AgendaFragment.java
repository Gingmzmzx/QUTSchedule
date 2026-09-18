package com.netessx.qutschedule.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.netessx.qutschedule.MainActivity;
import com.netessx.qutschedule.R;
import com.netessx.qutschedule.data.ScheduleRepository;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.model.Course;
import com.netessx.qutschedule.model.Semester;
import com.netessx.qutschedule.model.Todo;
import com.netessx.qutschedule.util.ColorPalette;
import com.netessx.qutschedule.util.Dates;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 日程页：整月日历 + 日期轴 + 当日时间轴，课程与待办混排。 */
public class AgendaFragment extends Fragment {

    private static final int CELLS = 42;
    /** 日期轴以选中日为中心，前后各铺这么多天。 */
    private static final int AXIS_BEFORE = 14;
    private static final int AXIS_AFTER = 14;

    private LocalDate visibleMonth = LocalDate.now().withDayOfMonth(1);
    private LocalDate selected = LocalDate.now();
    private MonthGridView gridView;
    private AgendaAdapter adapter;
    private TextView titleView;
    private TextView selectedView;
    private TextView emptyView;
    private LinearLayout dateAxis;
    private HorizontalScrollView dateAxisScroll;

    public static AgendaFragment newInstance() {
        return new AgendaFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_agenda, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        titleView = view.findViewById(R.id.tv_title);
        selectedView = view.findViewById(R.id.tv_selected);
        emptyView = view.findViewById(R.id.tv_empty);
        gridView = view.findViewById(R.id.month_grid);
        dateAxis = view.findViewById(R.id.date_axis);
        dateAxisScroll = view.findViewById(R.id.date_axis_scroll);

        gridView.setOnDaySelectedListener(date -> {
            selected = date;
            if (date.getMonthValue() != visibleMonth.getMonthValue()) {
                visibleMonth = date.withDayOfMonth(1);
            }
            refresh();
        });

        adapter = new AgendaAdapter(requireContext());
        adapter.setListener(new AgendaAdapter.Listener() {
            @Override
            public void onCourseClick(Course course) {
                ((MainActivity) requireActivity()).openEditor(course);
            }

            @Override
            public void onCourseLongClick(Course course) {
                CourseActions.show(requireContext(), course,
                        ScheduleRepository.weekOf(requireContext(), selected),
                        () -> ((MainActivity) requireActivity()).refreshAll());
            }

            @Override
            public void onTodoClick(Todo todo) {
                startActivity(TodoEditActivity.intentFor(requireContext(), todo.id,
                        Dates.parse(todo.date)));
            }

            @Override
            public void onTodoToggle(Todo todo, boolean done) {
                Todo updated = todo.copy();
                updated.done = done;
                ScheduleStore.get(requireContext()).upsertTodo(updated);
                ((MainActivity) requireActivity()).refreshAll();
            }
        });

        RecyclerView recycler = view.findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        ImageButton prev = view.findViewById(R.id.btn_prev);
        ImageButton next = view.findViewById(R.id.btn_next);
        prev.setOnClickListener(v -> shiftMonth(-1));
        next.setOnClickListener(v -> shiftMonth(1));
        titleView.setOnClickListener(v -> toggleMonth());

        view.findViewById(R.id.fab_add).setOnClickListener(v ->
                startActivity(TodoEditActivity.intentFor(requireContext(), null, selected)));
        refresh();
    }

    private void shiftMonth(int delta) {
        visibleMonth = visibleMonth.plusMonths(delta);
        selected = visibleMonth;
        refresh();
    }

    /** 点标题收起 / 展开月历，方便看当天的长列表。 */
    private void toggleMonth() {
        boolean visible = gridView.getVisibility() == View.VISIBLE;
        gridView.setVisibility(visible ? View.GONE : View.VISIBLE);
        titleView.setText(monthTitle() + " · "
                + getString(visible ? R.string.agenda_expand_month : R.string.agenda_collapse_month));
    }

    private String monthTitle() {
        return getString(R.string.month_title_fmt,
                visibleMonth.getYear(), visibleMonth.getMonthValue());
    }

    /** 日期轴：以选中日期为中心铺开，点左右滑动浏览，点一下选中。 */
    private void buildDateAxis() {
        if (dateAxis == null) {
            return;
        }
        dateAxis.removeAllViews();
        LocalDate start = selected.minusDays(AXIS_BEFORE);
        for (int i = 0; i <= AXIS_BEFORE + AXIS_AFTER; i++) {
            final LocalDate date = start.plusDays(i);
            boolean isSelected = date.equals(selected);
            TextView chip = new TextView(requireContext());
            chip.setText(date.getDayOfMonth() + "\n周" + Dates.weekName(date));
            chip.setTextSize(11);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setLines(2);
            int padH = SettingsUi.dp(requireContext(), 8);
            int padV = SettingsUi.dp(requireContext(), 6);
            chip.setPadding(padH, padV, padH, padV);
            chip.setTextColor(requireContext().getColor(isSelected
                    ? R.color.purple_500 : R.color.text_secondary));
            chip.setTypeface(null, isSelected
                    ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            chip.setOnClickListener(v -> {
                selected = date;
                if (date.getMonthValue() != visibleMonth.getMonthValue()) {
                    visibleMonth = date.withDayOfMonth(1);
                }
                refresh();
            });
            dateAxis.addView(chip);
        }
        // 选中项滚到中间
        dateAxis.post(() -> {
            View target = dateAxis.getChildAt(AXIS_BEFORE);
            if (target != null) {
                dateAxisScroll.smoothScrollTo(
                        target.getLeft() - dateAxisScroll.getWidth() / 2, 0);
            }
        });
    }

    /** 数据变化后由宿主调用。 */
    public void refresh() {
        if (!isAdded() || gridView == null) {
            return;
        }
        Semester semester = ScheduleRepository.currentSemester(requireContext());
        titleView.setText(monthTitle() + " · " + getString(R.string.agenda_collapse_month));

        LocalDate firstCell = Dates.mondayOf(visibleMonth);
        @SuppressWarnings("unchecked")
        List<Integer>[] dots = new List[CELLS];
        for (int i = 0; i < CELLS; i++) {
            LocalDate date = firstCell.plusDays(i);
            List<Integer> colors = new ArrayList<>();
            for (Course course : ScheduleRepository.coursesOn(requireContext(), date)) {
                colors.add(ColorPalette.colorOf(requireContext(), course));
                if (colors.size() >= 4) {
                    break;
                }
            }
            dots[i] = colors;
        }
        ColorPalette.refresh(ScheduleStore.get(requireContext()).coursesOf(semester.id));
        gridView.setMonth(visibleMonth, selected, LocalDate.now(), dots);
        buildDateAxis();

        selectedView.setText(getString(R.string.day_title_fmt,
                selected.format(Dates.MD), Dates.weekName(selected))
                + (semester.isConfigured()
                ? " · " + getString(R.string.week_of_fmt, semester.weekOf(selected)) : ""));

        List<Course> courses = ScheduleRepository.coursesOn(requireContext(), selected);
        List<Todo> todos = ScheduleRepository.todosOn(requireContext(), selected);
        adapter.submit(courses, todos);

        boolean empty = courses.isEmpty() && todos.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            emptyView.setText(ScheduleRepository.hasAnyCourse(requireContext())
                    ? getString(R.string.agenda_empty)
                    : getString(R.string.no_schedule_yet));
        }
    }
}
