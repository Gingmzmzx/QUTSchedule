package com.netessx.qutschedule.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** schedule.json 的根对象，schema v2。 */
public class AppData {

    public static final int VERSION = 2;

    public int version = VERSION;
    public List<Semester> semesters = new ArrayList<>();
    public String currentSemesterId;
    public List<TimeScheme> schemes = new ArrayList<>();
    public String currentSchemeId = TimeScheme.DEFAULT_ID;
    public List<Course> courses = new ArrayList<>();
    public List<Todo> todos = new ArrayList<>();
    /** 用户自己配置的放假与调休规则，见 {@link DayRule}。 */
    public List<DayRule> dayRules = new ArrayList<>();
    /** 一次性的临时调课 / 停课，见 {@link CourseShift}。 */
    public List<CourseShift> shifts = new ArrayList<>();
    public Profile profile = new Profile();
    public Prefs prefs = new Prefs();

    public Semester currentSemester() {
        Semester semester = semesterById(currentSemesterId);
        if (semester != null) {
            return semester;
        }
        if (semesters.isEmpty()) {
            Semester created = new Semester();
            created.name = "默认学期";
            semesters.add(created);
            currentSemesterId = created.id;
            return created;
        }
        currentSemesterId = semesters.get(0).id;
        return semesters.get(0);
    }

    public Semester semesterById(String id) {
        if (id == null) {
            return null;
        }
        for (Semester semester : semesters) {
            if (id.equals(semester.id)) {
                return semester;
            }
        }
        return null;
    }

    public void setCurrentSemester(String id) {
        if (semesterById(id) != null) {
            currentSemesterId = id;
        }
    }

    public TimeScheme schemeById(String id) {
        if (id == null) {
            return null;
        }
        for (TimeScheme scheme : schemes) {
            if (id.equals(scheme.id)) {
                return scheme;
            }
        }
        return null;
    }

    /** 某天适用的作息：学期指定 > 按「月-日」区间自动切换 > 当前方案 > 默认。 */
    public TimeScheme schemeFor(Semester semester, LocalDate date) {
        if (semester != null && semester.schemeId != null) {
            TimeScheme fixed = schemeById(semester.schemeId);
            if (fixed != null) {
                return fixed;
            }
        }
        if (date != null) {
            for (TimeScheme scheme : schemes) {
                if (hasRange(scheme) && scheme.activeOn(date)) {
                    return scheme;
                }
            }
        }
        TimeScheme current = schemeById(currentSchemeId);
        if (current != null) {
            return current;
        }
        if (schemes.isEmpty()) {
            TimeScheme fallback = TimeScheme.defaults();
            schemes.add(fallback);
            currentSchemeId = fallback.id;
            return fallback;
        }
        return schemes.get(0);
    }

    private static boolean hasRange(TimeScheme scheme) {
        return scheme.fromMonthDay != null && !scheme.fromMonthDay.isEmpty()
                && scheme.toMonthDay != null && !scheme.toMonthDay.isEmpty();
    }

    public void normalize() {
        version = VERSION;
        if (semesters == null) {
            semesters = new ArrayList<>();
        }
        if (schemes == null) {
            schemes = new ArrayList<>();
        }
        if (courses == null) {
            courses = new ArrayList<>();
        }
        if (todos == null) {
            todos = new ArrayList<>();
        }
        if (dayRules == null) {
            dayRules = new ArrayList<>();
        }
        if (shifts == null) {
            shifts = new ArrayList<>();
        }
        // 备份文件或手改过的 JSON 里可能有 null 元素，先剔掉，否则下面取字段就会 NPE
        semesters.removeIf(java.util.Objects::isNull);
        schemes.removeIf(java.util.Objects::isNull);
        courses.removeIf(java.util.Objects::isNull);
        todos.removeIf(java.util.Objects::isNull);
        dayRules.removeIf(java.util.Objects::isNull);
        shifts.removeIf(java.util.Objects::isNull);
        for (DayRule rule : dayRules) {
            rule.normalize();
        }
        for (CourseShift shift : shifts) {
            shift.normalize();
        }

        if (schemes.isEmpty()) {
            TimeScheme fallback = TimeScheme.defaults();
            schemes.add(fallback);
            currentSchemeId = fallback.id;
        }
        for (TimeScheme scheme : schemes) {
            scheme.normalize();
        }
        for (Semester semester : semesters) {
            semester.normalize();
        }
        if (semesters.isEmpty()) {
            Semester created = new Semester();
            created.name = "默认学期";
            semesters.add(created);
            currentSemesterId = created.id;
        }
        if (semesterById(currentSemesterId) == null) {
            currentSemesterId = semesters.get(0).id;
        }
        if (schemeById(currentSchemeId) == null) {
            currentSchemeId = schemes.get(0).id;
        }
        for (int i = 0; i < courses.size(); i++) {
            Course course = courses.get(i);
            if (course.id == null || course.id.isEmpty()) {
                course.id = UUID.randomUUID().toString();
            }
            if (course.weeks == null) {
                course.weeks = new ArrayList<>();
            }
            if (course.type == null) {
                course.type = CourseType.COURSE;
            }
            if (course.assessment == null) {
                course.assessment = "";
            }
            if (course.semesterId == null) {
                course.semesterId = currentSemesterId;
            }
        }
        for (Todo todo : todos) {
            todo.normalize();
        }
        if (profile == null) {
            profile = new Profile();
        }
        profile.normalize();
        if (prefs == null) {
            prefs = new Prefs();
        }
        prefs.normalize();
    }
}
