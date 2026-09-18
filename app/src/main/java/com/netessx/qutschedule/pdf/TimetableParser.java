package com.netessx.qutschedule.pdf;

import com.netessx.qutschedule.model.Course;
import com.netessx.qutschedule.model.CourseType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把教务系统课表 PDF 还原成 {@link Course} 列表。
 *
 * <p>版式是固定四列：最左为星期，其次节次，再往右是课程名，最右是「周数/校区/地点/教师/教学班」明细。
 * 生成器按阅读顺序逐格输出文本，因此按横坐标判断列、按出现顺序切换星期即可稳定还原。
 */
public final class TimetableParser {

    private static final float COL_DAY_MAX = 70f;
    private static final float COL_SLOT_MAX = 140f;
    private static final float COL_NAME_MAX = 300f;

    /** 解析时还不知道学期总周数，先按这个上限截断周次。 */
    private static final int MAX_WEEK = 30;

    private static final Pattern DAY_LABEL = Pattern.compile("^星期([一二三四五六日天])$");
    private static final Pattern SLOT_LABEL = Pattern.compile("^(\\d+)\\s*-\\s*(\\d+)$");
    private static final Pattern SHARED_WEEKS = Pattern.compile("\\(共(\\d+)周\\)");
    private static final String MARKERS = "★○●◇◆";

    private TimetableParser() {
    }

    /** 解析结果。 */
    public static class ParseResult {
        public String termName = "";
        public final List<Course> courses = new ArrayList<>();
        public final List<Course> unscheduled = new ArrayList<>();
        public String error;

        public boolean isOk() {
            return error == null;
        }
    }

    private static class Entry {
        int day;
        int startSlot = 1;
        int endSlot = 2;
        String name;
        final StringBuilder detail = new StringBuilder();
    }

    public static ParseResult parse(byte[] pdf) {
        ParseResult result = new ParseResult();
        List<TextRun> runs;
        try {
            runs = PdfTextExtractor.extract(pdf);
        } catch (IOException e) {
            result.error = e.getMessage();
            return result;
        }
        if (runs.isEmpty()) {
            result.error = "PDF 中没有可提取的文字";
            return result;
        }

        float minDayY = Float.MAX_VALUE;
        float maxDayY = -Float.MAX_VALUE;
        for (TextRun run : runs) {
            if (DAY_LABEL.matcher(run.text.trim()).matches()) {
                minDayY = Math.min(minDayY, run.y);
                maxDayY = Math.max(maxDayY, run.y);
            }
        }
        if (maxDayY < minDayY) {
            result.error = "没找到星期列，可能不是教务系统导出的课表";
            return result;
        }

        List<Entry> entries = new ArrayList<>();
        List<TextRun> footerRuns = new ArrayList<>();
        Entry current = null;
        int day = 0;
        int slotStart = 0;
        int slotEnd = 0;
        StringBuilder header = new StringBuilder();

        for (TextRun run : runs) {
            String text = run.text.trim();
            if (text.isEmpty()) {
                continue;
            }
            if (run.x < COL_DAY_MAX) {
                Matcher dayMatcher = DAY_LABEL.matcher(text);
                if (dayMatcher.matches()) {
                    day = dayOf(dayMatcher.group(1));
                    current = null;
                    continue;
                }
                if (run.y < minDayY - 5f) {
                    if (text.indexOf('/') >= 0 && !text.contains("上机")) {
                        footerRuns.add(run);
                    }
                } else if (text.contains("学年") && header.length() == 0) {
                    header.append(text);
                }
                continue;
            }
            if (run.x < COL_SLOT_MAX) {
                Matcher slot = SLOT_LABEL.matcher(text);
                if (slot.matches()) {
                    slotStart = parseInt(slot.group(1));
                    slotEnd = parseInt(slot.group(2));
                }
                continue;
            }
            if (run.x < COL_NAME_MAX) {
                current = new Entry();
                current.day = day;
                current.startSlot = slotStart > 0 ? slotStart : 1;
                current.endSlot = slotEnd >= slotStart ? slotEnd : current.startSlot;
                current.name = text;
                entries.add(current);
                continue;
            }
            if (current != null) {
                current.detail.append(text);
            }
        }

        result.termName = header.toString();

        for (Entry entry : entries) {
            if (entry.day < 1) {
                continue;
            }
            result.courses.add(toCourse(entry));
        }
        parseUnscheduled(footerText(footerRuns), result.unscheduled);

        if (result.courses.isEmpty() && result.unscheduled.isEmpty()) {
            result.error = "没有解析到任何课程";
        }
        return result;
    }

