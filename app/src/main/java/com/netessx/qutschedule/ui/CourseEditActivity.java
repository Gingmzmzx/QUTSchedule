package com.netessx.qutschedule.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
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
import com.netessx.qutschedule.model.Course;
import com.netessx.qutschedule.model.CourseType;
import com.netessx.qutschedule.model.Semester;
import com.netessx.qutschedule.pdf.WeekSpec;
import com.netessx.qutschedule.reminder.ReminderScheduler;
import com.netessx.qutschedule.util.Dates;
import com.netessx.qutschedule.util.TimeSlots;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 新增 / 修改 / 删除一条日程：课程、社团活动、研究院活动都走这里。 */
public class CourseEditActivity extends BaseActivity {

    public static final String EXTRA_COURSE_ID = "course_id";
    /** 新建时预置成这一天的单次日程，用于「临时加一节课」。 */
    public static final String EXTRA_DATE = "date";

    private static final String TAG = "CourseEditActivity";

    private static final String[] DAY_NAMES = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private static final int REPEAT_WEEKLY = 0;
    private static final int REPEAT_ONCE = 1;
    private static final int SLOT_COUNT = 10;

    private EditText nameInput;
    private EditText teacherInput;
    private EditText locationInput;
    private EditText campusInput;
    private EditText noteInput;
    private EditText weeksInput;
    private EditText leadInput;
    private MaterialAutoCompleteTextView typeSpinner;
    private MaterialAutoCompleteTextView repeatSpinner;
    private MaterialAutoCompleteTextView daySpinner;
    private MaterialAutoCompleteTextView startSlotSpinner;
    private MaterialAutoCompleteTextView endSlotSpinner;
    private MaterialSwitch reminderSwitch;
    private View dayGroup;
    private View slotsGroup;
    private View weeksGroup;
    private View onceGroup;
    private Button dateButton;
    private Button startTimeButton;
    private Button endTimeButton;

    private Course editing;
    private LocalDate onceDate = LocalDate.now();
    /** 单次日程按节次排（临时加课），而不是让用户自己填起止时间。 */
    private boolean slotOnceMode;

    public static Intent intentFor(Context ctx, Course course) {
        Intent intent = new Intent(ctx, CourseEditActivity.class);
        if (course != null) {
            intent.putExtra(EXTRA_COURSE_ID, course.id);
        }
        return intent;
    }

    /** 新建一节课，日期预置好 —— 周视图长按空白格走这里。 */
    public static Intent intentForNew(Context ctx, LocalDate date) {
        Intent intent = new Intent(ctx, CourseEditActivity.class);
        intent.putExtra(EXTRA_DATE, date.toString());
        return intent;
    }

    private String onceStart = "09:00";
    private String onceEnd = "10:00";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setPageContent(getLayoutInflater().inflate(R.layout.activity_course_edit, null, false));

        bindViews();
        setupSpinners();

