package com.netessx.qutschedule.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.netessx.qutschedule.model.AppData;
import com.netessx.qutschedule.model.Course;
import com.netessx.qutschedule.model.Prefs;
import com.netessx.qutschedule.model.Semester;
import com.netessx.qutschedule.model.Todo;

import org.junit.Test;

/** 老版本升级：清空课表，但待办 / 个人信息 / 偏好要原样保留。 */
public class ScheduleStoreResetTest {

    private static Course course(String name) {
        Course course = new Course();
        course.name = name;
        course.dayOfWeek = 1;
        course.startSlot = 1;
        course.endSlot = 2;
        return course;
    }

    @Test
    public void upgradeClearsScheduleAndKeepsTodos() {
        AppData data = new AppData();
        data.prefs.onboarded = true;
        data.prefs.schemaMigration = 0;
        Semester old = new Semester();
        old.name = "2026-2027学年第一学期";
        old.startDate = "2026-09-07";
        data.semesters.add(old);
        data.currentSemesterId = old.id;
        data.courses.add(course("高等数学D上"));
        data.courses.add(course("大学英语"));

        Todo todo = new Todo();
        todo.title = "交作业";
        todo.date = "2026-11-20";
        data.todos.add(todo);

        data.profile.nickname = "小明";

        assertTrue(ScheduleStore.applyUpgradeReset(data));

        assertEquals(0, data.courses.size());
        assertEquals(1, data.semesters.size());
        Semester fresh = data.currentSemester();
        assertEquals(Semester.DEFAULT_NAME, fresh.name);
        assertEquals("", fresh.startDate);
        assertEquals(fresh.id, data.currentSemesterId);
        assertEquals(1, data.todos.size());
        assertEquals("交作业", data.todos.get(0).title);
        assertEquals("小明", data.profile.nickname);
        assertFalse(data.prefs.onboarded);
        assertEquals(Prefs.SCHEMA_MIGRATION, data.prefs.schemaMigration);
    }

    @Test
    public void alreadyMigratedDataIsUntouched() {
        AppData data = new AppData();
        data.prefs.schemaMigration = Prefs.SCHEMA_MIGRATION;
        data.prefs.onboarded = true;
        Semester semester = new Semester();
        semester.name = "2026-2027学年第一学期";
        data.semesters.add(semester);
        data.currentSemesterId = semester.id;
        data.courses.add(course("高等数学D上"));

        assertFalse(ScheduleStore.applyUpgradeReset(data));

        assertEquals(1, data.courses.size());
        assertTrue(data.prefs.onboarded);
    }
}
