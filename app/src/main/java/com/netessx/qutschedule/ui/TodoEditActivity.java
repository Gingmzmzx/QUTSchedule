package com.netessx.qutschedule.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.netessx.qutschedule.R;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.model.Todo;
import com.netessx.qutschedule.util.Dates;
import com.netessx.qutschedule.util.TimeSlots;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 待办 / 活动 / 考试 / 作业的增删改。 */
public class TodoEditActivity extends BaseActivity {

    private static final String EXTRA_TODO_ID = "todo_id";
    private static final String EXTRA_DATE = "date";

    private final List<String> types = new ArrayList<>();
    private EditText titleInput;
    private EditText noteInput;
    private MaterialAutoCompleteTextView typeSpinner;
    private Button dateButton;
    private Button startButton;
    private Button endButton;
    private MaterialSwitch allDaySwitch;
    private MaterialSwitch doneSwitch;

    private Todo editing;
    private LocalDate date = LocalDate.now();
    private String start = "09:00";
    private String end = "10:00";

    public static Intent intentFor(Context ctx, Todo todo) {
        return todo == null
                ? intentFor(ctx, null, LocalDate.now())
                : intentFor(ctx, todo.id, Dates.parse(todo.date));
    }

    public static Intent intentFor(Context ctx, String todoId, LocalDate day) {
        Intent intent = new Intent(ctx, TodoEditActivity.class);
        if (todoId != null) {
            intent.putExtra(EXTRA_TODO_ID, todoId);
        }
        if (day != null) {
            intent.putExtra(EXTRA_DATE, day.toString());
        }
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPageContent(getLayoutInflater().inflate(R.layout.activity_todo_edit, null, false));

        titleInput = findViewById(R.id.et_title);
        noteInput = findViewById(R.id.et_note);
        typeSpinner = findViewById(R.id.spinner_type);
        dateButton = findViewById(R.id.btn_date);
        startButton = findViewById(R.id.btn_start_time);
        endButton = findViewById(R.id.btn_end_time);
        allDaySwitch = findViewById(R.id.switch_allday);
        doneSwitch = findViewById(R.id.switch_done);

        types.add(getString(R.string.type_todo));
        types.add(getString(R.string.type_activity));
        types.add(getString(R.string.type_exam));
        types.add(getString(R.string.type_homework));
        types.add(getString(R.string.type_other));
        typeSpinner.setAdapter(new ArrayAdapter<>(this,
                com.google.android.material.R.layout.mtrl_auto_complete_simple_item, types));

        LocalDate extra = Dates.parse(getIntent().getStringExtra(EXTRA_DATE));
        if (extra != null) {
            date = extra;
        }

        String id = getIntent().getStringExtra(EXTRA_TODO_ID);
        editing = id == null ? null : ScheduleStore.get(this).findTodo(id);
        if (editing == null) {
            setPageTitle(getString(R.string.todo_add));
            editing = new Todo();
            editing.date = date.toString();
            findViewById(R.id.btn_delete).setVisibility(View.GONE);
        } else {
            setPageTitle(getString(R.string.todo_edit));
            LocalDate parsed = Dates.parse(editing.date);
            if (parsed != null) {
                date = parsed;
            }
        }
        fillForm();

        dateButton.setOnClickListener(v -> pickDate());
        startButton.setOnClickListener(v -> pickTime(true));
        endButton.setOnClickListener(v -> pickTime(false));
        allDaySwitch.setOnCheckedChangeListener((button, checked) -> updateTimeButtons());
        findViewById(R.id.btn_save).setOnClickListener(v -> save());
        findViewById(R.id.btn_delete).setOnClickListener(v -> confirmDelete());
    }

    private void fillForm() {
        titleInput.setText(editing.title);
        noteInput.setText(editing.note);
        doneSwitch.setChecked(editing.done);
        int typeIndex = 0;
        for (int i = 0; i < Todo.ALL.length; i++) {
            if (Todo.ALL[i].equals(editing.type)) {
                typeIndex = i;
                break;
            }
        }
        typeSpinner.setText(types.get(typeIndex), false);

        if (editing.startTime != null && !editing.startTime.isEmpty()) {
            start = editing.startTime;
        }
        if (editing.endTime != null && !editing.endTime.isEmpty()) {
            end = editing.endTime;
        }
        allDaySwitch.setChecked(!editing.hasTime());
        updateTimeButtons();
    }

    private void updateTimeButtons() {
        dateButton.setText(date.format(Dates.YMD) + " 周" + Dates.weekName(date));
        boolean allDay = allDaySwitch.isChecked();
        startButton.setEnabled(!allDay);
        endButton.setEnabled(!allDay);
        startButton.setText(allDay ? getString(R.string.todo_all_day) : start);
        endButton.setText(allDay ? getString(R.string.todo_all_day) : end);
    }

    private void pickDate() {
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            date = LocalDate.of(year, month + 1, dayOfMonth);
            updateTimeButtons();
        }, date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth()).show();
    }

    private void pickTime(final boolean isStart) {
        int minutes = TimeSlots.minutes(isStart ? start : end);
        if (minutes < 0) {
            minutes = 9 * 60;
        }
        new TimePickerDialog(this, (view, hour, minute) -> {
            String value = TimeSlots.format(hour * 60 + minute);
            if (isStart) {
                start = value;
            } else {
                end = value;
            }
            updateTimeButtons();
        }, minutes / 60, minutes % 60, true).show();
    }

    private void save() {
        String title = titleInput.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(this, R.string.todo_title, Toast.LENGTH_SHORT).show();
            return;
        }
        Todo todo = editing.copy();
        todo.title = title;
        todo.note = noteInput.getText().toString().trim();
        int typeIndex = types.indexOf(typeSpinner.getText().toString());
        todo.type = Todo.ALL[typeIndex < 0 ? 0 : typeIndex];
        todo.date = date.toString();
        todo.done = doneSwitch.isChecked();
        if (allDaySwitch.isChecked()) {
            todo.startTime = null;
            todo.endTime = null;
        } else {
            todo.startTime = start;
            todo.endTime = end;
        }
        ScheduleStore.get(this).upsertTodo(todo);
        setResult(RESULT_OK);
        finish();
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete)
                .setMessage(getString(R.string.confirm_delete, editing.title))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    ScheduleStore.get(this).removeTodo(editing.id);
                    setResult(RESULT_OK);
                    finish();
                })
                .show();
    }
}
