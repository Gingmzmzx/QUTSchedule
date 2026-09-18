package com.netessx.qutschedule.ui;

import android.content.Context;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.netessx.qutschedule.R;
import com.netessx.qutschedule.data.ScheduleRepository;
import com.netessx.qutschedule.model.Course;
import com.netessx.qutschedule.model.Todo;
import com.netessx.qutschedule.util.ColorPalette;
import com.netessx.qutschedule.util.TimeSlots;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** 课程与待办混排的列表：今日页和日程页共用。 */
public class AgendaAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onCourseClick(Course course);

        void onCourseLongClick(Course course);

        void onTodoClick(Todo todo);

        void onTodoToggle(Todo todo, boolean done);
    }

    private static final int TYPE_COURSE = 0;
    private static final int TYPE_TODO = 1;
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final Context context;
    private final List<Object> items = new ArrayList<>();
    private Listener listener;
    private boolean showState;

    public AgendaAdapter(Context context) {
        this.context = context;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** 今日页需要「已结束 / 进行中 / 未开始」的状态标签，日程页不需要。 */
    public void setShowState(boolean showState) {
        this.showState = showState;
    }

    public void submit(List<Course> courses, List<Todo> todos) {
        items.clear();
        if (courses != null) {
            items.addAll(courses);
        }
        if (todos != null) {
            items.addAll(todos);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof Todo ? TYPE_TODO : TYPE_COURSE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_TODO) {
            return new TodoHolder(inflater.inflate(R.layout.item_todo, parent, false));
        }
        return new CourseHolder(inflater.inflate(R.layout.item_timeline, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = items.get(position);
        if (holder instanceof TodoHolder && item instanceof Todo) {
            bindTodo((TodoHolder) holder, (Todo) item);
        } else if (holder instanceof CourseHolder && item instanceof Course) {
            bindCourse((CourseHolder) holder, (Course) item);
        }
    }

    private void bindCourse(CourseHolder holder, final Course course) {
        LocalTime[] times = ScheduleRepository.timesOf(context, course);
        holder.start.setText(HM.format(times[0]));
        holder.end.setText(HM.format(times[1]));
        holder.name.setText(course.name);
        holder.bar.setBackgroundColor(ColorPalette.colorOf(context, course));

        StringBuilder place = new StringBuilder();
        if (course.location != null && !course.location.isEmpty()) {
            place.append(course.location);
        }
        if (!course.isOneOff()) {
            if (place.length() > 0) {
                place.append(" · ");
            }
            place.append("第 ").append(TimeSlots.label(course.startSlot)).append(" 节");
        }
        if (course.teacher != null && !course.teacher.isEmpty()) {
            if (place.length() > 0) {
                place.append(" · ");
            }
            place.append(course.teacher);
        }
        holder.place.setText(place);
        holder.place.setVisibility(place.length() == 0 ? View.GONE : View.VISIBLE);

        boolean past = LocalTime.now().isAfter(times[1]);
        holder.state.setText(showState ? stateOf(times) : "");
        holder.state.setVisibility(showState ? View.VISIBLE : View.GONE);
        holder.itemView.setAlpha(showState && past ? 0.5f : 1f);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCourseClick(course);
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onCourseLongClick(course);
                return true;
            }
            return false;
        });
    }

    private void bindTodo(TodoHolder holder, final Todo todo) {
        holder.title.setText(todo.title);
        LocalTime[] times = ScheduleRepository.timesOf(todo);
        holder.time.setText(times == null
                ? context.getString(R.string.type_todo)
                : HM.format(times[0]) + " - " + HM.format(times[1]));
        holder.done.setOnCheckedChangeListener(null);
        holder.done.setChecked(todo.done);
        applyDone(holder, todo.done);
        holder.done.setOnCheckedChangeListener((button, checked) -> {
            applyDone(holder, checked);
            if (listener != null) {
                listener.onTodoToggle(todo, checked);
            }
        });
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTodoClick(todo);
            }
        });
    }

    private void applyDone(TodoHolder holder, boolean done) {
        holder.title.setPaintFlags(done
                ? holder.title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                : holder.title.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        holder.itemView.setAlpha(done ? 0.5f : 1f);
    }

    private String stateOf(LocalTime[] times) {
        LocalTime now = LocalTime.now();
        if (now.isAfter(times[1])) {
            return context.getString(R.string.today_state_finished);
        }
        if (!now.isBefore(times[0])) {
            return context.getString(R.string.today_state_ongoing);
        }
        return context.getString(R.string.today_state_upcoming);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class CourseHolder extends RecyclerView.ViewHolder {
        final TextView start;
        final TextView end;
        final View bar;
        final TextView name;
        final TextView place;
        final TextView state;

        CourseHolder(@NonNull View itemView) {
            super(itemView);
            start = itemView.findViewById(R.id.tv_start);
            end = itemView.findViewById(R.id.tv_end);
            bar = itemView.findViewById(R.id.color_bar);
            name = itemView.findViewById(R.id.tv_name);
            place = itemView.findViewById(R.id.tv_place);
            state = itemView.findViewById(R.id.tv_state);
        }
    }

    static class TodoHolder extends RecyclerView.ViewHolder {
        final CheckBox done;
        final TextView title;
        final TextView time;

        TodoHolder(@NonNull View itemView) {
            super(itemView);
            done = itemView.findViewById(R.id.cb_done);
            title = itemView.findViewById(R.id.tv_title);
            time = itemView.findViewById(R.id.tv_time);
        }
    }
}
