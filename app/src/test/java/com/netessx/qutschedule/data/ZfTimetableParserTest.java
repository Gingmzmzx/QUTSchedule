package com.netessx.qutschedule.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.netessx.qutschedule.model.Course;
import com.netessx.qutschedule.model.CourseType;

import org.junit.Test;

import java.util.Arrays;

/** 正方数据中台课表 JSON 的解析：定时课、未排课、去重与错误处理。 */
public class ZfTimetableParserTest {

    private static final String ROW_A1 = "{"
            + "\"KCMC\":\"测试课程A\",\"KCDM\":\"BK00000001\",\"XQJ\":\"1\",\"KSJC\":\"1\",\"JSJC\":\"2\","
            + "\"SKZC\":\"4,5,6,7,8\",\"SKDD\":\"A101\",\"JXBMC\":\"(2026-2027-1)-BK00000001-01\","
            + "\"XF\":\"3.5\",\"KCXZMC\":\"学科基础课\",\"KCLBMC\":\"理论类\","
            + "\"XN\":\"2026-2027\",\"XQ\":\"1\"}";

    private static final String ROW_A2 = "{"
            + "\"KCMC\":\"测试课程A\",\"KCDM\":\"BK00000001\",\"XQJ\":\"3\",\"KSJC\":\"3\",\"JSJC\":\"4\","
            + "\"SKZC\":\"6,8,10\",\"SKDD\":\"B202\",\"JXBMC\":\"(2026-2027-1)-BK00000001-01\","
            + "\"XF\":\"3.5\",\"KCXZMC\":\"学科基础课\",\"KCLBMC\":\"理论类\","
            + "\"XN\":\"2026-2027\",\"XQ\":\"1\"}";

    private static final String ROW_UNSCHEDULED = "{"
            + "\"KCMC\":\"实践课程B\",\"KCDM\":\"BK00000002\",\"XQJ\":\"\",\"KSJC\":\"\",\"JSJC\":\"\","
            + "\"SKZC\":\"\",\"SKDD\":\"\",\"JXBMC\":\"(2026-2027-1)-BK00000002-01\","
            + "\"XF\":\"1\",\"KCXZMC\":\"实践环节\",\"KCLBMC\":\"\","
            + "\"XN\":\"2026-2027\",\"XQ\":\"1\"}";

    private static String response(String... rows) {
        return "{\"results\":{\"status\":200,\"msg\":\"successful\",\"total\":" + rows.length
                + ",\"data\":{\"Total\":" + rows.length + ",\"Rows\":["
                + String.join(",", rows) + "]}}}";
    }

    @Test
    public void parsesScheduledCourses() {
        ZfTimetableParser.ParseResult result = ZfTimetableParser.parse(
                response(ROW_A1, ROW_A2, ROW_UNSCHEDULED));

        assertTrue(result.isOk());
        assertEquals(2, result.courses.size());
        assertEquals(1, result.unscheduled.size());

        Course first = result.courses.get(0);
        assertEquals("测试课程A", first.name);
        assertEquals(CourseType.COURSE, first.type);
        assertEquals(1, first.dayOfWeek);
        assertEquals(1, first.startSlot);
        assertEquals(2, first.endSlot);
        assertEquals(Arrays.asList(4, 5, 6, 7, 8), first.weeks);
        assertEquals("4-8周", first.weekSpec);
        assertEquals("A101", first.location);
        assertEquals("(2026-2027-1)-BK00000001-01", first.teachingClass);
        assertEquals(3.5, first.credit, 0.0001);
        assertFalse(first.lab);

        Course second = result.courses.get(1);
        assertEquals(3, second.dayOfWeek);
        assertEquals(3, second.startSlot);
        assertEquals(Arrays.asList(6, 8, 10), second.weeks);
        assertEquals("6,8,10周", second.weekSpec);

        Course unscheduled = result.unscheduled.get(0);
        assertEquals("实践课程B", unscheduled.name);
        assertEquals(0, unscheduled.dayOfWeek);
        assertTrue(unscheduled.isUnscheduled());
        assertEquals("未排课", unscheduled.note);
    }

    @Test
    public void keepsTermNameFromRows() {
        ZfTimetableParser.ParseResult result = ZfTimetableParser.parse(response(ROW_A1));
        assertEquals("2026-2027学年第一学期", result.termName);
    }

    @Test
    public void deduplicatesIdenticalRows() {
        ZfTimetableParser.ParseResult result = ZfTimetableParser.parse(
                response(ROW_A1, ROW_A1, ROW_A2));
        assertEquals(2, result.courses.size());
    }

    @Test
    public void acceptsRangeStyleWeeks() {
        String row = ROW_A1.replace("\"SKZC\":\"4,5,6,7,8\"", "\"SKZC\":\"1-16\"");
        ZfTimetableParser.ParseResult result = ZfTimetableParser.parse(response(row));
        assertEquals(16, result.courses.get(0).weeks.size());
        assertEquals(1, (int) result.courses.get(0).weeks.get(0));
        assertEquals(16, (int) result.courses.get(0).weeks.get(15));
    }

    @Test
    public void rejectsInvalidJson() {
        ZfTimetableParser.ParseResult result = ZfTimetableParser.parse("not a json");
        assertFalse(result.isOk());
        assertNotNull(result.error);
    }

    @Test
    public void reportsApiError() {
        ZfTimetableParser.ParseResult result = ZfTimetableParser.parse(
                "{\"results\":{\"status\":500,\"msg\":\"denied\"}}");
        assertFalse(result.isOk());
        assertTrue(result.error.contains("denied"));
    }

    @Test
    public void rejectsEmptyRows() {
        ZfTimetableParser.ParseResult result = ZfTimetableParser.parse(response());
        assertFalse(result.isOk());
        assertNotNull(result.error);
    }
}
