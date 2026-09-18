package com.netessx.qutschedule.pdf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.netessx.qutschedule.model.Course;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

/** 用仓库里的真实课表验证解析结果。 */
public class TimetableParserTest {

    private static final String PDF_NAME = "徐子越(2026-2027-1)课表.pdf";

    private TimetableParser.ParseResult parseRealTimetable() throws Exception {
        File pdf = new File("..", PDF_NAME);
        Assume.assumeTrue("样例课表不在仓库根目录，跳过", pdf.exists());
        TimetableParser.ParseResult result =
                TimetableParser.parse(Files.readAllBytes(pdf.toPath()));
        assertTrue(result.error == null ? "" : result.error, result.isOk());
        return result;
    }

    @Test
    public void parsesEveryCellOfTheGrid() throws Exception {
        TimetableParser.ParseResult result = parseRealTimetable();

        // 周一 3 条、周二 5 条、周三 3 条、周四 4 条、周五 4 条
        assertEquals(19, result.courses.size());
        assertEquals(6, result.unscheduled.size());
        assertEquals("2026-2027学年第1学期", result.termName);
    }

    @Test
    public void keepsWeekRangesAndSlotOfEachCourse() throws Exception {
        TimetableParser.ParseResult result = parseRealTimetable();

        Course advanced = find(result.courses, "高等数学D上", 1, 1);
        assertNotNull(advanced);
        assertEquals(2, advanced.endSlot);
        assertEquals("刘玉香", advanced.teacher);
        assertEquals("B104", advanced.location);
        assertEquals(Arrays.asList(4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17),
                advanced.weeks);

        // 同一节次的两门课按周次分段，互不重叠
        Course first = find(result.courses, "程序设计基础", 1, 5);
        Course second = find(result.courses, "程序设计基础", 1, 5, 14);
        assertNotNull(first);
        assertNotNull(second);
        assertFalse(first.weeks.contains(14));
        assertTrue(second.weeks.contains(14));
        assertTrue(second.weeks.contains(17));
    }

    @Test
    public void readsSingleWeekAndOddEvenSpecs() throws Exception {
        TimetableParser.ParseResult result = parseRealTimetable();

        Course labour = find(result.courses, "劳动教育基础1", 3, 7);
        assertNotNull(labour);
        assertEquals("第4周", labour.weekSpec);
        assertEquals(Arrays.asList(4), labour.weeks);

        Course odd = find(result.courses, "中国近现代史纲要", 5, 7);
        assertNotNull(odd);
        assertTrue(odd.weekSpec.contains("单"));
        assertEquals(Arrays.asList(5, 7, 9, 11, 13, 15), odd.weeks);

        Course even = find(result.courses, "习近平新时代中国特色社会主义思想概论", 5, 7);
        assertNotNull(even);
        assertTrue(even.weekSpec.contains("双"));
        assertEquals(Arrays.asList(6, 8, 10, 12, 14, 16), even.weeks);
    }

    @Test
    public void collectsUnscheduledCoursesFromFooter() throws Exception {
        TimetableParser.ParseResult result = parseRealTimetable();

        // 底部「其他课程」的课程名与教师名连在一起，没有可靠的分隔符，原样保留
        Course practice = null;
        for (Course course : result.unscheduled) {
            if (course.name.startsWith("公益类劳动实践1")) {
                practice = course;
                break;
            }
        }
        assertNotNull(practice);
        assertEquals(0, practice.dayOfWeek);
        assertEquals("5-16周", practice.weekSpec);
        assertEquals(Arrays.asList(5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16),
                practice.weeks);
        assertEquals("共12周 · 未排课", practice.note);
    }

    @Test
    public void fillsStructuredFieldsFromDetail() throws Exception {
        TimetableParser.ParseResult result = parseRealTimetable();

        Course advanced = find(result.courses, "高等数学D上", 1, 1);
        assertNotNull(advanced);
        assertEquals(5.0, advanced.credit, 0.001);
        assertEquals("考试", advanced.assessment);
        assertFalse(advanced.lab);
        assertEquals("理论 · 考试", advanced.note);

        // 同一节次的后半段名称后缀是 ○，识别为实验课
        Course lab = find(result.courses, "程序设计基础", 1, 5, 14);
        assertNotNull(lab);
        assertEquals(3.5, lab.credit, 0.001);
        assertTrue(lab.lab);
        assertFalse(find(result.courses, "程序设计基础", 1, 5).lab);
    }

    @Test
    public void recognizesStructuredFieldsFromNotes() {
        Course course = new Course();
        course.assessment = "";
        Structured.fillFromNotes(course, "备注：学分 3.5，考核方式：考试，实验课");
        assertEquals(3.5, course.credit, 0.001);
        assertEquals("考试", course.assessment);
        assertTrue(course.lab);

        Course english = new Course();
        english.assessment = "";
        Structured.fillFromNotes(english, "Credit: 2, Assessment: 考查, Lab");
        assertEquals(2.0, english.credit, 0.001);
        assertEquals("考查", english.assessment);
        assertTrue(english.lab);
    }

    @Test
    public void weekSpecHandlesHalfTermSuffixes() {
        assertEquals(Arrays.asList(5, 7, 9), WeekSpec.parse("5-9周(单)", 20));
        assertEquals(Arrays.asList(6, 8), WeekSpec.parse("6-9周(双)", 20));
        assertEquals(Arrays.asList(3), WeekSpec.parse("第3周", 20));
        assertTrue(WeekSpec.parse("无", 20).isEmpty());
        assertEquals("4-6,8周", WeekSpec.describe(Arrays.asList(4, 5, 6, 8)));
    }

    private static Course find(List<Course> courses, String name, int day, int startSlot) {
        return find(courses, name, day, startSlot, -1);
    }

    private static Course find(List<Course> courses, String name, int day, int startSlot,
                               int mustContainWeek) {
        for (Course course : courses) {
            if (!course.name.equals(name) || course.dayOfWeek != day
                    || course.startSlot != startSlot) {
                continue;
            }
            if (mustContainWeek >= 0 && !course.weeks.contains(mustContainWeek)) {
                continue;
            }
            return course;
        }
        return null;
    }
}