        String id = getIntent().getStringExtra(EXTRA_COURSE_ID);
        editing = id == null ? null : ScheduleStore.get(this).findCourse(id);
        if (editing == null) {
            setPageTitle(getString(R.string.title_add_course));
            editing = new Course();
            editing.dayOfWeek = LocalDate.now().getDayOfWeek().getValue();
            Semester semester = ScheduleStore.get(this).currentSemester();
            editing.weekSpec = "1-" + Math.max(1, semester.totalWeeks) + "周";
            editing.weeks = WeekSpec.parse(editing.weekSpec, 40);
            // 从周视图空白格进来时，直接落成这一天的单次课
            LocalDate preset = Dates.parse(getIntent().getStringExtra(EXTRA_DATE));
            if (preset != null) {
                editing.date = preset.toString();
                editing.dayOfWeek = preset.getDayOfWeek().getValue();
            }
            // 从周视图空白格进来的临时加课：按节次排，不让用户填起止时间
            slotOnceMode = preset != null;
            findViewById(R.id.btn_delete).setVisibility(View.GONE);
        } else {
            // 没写具体时间的单次日程就是按节次排的，重新编辑也走节次
            slotOnceMode = editing.isOneOff()
                    && (editing.startTime == null || editing.startTime.isEmpty());
            setPageTitle(getString(R.string.title_edit_course));
        }
        fillForm();
    }

    private void bindViews() {
        nameInput = findViewById(R.id.et_name);
        teacherInput = findViewById(R.id.et_teacher);
        locationInput = findViewById(R.id.et_location);
        campusInput = findViewById(R.id.et_campus);
        noteInput = findViewById(R.id.et_note);
        weeksInput = findViewById(R.id.et_weeks);
        leadInput = findViewById(R.id.et_lead);
        typeSpinner = findViewById(R.id.spinner_type);
        repeatSpinner = findViewById(R.id.spinner_repeat);
        daySpinner = findViewById(R.id.spinner_day);
        startSlotSpinner = findViewById(R.id.spinner_start_slot);
        endSlotSpinner = findViewById(R.id.spinner_end_slot);
        reminderSwitch = findViewById(R.id.switch_reminder);
        dayGroup = findViewById(R.id.group_day);
        slotsGroup = findViewById(R.id.group_slots);
        weeksGroup = findViewById(R.id.group_weeks);
        onceGroup = findViewById(R.id.group_once);
        dateButton = findViewById(R.id.btn_date);
        startTimeButton = findViewById(R.id.btn_start_time);
        endTimeButton = findViewById(R.id.btn_end_time);

        findViewById(R.id.btn_save).setOnClickListener(v -> save());
        findViewById(R.id.btn_delete).setOnClickListener(v -> confirmDelete());
        dateButton.setOnClickListener(v -> pickDate());
        startTimeButton.setOnClickListener(v -> pickTime(true));
        endTimeButton.setOnClickListener(v -> pickTime(false));
    }

    private void setupSpinners() {
        List<String> types = new ArrayList<>();
        for (String type : CourseType.ALL) {
            types.add(CourseType.label(this, type));
        }
        typeSpinner.setAdapter(simpleAdapter(types));

        repeatSpinner.setAdapter(simpleAdapter(Arrays.asList(
                getString(R.string.repeat_weekly), getString(R.string.repeat_once))));
        repeatSpinner.setOnItemClickListener((parent, view, position, id) -> updateGroups());

        daySpinner.setAdapter(simpleAdapter(Arrays.asList(DAY_NAMES)));

        List<String> slots = new ArrayList<>();
        for (int i = 1; i <= SLOT_COUNT; i++) {
            slots.add(getString(R.string.slot_fmt, i));
        }
        startSlotSpinner.setAdapter(simpleAdapter(slots));
        endSlotSpinner.setAdapter(simpleAdapter(slots));
    }

    /**
     * 按「重复方式」决定显示哪些行。
     *
     * <p>每周重复：周几 + 节次 + 周次；单次：日期，再按需选节次（临时加课）
     * 或自己填起止时间（考试、活动这类不在节次表里的）。
     */
    private void updateGroups() {
        boolean weekly = indexOf(repeatSpinner) == REPEAT_WEEKLY;
        boolean bySlot = weekly || slotOnceMode;
        dayGroup.setVisibility(weekly ? View.VISIBLE : View.GONE);
        weeksGroup.setVisibility(weekly ? View.VISIBLE : View.GONE);
        slotsGroup.setVisibility(bySlot ? View.VISIBLE : View.GONE);
        startTimeButton.setVisibility(bySlot ? View.GONE : View.VISIBLE);
        endTimeButton.setVisibility(bySlot ? View.GONE : View.VISIBLE);
        onceGroup.setVisibility(weekly ? View.GONE : View.VISIBLE);
    }

    /** 暴露式下拉没有「选中下标」概念，按下标取文本回填。 */
    private static void setChoice(MaterialAutoCompleteTextView view, int index) {
        if (!(view.getAdapter() instanceof ArrayAdapter)) {
            return;
        }
        ArrayAdapter<?> adapter = (ArrayAdapter<?>) view.getAdapter();
        if (adapter.getCount() == 0) {
            return;
        }
        int safe = Math.max(0, Math.min(index, adapter.getCount() - 1));
        view.setText(String.valueOf(adapter.getItem(safe)), false);
    }

    private static int indexOf(MaterialAutoCompleteTextView view) {
        if (!(view.getAdapter() instanceof ArrayAdapter)) {
            return 0;
        }
        ArrayAdapter<?> adapter = (ArrayAdapter<?>) view.getAdapter();
        CharSequence text = view.getText();
        for (int i = 0; i < adapter.getCount(); i++) {
            if (text != null && text.toString().contentEquals(String.valueOf(adapter.getItem(i)))) {
                return i;
            }
        }
        return 0;
    }

    /** 暴露式下拉要用 Material 的 item 布局，Spinner 的布局在 M3 菜单里样式不对。 */
    private ArrayAdapter<String> simpleAdapter(List<String> items) {
        return new ArrayAdapter<>(this,
                com.google.android.material.R.layout.mtrl_auto_complete_simple_item, items);
    }

    private void fillForm() {
        nameInput.setText(editing.name);
        teacherInput.setText(editing.teacher);
        locationInput.setText(editing.location);
        campusInput.setText(editing.campus);
        noteInput.setText(editing.note);
        reminderSwitch.setChecked(editing.reminderEnabled);
        leadInput.setText(String.valueOf(editing.reminderLeadMinutes >= 0
                ? editing.reminderLeadMinutes
                : ScheduleStore.get(this).data().prefs.defaultReminderMinutes));

        int typeIndex = 0;
        for (int i = 0; i < CourseType.ALL.length; i++) {
            if (CourseType.ALL[i].equals(editing.type)) {
                typeIndex = i;
                break;
            }
        }
        setChoice(typeSpinner, typeIndex);

        boolean oneOff = editing.isOneOff();
        setChoice(repeatSpinner, oneOff ? REPEAT_ONCE : REPEAT_WEEKLY);

        int day = editing.dayOfWeek >= 1
                ? editing.dayOfWeek
                : LocalDate.now().getDayOfWeek().getValue();
        setChoice(daySpinner, day - 1);
        setChoice(startSlotSpinner, Math.max(0, Math.min(SLOT_COUNT - 1, editing.startSlot - 1)));
        setChoice(endSlotSpinner, Math.max(0, Math.min(SLOT_COUNT - 1, editing.endSlot - 1)));
        weeksInput.setText(editing.weekSpec);

        if (oneOff) {
            LocalDate date = Dates.parse(editing.date);
            if (date != null) {
                onceDate = date;
            }
            if (editing.startTime != null && !editing.startTime.isEmpty()) {
                onceStart = editing.startTime;
            }
            if (editing.endTime != null && !editing.endTime.isEmpty()) {
                onceEnd = editing.endTime;
            }
        }
        updateOnceButtons();
        updateGroups();
    }

    private void updateOnceButtons() {
        dateButton.setText(onceDate.format(Dates.YMD) + " 周" + Dates.weekName(onceDate));
        startTimeButton.setText(onceStart);
        endTimeButton.setText(onceEnd);
    }

    private void pickDate() {
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            onceDate = LocalDate.of(year, month + 1, dayOfMonth);
            updateOnceButtons();
        }, onceDate.getYear(), onceDate.getMonthValue() - 1, onceDate.getDayOfMonth()).show();
    }

    private void pickTime(final boolean isStart) {
        String current = isStart ? onceStart : onceEnd;
        int minutes = TimeSlots.minutes(current);
        if (minutes < 0) {
            minutes = 9 * 60;
        }
        new TimePickerDialog(this, (view, hour, minute) -> {
            String value = TimeSlots.format(hour * 60 + minute);
            if (isStart) {
                onceStart = value;
            } else {
                onceEnd = value;
            }
            updateOnceButtons();
        }, minutes / 60, minutes % 60, true).show();
    }

    private void save() {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.field_name, Toast.LENGTH_SHORT).show();
            return;
        }
        boolean oneOff = indexOf(repeatSpinner) == REPEAT_ONCE;

        Course course = editing.copy();
        course.name = name;
        course.teacher = teacherInput.getText().toString().trim();
        course.location = locationInput.getText().toString().trim();
        course.campus = campusInput.getText().toString().trim();
        course.note = noteInput.getText().toString().trim();
        course.type = CourseType.ALL[indexOf(typeSpinner)];
        course.reminderEnabled = reminderSwitch.isChecked();
        course.reminderLeadMinutes = parseInt(leadInput.getText().toString(), -1);

        if (oneOff) {
            course.date = onceDate.toString();
            course.dayOfWeek = onceDate.getDayOfWeek().getValue();
            course.weeks = new ArrayList<>();
            course.weekSpec = "";
            if (slotOnceMode) {
                // 按节次排：时间交给当天生效的作息方案算，起止时间留空
                course.startSlot = indexOf(startSlotSpinner) + 1;
                course.endSlot = Math.max(course.startSlot, indexOf(endSlotSpinner) + 1);
                course.startTime = null;
                course.endTime = null;
            } else {
                course.startTime = onceStart;
                course.endTime = onceEnd;
            }
        } else {
            course.date = null;
            course.startTime = null;
            course.endTime = null;
            course.dayOfWeek = indexOf(daySpinner) + 1;
            course.startSlot = indexOf(startSlotSpinner) + 1;
            course.endSlot = Math.max(course.startSlot, indexOf(endSlotSpinner) + 1);
            course.weekSpec = weeksInput.getText().toString().trim();
            if (!WeekSpec.isValid(course.weekSpec)) {
                Toast.makeText(this, R.string.field_weeks, Toast.LENGTH_LONG).show();
                return;
            }
            Semester semester = ScheduleStore.get(this).currentSemester();
            course.weeks = WeekSpec.parse(course.weekSpec, Math.max(40, semester.totalWeeks + 4));
        }

        course.custom = false;
        try {
            ScheduleStore.get(this).upsertCourse(course);
            ReminderScheduler.sync(this);
        } catch (RuntimeException e) {
            // 保存失败不能直接闪退，否则用户刚改的内容全丢且看不到原因
            Log.e(TAG, "保存课程失败", e);
            Toast.makeText(this,
                    getString(R.string.save_failed_fmt, String.valueOf(e.getMessage())),
                    Toast.LENGTH_LONG).show();
            return;
        }
        setResult(RESULT_OK);
        finish();
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete)
                .setMessage(getString(R.string.confirm_delete, editing.name))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    ScheduleStore.get(this).removeCourse(editing.id);
                    ReminderScheduler.sync(this);
                    setResult(RESULT_OK);
                    finish();
                })
                .show();
    }

    private static int parseInt(String text, int fallback) {
        try {
            int value = Integer.parseInt(text.trim());
            return value < 0 ? fallback : value;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
