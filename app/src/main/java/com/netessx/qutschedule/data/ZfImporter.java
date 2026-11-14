package com.netessx.qutschedule.data;

import android.content.Context;

import com.netessx.qutschedule.R;
import com.netessx.qutschedule.model.AppData;
import com.netessx.qutschedule.model.Course;
import com.netessx.qutschedule.model.Semester;
import com.netessx.qutschedule.reminder.ReminderScheduler;

import java.util.ArrayList;
import java.util.List;

/**
 * 把已经抓到的课表 JSON 写进当前学期。
 *
 * <p>只替换「当前学期 + 教务系统来源」的课程，手动添加 / PDF 导入的课程原样保留；
 * 学期名还是占位名时顺手改成抓到的真实学期名。后台自动刷新和引导里的自动导入共用这套
 * 逻辑，保证两条路径结果一致。
 */
public final class ZfImporter {

    /** 导入结果，供界面展示。 */
    public static final class Result {
        public boolean ok;
        public int courses;
        public int unscheduled;
        public String error = "";
    }

    private ZfImporter() {
    }

    /** 读取最近一次抓取结果并导入当前学期。调用方负责放到后台线程。 */
    public static Result importCaptured(Context context) {
        Context app = context.getApplicationContext();
        Result result = new Result();
        String body = ZfCaptureStore.get(app).latestBody();
        if (body == null || body.trim().isEmpty()) {
            result.error = app.getString(R.string.import_web_missing);
            return result;
        }
        ZfTimetableParser.ParseResult parsed = ZfTimetableParser.parse(body);
        if (!parsed.isOk()) {
            result.error = parsed.error;
            return result;
        }
        apply(app, parsed, result);
        return result;
    }

    /** 把解析结果合并进当前学期并落盘。 */
    public static void apply(Context app, ZfTimetableParser.ParseResult parsed, Result result) {
        ScheduleStore store = ScheduleStore.get(app);
        AppData data = store.data();
        Semester semester = store.currentSemester();
        data.courses = merge(data.courses, semester.id, parsed.courses, parsed.unscheduled);
        if (semester.isDefaultName()
                && parsed.termName != null && !parsed.termName.trim().isEmpty()) {
            semester.name = parsed.termName.trim();
        }
        semester.normalize();
        store.save();
        ReminderScheduler.sync(app);

        result.ok = true;
        result.courses = parsed.courses.size();
        result.unscheduled = parsed.unscheduled.size();
    }

    /**
     * 用新抓到的课程替换当前学期里来自教务系统的课程；其余课程保持不动。
     *
     * <p>纯函数，方便单元测试。
     */
    public static List<Course> merge(List<Course> existing, String semesterId,
                                     List<Course> incoming, List<Course> incomingUnscheduled) {
        List<Course> merged = new ArrayList<>();
        for (Course course : existing) {
            boolean fromZf = ZfCaptureStore.SOURCE_ZF.equals(course.source);
            if (fromZf && semesterId != null && semesterId.equals(course.semesterId)) {
                continue;
            }
            merged.add(course);
        }
        for (Course course : incoming) {
            course.semesterId = semesterId;
            course.source = ZfCaptureStore.SOURCE_ZF;
            merged.add(course);
        }
        for (Course course : incomingUnscheduled) {
            course.semesterId = semesterId;
            course.source = ZfCaptureStore.SOURCE_ZF;
            merged.add(course);
        }
        return merged;
    }
}