    private static Course toCourse(Entry entry) {
        Map<String, String> fields = splitFields(entry.detail.toString());

        Course course = new Course();
        course.type = CourseType.COURSE;
        course.dayOfWeek = entry.day;
        course.startSlot = entry.startSlot;
        course.endSlot = Math.max(entry.startSlot, entry.endSlot);

        String rawName = entry.name == null ? "" : entry.name.trim();
        StringBuilder note = new StringBuilder();
        int last = rawName.length() - 1;
        char marker = 0;
        if (last >= 0 && MARKERS.indexOf(rawName.charAt(last)) >= 0) {
            marker = rawName.charAt(last);
            note.append(markerMeaning(marker));
            rawName = rawName.substring(0, last).trim();
        }
        course.name = rawName;
        course.teacher = value(fields, "教师");
        course.location = value(fields, "地点");
        course.campus = value(fields, "校区");
        course.teachingClass = value(fields, "教学班");
        course.weekSpec = value(fields, "周数");
        course.weeks = WeekSpec.parse(course.weekSpec, MAX_WEEK);

        // 结构化字段：明细里直接有就取，没有再从备注文本里按中英文写法识别
        course.assessment = value(fields, "考核方式");
        course.credit = parseCredit(value(fields, "学分"));
        course.lab = marker == '○' || marker == '●';
        Structured.fillFromNotes(course, entry.detail.toString());

        if (!course.assessment.isEmpty() && !"无".equals(course.assessment)) {
            if (note.length() > 0) {
                note.append(" · ");
            }
            note.append(course.assessment);
        }
        course.note = note.toString();
        return course;
    }

    private static double parseCredit(String text) {
        if (text == null || text.isEmpty()) {
            return -1;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 明细串形如 {@code 周数: 4-17周/校区: 黄岛校区/地点: B104/教师: 刘玉香/...}。 */
    private static Map<String, String> splitFields(String detail) {
        Map<String, String> map = new LinkedHashMap<>();
        if (detail == null || detail.isEmpty()) {
            return map;
        }
        for (String part : detail.split("/")) {
            int colon = part.indexOf(':');
            if (colon < 0) {
                colon = part.indexOf('：');
            }
            if (colon <= 0) {
                continue;
            }
            String key = part.substring(0, colon).trim();
            String value = part.substring(colon + 1).trim();
            if (!key.isEmpty() && !map.containsKey(key)) {
                map.put(key, value);
            }
        }
        return map;
    }

    private static String value(Map<String, String> fields, String key) {
        String v = fields.get(key);
        return v == null ? "" : v;
    }

    private static String footerText(List<TextRun> footerRuns) {
        if (footerRuns.isEmpty()) {
            return "";
        }
        List<TextRun> sorted = new ArrayList<>(footerRuns);
        sorted.sort((a, b) -> Float.compare(b.y, a.y));
        StringBuilder sb = new StringBuilder();
        for (TextRun run : sorted) {
            sb.append(run.text.trim());
        }
        String text = sb.toString();
        int label = text.indexOf("其他课程");
        if (label >= 0) {
            int colon = text.indexOf('：', label);
            if (colon < 0) {
                colon = text.indexOf(':', label);
            }
            text = colon >= 0 ? text.substring(colon + 1) : text.substring(label + 4);
        }
        return text;
    }

    /** 底部「其他课程」形如 {@code 课程名(共4周)/8-11周/无;  课程名(共1周)/第1周/无;}。 */
    private static void parseUnscheduled(String raw, List<Course> out) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        for (String item : raw.split(";")) {
            String entry = item.trim();
            if (entry.isEmpty()) {
                continue;
            }
            String[] parts = entry.split("/");
            String head = parts[0].trim();
            if (head.isEmpty()) {
                continue;
            }
            Course course = new Course();
            course.type = CourseType.COURSE;
            course.dayOfWeek = 0;
            course.name = SHARED_WEEKS.matcher(head).replaceAll("").trim();
            course.weekSpec = parts.length > 1 ? parts[1].trim() : "";
            course.location = parts.length > 2 ? parts[2].trim() : "";
            course.weeks = WeekSpec.parse(course.weekSpec, 40);
            course.note = "未排课";

            Matcher shared = SHARED_WEEKS.matcher(head);
            if (shared.find()) {
                course.note = "共" + shared.group(1) + "周 · 未排课";
            }
            out.add(course);
        }
    }

    private static String markerMeaning(char marker) {
        switch (marker) {
            case '★': return "理论";
            case '○': return "实验";
            case '●': return "实践";
            case '◇': return "上机";
            case '◆': return "讨论";
            default: return "";
        }
    }

    private static int dayOf(String ch) {
        switch (ch) {
            case "一": return 1;
            case "二": return 2;
            case "三": return 3;
            case "四": return 4;
            case "五": return 5;
            case "六": return 6;
            default: return 7;
        }
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
