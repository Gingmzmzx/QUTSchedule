package com.netessx.qutschedule.data;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.netessx.qutschedule.model.AppData;
import com.netessx.qutschedule.model.Course;
import com.netessx.qutschedule.model.Semester;
import com.netessx.qutschedule.model.TimeScheme;
import com.netessx.qutschedule.model.Todo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 课表的本地存储，整表序列化为 filesDir/schedule.json。
 *
 * <p>v1 只有「一个学期 + 一套作息」的扁平结构，读取时自动升级为 v2 并立刻回写，
 * 用户不需要做任何事。
 */
public class ScheduleStore {

    private static final String TAG = "ScheduleStore";
    private static final String FILE_NAME = "schedule.json";

    private static ScheduleStore instance;

    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private AppData data = new AppData();

    /** v1 的根结构，仅用于迁移。 */
    private static class LegacyRoot {
        LegacyTerm term;
        List<Course> courses;
    }

    private static class LegacyTerm {
        String termName;
        String startDate;
        int totalWeeks = 20;
        int defaultReminderMinutes = 15;
        boolean reminderEnabled = true;
        boolean liveUpdateEnabled = true;
        String[] slotStarts;
        String[] slotEnds;
    }

    private ScheduleStore(Context context) {
        file = new File(context.getFilesDir(), FILE_NAME);
        load();
    }

    public static synchronized ScheduleStore get(Context context) {
        if (instance == null) {
            instance = new ScheduleStore(context.getApplicationContext());
        }
        return instance;
    }

    public synchronized AppData data() {
        return data;
    }

    public synchronized void load() {
        if (!file.exists()) {
            data = new AppData();
            data.normalize();
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            int version = root.has("version") ? root.get("version").getAsInt() : 1;
            if (version >= AppData.VERSION) {
                data = gson.fromJson(root, AppData.class);
                if (data == null) {
                    data = new AppData();
                }
            } else {
                data = migrate(gson.fromJson(root, LegacyRoot.class));
            }
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "读取课表失败，按空数据启动", e);
            data = new AppData();
        }
        try {
            data.normalize();
        } catch (RuntimeException e) {
            // 兜底：坏数据绝不能把应用卡在启动，宁可先空着让用户重新导入
            Log.e(TAG, "课表数据异常，已重置为空", e);
            data = new AppData();
            data.normalize();
        }
    }

    /** v1 → v2：term 拆成一个学期 + 一套作息方案，课程原样搬过去。 */
    private AppData migrate(LegacyRoot legacy) {
        AppData migrated = new AppData();
        TimeScheme scheme = TimeScheme.defaults();
        LegacyTerm term = legacy == null ? null : legacy.term;
        if (term != null) {
            if (term.slotStarts != null) {
                for (int i = 0; i < scheme.slots.size() && i < term.slotStarts.length; i++) {
                    scheme.slots.get(i).start = term.slotStarts[i];
                }
            }
            if (term.slotEnds != null) {
                for (int i = 0; i < scheme.slots.size() && i < term.slotEnds.length; i++) {
                    scheme.slots.get(i).end = term.slotEnds[i];
                }
            }
        }
        scheme.normalize();
        migrated.schemes.add(scheme);
        migrated.currentSchemeId = scheme.id;

        Semester semester = new Semester();
        semester.name = term != null && term.termName != null && !term.termName.isEmpty()
                ? term.termName : "默认学期";
        semester.startDate = term != null && term.startDate != null ? term.startDate : "";
        semester.totalWeeks = term != null ? term.totalWeeks : 20;
        semester.schemeId = scheme.id;
        semester.normalize();
        migrated.semesters.add(semester);
        migrated.currentSemesterId = semester.id;

        if (term != null) {
            migrated.prefs.defaultReminderMinutes = term.defaultReminderMinutes;
            migrated.prefs.reminderEnabled = term.reminderEnabled;
            migrated.prefs.liveUpdateEnabled = term.liveUpdateEnabled;
        }
        if (legacy != null && legacy.courses != null) {
            for (Course course : legacy.courses) {
                course.semesterId = semester.id;
                migrated.courses.add(course);
            }
        }
        Log.i(TAG, "已把 v1 课表升级为 v2：学期「" + semester.name + "」");
        return migrated;
    }

    public synchronized void save() {
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            gson.toJson(data, writer);
        } catch (IOException e) {
            Log.e(TAG, "保存课表失败", e);
        }
    }

    /** 备份恢复、清空等整体替换。 */
    public synchronized void replaceAll(AppData replacement) {
        data = replacement == null ? new AppData() : replacement;
        data.normalize();
        save();
    }

    public synchronized Semester currentSemester() {
        return data.currentSemester();
    }

    /** 某天适用的作息方案，含夏令时 / 冬令时自动切换。 */
    public synchronized TimeScheme schemeFor(LocalDate date) {
        return data.schemeFor(data.currentSemester(), date);
    }

    public synchronized List<Course> courses() {
        return new ArrayList<>(data.courses);
    }

    /** 某个学期的全部课程，含未排课的。 */
    public synchronized List<Course> coursesOf(String semesterId) {
        List<Course> out = new ArrayList<>();
        for (Course course : data.courses) {
            if (semesterId == null || semesterId.equals(course.semesterId)) {
                out.add(course);
            }
        }
        return out;
    }

    public synchronized List<Todo> todos() {
        return new ArrayList<>(data.todos);
    }

    public synchronized Course findCourse(String id) {
        if (id == null) {
            return null;
        }
        for (Course course : data.courses) {
            if (id.equals(course.id)) {
                return course;
            }
        }
        return null;
    }

    public synchronized Todo findTodo(String id) {
        if (id == null) {
            return null;
        }
        for (Todo todo : data.todos) {
            if (id.equals(todo.id)) {
                return todo;
            }
        }
        return null;
    }

    public synchronized void upsertCourse(Course course) {
        if (course == null) {
            return;
        }
        if (course.semesterId == null) {
            course.semesterId = data.currentSemester().id;
        }
        for (int i = 0; i < data.courses.size(); i++) {
            if (data.courses.get(i).id.equals(course.id)) {
                data.courses.set(i, course);
                save();
                return;
            }
        }
        data.courses.add(course);
        save();
    }

    public synchronized void removeCourse(String id) {
        data.courses.removeIf(course -> id.equals(course.id));
        save();
    }

    public synchronized void upsertTodo(Todo todo) {
        if (todo == null) {
            return;
        }
        todo.normalize();
        for (int i = 0; i < data.todos.size(); i++) {
            if (data.todos.get(i).id.equals(todo.id)) {
                data.todos.set(i, todo);
                save();
                return;
            }
        }
        data.todos.add(todo);
        save();
    }

    public synchronized void removeTodo(String id) {
        data.todos.removeIf(todo -> id.equals(todo.id));
        save();
    }

    public synchronized boolean isEmpty() {
        return data.courses.isEmpty() && data.todos.isEmpty();
    }
}
