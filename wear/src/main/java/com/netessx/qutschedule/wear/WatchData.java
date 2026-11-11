package com.netessx.qutschedule.wear;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 手表端的课表：只解析用得到的字段，不依赖手机模块、不依赖 Gson。
 *
 * <p>数据来自手机导出的 {@code schedule.json}（AppData v2 子集）。手表没有 GMS，
 * 走不了 Wearable Data Layer，所以用 adb 把文件推到应用自己的外部私有目录：
 * {@code /sdcard/Android/data/com.netessx.qutschedule.wear/files/schedule.json}。
 */
final class WatchData {

    private static final String TAG = "WatchData";

    /** 一个大节的时间；{@code floorFrom > 0} 表示该楼层及以上用另一套时间。 */
    static final class Slot {
        LocalTime start;
        LocalTime end;
        int floorFrom;
        LocalTime floorStart;
        LocalTime floorEnd;
    }

    static final class Scheme {
        final List<Slot> slots = new ArrayList<>();
        String from;
        String to;
    }

    static final class Course {
        String id = "";
        String name = "";
        String location = "";
        String date;
        LocalTime startTime;
        LocalTime endTime;
        int dayOfWeek;
        int startSlot = 1;
        int endSlot = 2;
        final List<Integer> weeks = new ArrayList<>();
    }

    static final class Shift {
        String type = "MOVE";
        String courseId = "";
        String fromDate = "";
        String toDate;
        int startSlot;
        int endSlot;
    }

    static final class Rule {
        String type = "OFF";
        String date = "";
        String endDate;
        String refDate;
    }

    /** 某天的一条课：课程 + 这一节的起止时间。 */
    static final class Occurrence {
        Course course;
        LocalTime start;
        LocalTime end;

        Occurrence(Course course, LocalTime start, LocalTime end) {
            this.course = course;
            this.start = start;
            this.end = end;
        }

        boolean ongoing(LocalTime now) {
            return !now.isBefore(start) && now.isBefore(end);
        }
    }

    String semesterName = "";
    LocalDate startMonday;
    int totalWeeks = 20;

    private final List<Course> courses = new ArrayList<>();
    private final List<Shift> shifts = new ArrayList<>();
    private final List<Rule> rules = new ArrayList<>();
    private final List<Scheme> schemes = new ArrayList<>();

    private WatchData() {
    }

    /**
     * 读取课表：外部文件（adb push）与内部文件（蓝牙接收）谁的更新用谁。
     *
     * <p>早先无条件用外部文件，导致蓝牙刚收到的课表在下次启动时被旧的 adb 文件覆盖。
     */
    static WatchData load(Context ctx) {
        File external = new File(ctx.getExternalFilesDir(null), "schedule.json");
        File internal = new File(ctx.getFilesDir(), "schedule.json");
        try {
            if (external.exists()
                    && (!internal.exists() || external.lastModified() > internal.lastModified())) {
                copy(external, internal);
            }
        } catch (Exception e) {
            Log.w(TAG, "同步课表文件失败", e);
        }
        if (!internal.exists() || internal.length() == 0) {
            return null;
        }
        try {
            return parse(internal);
        } catch (Exception e) {
            Log.w(TAG, "解析课表失败", e);
            return null;
        }
    }

