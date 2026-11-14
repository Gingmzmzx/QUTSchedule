package com.netessx.qutschedule.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.netessx.qutschedule.model.Course;
import com.netessx.qutschedule.model.CourseType;
import com.netessx.qutschedule.pdf.WeekSpec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 解析正方教务系统数据中台返回的课表 JSON。
 *
 * <p>接口为 {@code /portal/api/v2/datacenter/get/xskb}，响应形如：
 * <pre>
 * {"results":{"status":200,"msg":"successful","data":{"Total":28,"Rows":[
 *   {"KCMC":"高等数学","KCDM":"BK10601141","XQJ":"1","KSJC":"1","JSJC":"2",
 *    "SKZC":"4,5,6,7","SKDD":"B104","JXBMC":"(2026-2027-1)-BK10601141-01",
 *    "XF":"5","KCXZMC":"学科基础课","KCLBMC":"理论类", ...}
 * ]}}}
 * </pre>
 *
 * <p>每行是一次上课安排：{@code XQJ} 星期、{@code KSJC}/{@code JSJC} 起止节次、
 * {@code SKZC} 周次（逗号分隔的数字，不是区间写法）。实践环节、入学测试等没有固定时间的行
 * 归到「未排课」，仍然保留，方便用户在课程列表里看到。
 */
public final class ZfTimetableParser {

    /** 学期总周数未知时按这个上限截断。 */
    private static final int MAX_WEEK = 40;

    private static final String[] TERM_NUMBERS = {"一", "二", "三", "四"};

    private ZfTimetableParser() {
    }

    /** 解析结果，与 {@code pdf.TimetableParser.ParseResult} 同构，便于复用导入预览。 */
    public static class ParseResult {
        public String termName = "";
        public final List<Course> courses = new ArrayList<>();
        public final List<Course> unscheduled = new ArrayList<>();
        public String error;

        public boolean isOk() {
            return error == null;
        }
    }

    public static ParseResult parse(String json) {
        ParseResult result = new ParseResult();
        if (json == null || json.trim().isEmpty()) {
            result.error = "没有可解析的课表数据";
            return result;
        }

        JsonElement rootElement;
        try {
            rootElement = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            result.error = "课表数据不是有效的 JSON";
            return result;
        }
        if (!rootElement.isJsonObject()) {
            result.error = "课表数据格式不正确";
            return result;
        }

        JsonObject root = rootElement.getAsJsonObject();
        JsonObject results = root.has("results") && root.get("results").isJsonObject()
                ? root.getAsJsonObject("results") : root;

        int status = optInt(results, "status", 200);
        if (status != 200) {
            result.error = "教务系统返回错误：" + optString(results, "msg", String.valueOf(status));
            return result;
        }

        JsonArray rows = rowsOf(results);
        if (rows == null || rows.isEmpty()) {
            result.error = "没有解析到任何课程";
            return result;
        }

        String termYear = "";
        String termIndex = "";
        Set<String> seen = new HashSet<>();
        for (JsonElement element : rows) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject row = element.getAsJsonObject();
            String name = optString(row, "KCMC", "").trim();
            if (name.isEmpty()) {
                continue;
            }
            if (termYear.isEmpty()) {
                termYear = optString(row, "XN", "").trim();
            }
            if (termIndex.isEmpty()) {
                termIndex = optString(row, "XQ", "").trim();
            }

            int day = optInt(row, "XQJ", 0);
            int startSlot = optInt(row, "KSJC", 0);
            int endSlot = optInt(row, "JSJC", 0);
            String weekSpec = optString(row, "SKZC", "").trim();
            List<Integer> weeks = WeekSpec.parse(weekSpec, MAX_WEEK);
            boolean scheduled = day >= 1 && startSlot >= 1 && !weeks.isEmpty();

            String teachingClass = optString(row, "JXBMC", "").trim();
            String key = name + '|' + teachingClass + '|' + day + '|' + startSlot + '|'
                    + endSlot + '|' + weekSpec;
            if (!seen.add(key)) {
                continue;
            }

            Course course = new Course();
            course.type = CourseType.COURSE;
            course.name = name;
            course.teachingClass = teachingClass;
            course.location = optString(row, "SKDD", "").trim();
            course.credit = optDouble(row, "XF", -1);
            String category = optString(row, "KCLBMC", "").trim();
            course.lab = category.contains("实验") || category.contains("上机");

            if (scheduled) {
                course.dayOfWeek = day;
                course.startSlot = startSlot;
                course.endSlot = Math.max(startSlot, endSlot);
                course.weeks = weeks;
                course.weekSpec = WeekSpec.describe(weeks);
                result.courses.add(course);
            } else {
                // 实践环节 / 入学测试这类没有固定时间的课，保留为「未排课」
                course.dayOfWeek = 0;
                course.weeks = new ArrayList<>();
                course.note = "未排课";
                result.unscheduled.add(course);
            }
        }

        result.termName = termName(termYear, termIndex);
        if (result.courses.isEmpty() && result.unscheduled.isEmpty()) {
            result.error = "没有解析到任何课程";
        }
        return result;
    }

    private static JsonArray rowsOf(JsonObject results) {
        if (results.has("data")) {
            JsonElement data = results.get("data");
            if (data.isJsonObject()) {
                JsonArray rows = optArray(data.getAsJsonObject(), "Rows");
                if (rows != null) {
                    return rows;
                }
            } else if (data.isJsonArray()) {
                return data.getAsJsonArray();
            }
        }
        return optArray(results, "Rows");
    }

    private static String termName(String year, String index) {
        if (year == null || year.isEmpty()) {
            return "";
        }
        int term = 0;
        try {
            term = Integer.parseInt(index);
        } catch (NumberFormatException ignored) {
            // 学期编号缺失时只带学年
        }
        if (term >= 1 && term <= TERM_NUMBERS.length) {
            return year + "学年第" + TERM_NUMBERS[term - 1] + "学期";
        }
        return year + "学年";
    }

    private static JsonArray optArray(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static String optString(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsString();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static int optInt(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static double optDouble(JsonObject object, String key, double fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsDouble();
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