    private static void copy(File from, File to) throws Exception {
        // 文件没变就不重复写，省得每次启动都碰一次闪存
        if (to.exists() && to.length() == from.length()
                && to.lastModified() == from.lastModified()) {
            return;
        }
        try (InputStream in = new FileInputStream(from);
             FileOutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        to.setLastModified(from.lastModified());
    }

    private static WatchData parse(File file) throws Exception {
        WatchData data = new WatchData();
        JSONObject root = new JSONObject(new String(readAll(file), "UTF-8"));

        JSONArray semesters = root.optJSONArray("semesters");
        String currentId = root.optString("currentSemesterId", "");
        JSONObject semester = null;
        if (semesters != null) {
            for (int i = 0; i < semesters.length(); i++) {
                JSONObject item = semesters.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                if (semester == null) {
                    semester = item;
                }
                if (currentId.equals(item.optString("id"))) {
                    semester = item;
                    break;
                }
            }
        }
        if (semester != null) {
            data.semesterName = semester.optString("name", "");
            data.startMonday = parseDate(semester.optString("startDate", ""));
            data.totalWeeks = Math.max(1, semester.optInt("totalWeeks", 20));
        }

        JSONArray schemes = root.optJSONArray("schemes");
        if (schemes != null) {
            for (int i = 0; i < schemes.length(); i++) {
                JSONObject item = schemes.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                Scheme scheme = new Scheme();
                scheme.from = item.optString("fromMonthDay", "");
                scheme.to = item.optString("toMonthDay", "");
                JSONArray slots = item.optJSONArray("slots");
                if (slots != null) {
                    for (int j = 0; j < slots.length(); j++) {
                        JSONObject raw = slots.optJSONObject(j);
                        if (raw == null) {
                            continue;
                        }
                        Slot slot = new Slot();
                        slot.start = parseTime(raw.optString("start", ""));
                        slot.end = parseTime(raw.optString("end", ""));
                        slot.floorFrom = raw.optInt("floorFrom", 0);
                        slot.floorStart = parseTime(raw.optString("floorStart", ""));
                        slot.floorEnd = parseTime(raw.optString("floorEnd", ""));
                        scheme.slots.add(slot);
                    }
                }
                if (!scheme.slots.isEmpty()) {
                    data.schemes.add(scheme);
                }
            }
        }

        JSONArray courses = root.optJSONArray("courses");
        if (courses != null) {
            for (int i = 0; i < courses.length(); i++) {
                JSONObject raw = courses.optJSONObject(i);
                if (raw == null) {
                    continue;
                }
                Course course = new Course();
                course.id = raw.optString("id", "");
                course.name = raw.optString("name", "");
                course.location = raw.optString("location", "");
                course.date = emptyToNull(raw.optString("date", ""));
                course.startTime = parseTime(raw.optString("startTime", ""));
                course.endTime = parseTime(raw.optString("endTime", ""));
                course.dayOfWeek = raw.optInt("dayOfWeek", 0);
                course.startSlot = Math.max(1, raw.optInt("startSlot", 1));
                course.endSlot = Math.max(course.startSlot,
                        raw.optInt("endSlot", course.startSlot));
                JSONArray weeks = raw.optJSONArray("weeks");
                if (weeks != null) {
                    for (int j = 0; j < weeks.length(); j++) {
                        course.weeks.add(weeks.optInt(j));
                    }
                }
                data.courses.add(course);
            }
        }

        JSONArray shifts = root.optJSONArray("shifts");
        if (shifts != null) {
            for (int i = 0; i < shifts.length(); i++) {
                JSONObject raw = shifts.optJSONObject(i);
                if (raw == null) {
                    continue;
                }
                Shift shift = new Shift();
                shift.type = raw.optString("type", "MOVE");
                shift.courseId = raw.optString("courseId", "");
                shift.fromDate = raw.optString("fromDate", "");
                shift.toDate = emptyToNull(raw.optString("toDate", ""));
                shift.startSlot = raw.optInt("startSlot", 0);
                shift.endSlot = raw.optInt("endSlot", 0);
                if (!shift.courseId.isEmpty() && parseDate(shift.fromDate) != null) {
                    data.shifts.add(shift);
                }
            }
        }

        JSONArray rules = root.optJSONArray("dayRules");
        if (rules != null) {
            for (int i = 0; i < rules.length(); i++) {
                JSONObject raw = rules.optJSONObject(i);
                if (raw == null) {
                    continue;
                }
                Rule rule = new Rule();
                rule.type = raw.optString("type", "OFF");
                rule.date = raw.optString("date", "");
                rule.endDate = raw.optString("endDate", "");
                rule.refDate = raw.optString("refDate", "");
                if (parseDate(rule.date) != null) {
                    data.rules.add(rule);
                }
            }
        }
        return data;
    }

    // ---------------- 查询 ----------------

    boolean configured() {
        return startMonday != null;
    }

    int weekOf(LocalDate date) {
        if (startMonday == null) {
            return 0;
        }
        return (int) Math.floorDiv(ChronoUnit.DAYS.between(startMonday, date), 7L) + 1;
    }

    /** 当天是否放假。 */
    boolean isOff(LocalDate date) {
        for (Rule rule : rules) {
            if ("SWAP".equals(rule.type)) {
                continue;
            }
            LocalDate start = parseDate(rule.date);
            LocalDate end = parseDate(rule.endDate);
            if (start == null) {
                continue;
            }
            if (end == null || end.isBefore(start)) {
                end = start;
            }
            if (!date.isBefore(start) && !date.isAfter(end)) {
                return true;
            }
        }
        return false;
    }

    /** 调休要照搬的那一天；没有则 null。 */
    private LocalDate swapReference(LocalDate date) {
        LocalDate found = null;
        for (Rule rule : rules) {
            if (!"SWAP".equals(rule.type) || !date.toString().equals(rule.date)) {
                continue;
            }
            LocalDate ref = parseDate(rule.refDate);
            if (ref != null) {
                found = ref;
            }
        }
        return found;
    }

    /** 某天的全部课，已按开始时间排序。 */
    List<Occurrence> occurrences(LocalDate date) {
        List<Occurrence> out = new ArrayList<>();
        if (startMonday == null || isOff(date)) {
            return out;
        }
        LocalDate reference = swapReference(date);
        LocalDate matchDate = reference == null ? date : reference;
        int week = weekOf(matchDate);

        for (Course course : courses) {
            // 这节课在这一天被临时停掉或挪走了
            if (shiftOn(course.id, date) != null) {
                continue;
            }
            if (!matches(course, matchDate, week)) {
                continue;
            }
            out.add(new Occurrence(course, startOf(course, date), endOf(course, date)));
        }

        // 临时挪到这一天的课
        for (Shift shift : shifts) {
            if (!"MOVE".equals(shift.type) || !date.toString().equals(shift.toDate)) {
                continue;
            }
            Course origin = courseById(shift.courseId);
            if (origin == null) {
                continue;
            }
            // 只改了起始节次时，结束节次不能比它早（和手机端 endSlotOf 的处理一致）
            int startSlot = shift.startSlot > 0 ? shift.startSlot : origin.startSlot;
            int endSlot = Math.max(startSlot,
                    shift.endSlot > 0 ? shift.endSlot : origin.endSlot);
            if (origin.startTime != null && origin.endTime != null) {
                // 单次日程本身带具体时间，调课不改时间
                out.add(new Occurrence(origin, origin.startTime, origin.endTime));
            } else {
                out.add(new Occurrence(origin,
                        startOfSlot(origin, date, startSlot),
                        endOfSlot(origin, date, endSlot)));
            }
        }

        Collections.sort(out, new Comparator<Occurrence>() {
            @Override
            public int compare(Occurrence a, Occurrence b) {
                return a.start.compareTo(b.start);
            }
        });
        return out;
    }

    private boolean matches(Course course, LocalDate date, int week) {
        if (course.date != null) {
            return date.toString().equals(course.date);
        }
        if (course.dayOfWeek < 1 || course.dayOfWeek != date.getDayOfWeek().getValue()) {
            return false;
        }
        // 周次为空按「每周都上」处理，和手机端一致
        return course.weeks.isEmpty() || course.weeks.contains(week);
    }

    private Shift shiftOn(String courseId, LocalDate date) {
        for (Shift shift : shifts) {
            if (shift.courseId.equals(courseId) && date.toString().equals(shift.fromDate)) {
                return shift;
            }
        }
        return null;
    }

    private Course courseById(String id) {
        for (Course course : courses) {
            if (course.id.equals(id)) {
                return course;
            }
        }
        return null;
    }

    private LocalTime startOf(Course course, LocalDate date) {
        if (course.startTime != null) {
            return course.startTime;
        }
        return startOfSlot(course, date, course.startSlot);
    }

    private LocalTime endOf(Course course, LocalDate date) {
        if (course.endTime != null) {
            return course.endTime;
        }
        return endOfSlot(course, date, course.endSlot);
    }

    private LocalTime startOfSlot(Course course, LocalDate date, int slotNumber) {
        Slot slot = slotOf(date, slotNumber);
        LocalTime start = highFloor(slot, course) && slot.floorStart != null
                ? slot.floorStart : slot.start;
        return start == null ? LocalTime.of(8, 0) : start;
    }

    private LocalTime endOfSlot(Course course, LocalDate date, int slotNumber) {
        Slot slot = slotOf(date, slotNumber);
        LocalTime end = highFloor(slot, course) && slot.floorEnd != null
                ? slot.floorEnd : slot.end;
        return end == null ? LocalTime.of(9, 40) : end;
    }

    /** 教室楼层是否落在这一大节的错峰范围内（如 4 层及以上）。 */
    private static boolean highFloor(Slot slot, Course course) {
        return slot.floorFrom > 0 && floorOf(course.location) >= slot.floorFrom;
    }

    private Slot slotOf(LocalDate date, int slotNumber) {
        Scheme scheme = schemeFor(date);
        Slot fallback = new Slot();
        fallback.start = LocalTime.of(8, 0);
        fallback.end = LocalTime.of(9, 40);
        if (scheme == null || scheme.slots.isEmpty()) {
            return fallback;
        }
        int band = Math.max(0, (Math.max(1, Math.min(10, slotNumber)) - 1) / 2);
        return scheme.slots.get(Math.min(band, scheme.slots.size() - 1));
    }

    /** 按「月-日」区间挑方案，都不匹配就用第一套。 */
    private Scheme schemeFor(LocalDate date) {
        for (Scheme scheme : schemes) {
            if (scheme.from == null || scheme.from.isEmpty()
                    || scheme.to == null || scheme.to.isEmpty()) {
                continue;
            }
            if (inRange(date, scheme.from, scheme.to)) {
                return scheme;
            }
        }
        return schemes.isEmpty() ? null : schemes.get(0);
    }

    private static boolean inRange(LocalDate date, String from, String to) {
        try {
            int start = Integer.parseInt(from.substring(0, 2)) * 100
                    + Integer.parseInt(from.substring(3, 5));
            int end = Integer.parseInt(to.substring(0, 2)) * 100
                    + Integer.parseInt(to.substring(3, 5));
            int value = date.getMonthValue() * 100 + date.getDayOfMonth();
            // 支持跨年区间，如 10-01 ~ 04-30
            return start <= end ? value >= start && value <= end : value >= start || value <= end;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 从教室号里取楼层：A302 → 3，B1-401 → 4；认不出返回 0。 */
    static int floorOf(String location) {
        if (location == null || location.isEmpty()) {
            return 0;
        }
        int start = -1;
        int i = 0;
        while (i < location.length()) {
            if (!Character.isDigit(location.charAt(i))) {
                i++;
                continue;
            }
            int end = i;
            while (end < location.length() && Character.isDigit(location.charAt(end))) {
                end++;
            }
            // 只认三位以上的数字串，免得把「3 号教学楼」当成楼层
            if (end - i >= 3) {
                start = i;
            }
            i = end;
        }
        if (start < 0) {
            return 0;
        }
        int floor = location.charAt(start) - '0';
        return floor >= 1 ? floor : 0;
    }

    /** 从今天往后找最近一节还没开始的课；找不到返回 null。 */
    Occurrence next(LocalDate today, LocalTime now) {
        if (startMonday == null) {
            return null;
        }
        for (int offset = 0; offset < 8; offset++) {
            LocalDate date = today.plusDays(offset);
            for (Occurrence occurrence : occurrences(date)) {
                if (offset == 0 && !occurrence.start.isAfter(now)) {
                    continue;
                }
                return occurrence;
            }
        }
        return null;
    }

    // ---------------- 小工具 ----------------

    private static byte[] readAll(File file) throws Exception {
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static LocalDate parseDate(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static LocalTime parseTime(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return LocalTime.parse(text);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String emptyToNull(String text) {
        return text == null || text.isEmpty() ? null : text;
    }
}
