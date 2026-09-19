package com.netessx.qutschedule.model;

import com.netessx.qutschedule.util.TimeSlots;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 一套作息方案（默认 / 夏令时 / 冬令时 / 自定义）。
 *
 * <p>每个 {@link Slot} 是一个「大节」，覆盖两个小节：第 i 个大节对应第 2i+1、2i+2 小节，
 * 因此课程里记录的 1..2N 小节号可以直接映射回来。
 *
 * <p>{@code fromMonthDay}/{@code toMonthDay} 为空表示不参与自动切换；填写后按「月-日」区间匹配，
 * 支持跨年（如 10-01 ~ 04-30）。
 */
public class TimeScheme {

    public static final String DEFAULT_ID = "default";

    public String id = UUID.randomUUID().toString();
    public String name = "";
    public List<Slot> slots = new ArrayList<>();
    /** 生效区间起，格式 MM-dd，可空。 */
    public String fromMonthDay;
    /** 生效区间止，格式 MM-dd，可空。 */
    public String toMonthDay;
    public boolean builtin;

    /**
     * 一个大节。
     *
     * <p>有些教学楼的同一节课按楼层错峰上下课（黄岛校区上午三四节就是这样），
     * {@code floorFrom} 填起始楼层后，该楼层及以上改用 {@code floorStart}/{@code floorEnd}。
     */
    public static class Slot {
        public String start;
        public String end;
        /** 从这一层起（含）走另一套时间；0 表示不按楼层区分。 */
        public int floorFrom;
        public String floorStart;
        public String floorEnd;

        public Slot() {
        }

        public Slot(String start, String end) {
            this.start = start;
            this.end = end;
        }

        public boolean hasFloorSplit() {
            return floorFrom > 0;
        }

        /** 给定楼层是否走另一套时间；楼层未知（0）时一律按普通时间。 */
        public boolean usesFloorTime(int floor) {
            return hasFloorSplit() && floor >= floorFrom;
        }

        public String startFor(int floor) {
            return usesFloorTime(floor) ? floorStart : start;
        }

        public String endFor(int floor) {
            return usesFloorTime(floor) ? floorEnd : end;
        }

        public Slot copy() {
            Slot s = new Slot(start, end);
            s.floorFrom = floorFrom;
            s.floorStart = floorStart;
            s.floorEnd = floorEnd;
            return s;
        }

        public void normalize() {
            if (start == null || start.isEmpty()) {
                start = TimeSlots.DEFAULT_START[0];
            }
            if (end == null || end.isEmpty()) {
                end = TimeSlots.DEFAULT_END[0];
            }
            // 楼层时间写坏或不完整就整条撤掉，免得算出个不存在的时间
            if (floorFrom > 0
                    && TimeSlots.minutes(floorStart) >= 0 && TimeSlots.minutes(floorEnd) >= 0) {
                return;
            }
            floorFrom = 0;
            floorStart = null;
            floorEnd = null;
        }
    }

    public static TimeScheme defaults() {
        TimeScheme scheme = new TimeScheme();
        scheme.id = DEFAULT_ID;
        scheme.name = "默认";
        scheme.builtin = true;
        for (int i = 0; i < TimeSlots.BANDS; i++) {
            scheme.slots.add(new Slot(TimeSlots.DEFAULT_START[i], TimeSlots.DEFAULT_END[i]));
        }
        // 黄岛校区：上午三四节 4 层及以上错峰，晚 15 分钟上下课
        Slot morning = scheme.slots.get(1);
        morning.floorFrom = 4;
        morning.floorStart = "10:20";
        morning.floorEnd = "12:10";
        return scheme;
    }

    public int count() {
        return Math.max(1, slots.size());
    }

    public int bandOf(int slot) {
        int band = (TimeSlots.clamp(slot) - 1) / 2;
        return Math.min(band, count() - 1);
    }

    public String labelOf(int slot) {
        int band = bandOf(slot);
        return (band * 2 + 1) + "-" + (band * 2 + 2);
    }

    public String startOf(int slot) {
        return startOf(slot, 0);
    }

    public String endOf(int slot) {
        return endOf(slot, 0);
    }

    /** 给定楼层的开始时间，见 {@link Slot#usesFloorTime(int)}；楼层未知传 0。 */
    public String startOf(int slot, int floor) {
        return slotAt(bandOf(slot)).startFor(floor);
    }

    public String endOf(int slot, int floor) {
        return slotAt(bandOf(slot)).endFor(floor);
    }

    private Slot slotAt(int band) {
        if (slots.isEmpty()) {
            slots.add(new Slot(TimeSlots.DEFAULT_START[0], TimeSlots.DEFAULT_END[0]));
        }
        return slots.get(Math.max(0, Math.min(band, slots.size() - 1)));
    }

    /** 该方案在给定日期是否生效。区间为空时永远生效。 */
    public boolean activeOn(LocalDate date) {
        MonthDay from = parse(fromMonthDay);
        MonthDay to = parse(toMonthDay);
        if (from == null || to == null) {
            return true;
        }
        MonthDay day = MonthDay.of(date.getMonthValue(), date.getDayOfMonth());
        if (from.compareTo(to) <= 0) {
            return day.compareTo(from) >= 0 && day.compareTo(to) <= 0;
        }
        // 跨年区间，如 10-01 ~ 04-30
        return day.compareTo(from) >= 0 || day.compareTo(to) <= 0;
    }

    /**
     * 修改第 index 个大节的结束时间后，把后面所有大节整体顺延，避免逐节手改。
     *
     * @return 实际顺延的分钟数
     */
    public int shiftAfter(int index, int deltaMinutes) {
        if (index < 0 || index >= slots.size() || deltaMinutes == 0) {
            return 0;
        }
        for (int i = index + 1; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            slot.start = shift(slot.start, deltaMinutes);
            slot.end = shift(slot.end, deltaMinutes);
            if (slot.hasFloorSplit()) {
                slot.floorStart = shift(slot.floorStart, deltaMinutes);
                slot.floorEnd = shift(slot.floorEnd, deltaMinutes);
            }
        }
        return deltaMinutes;
    }

    private static String shift(String hhmm, int delta) {
        int minutes = TimeSlots.minutes(hhmm);
        return minutes < 0 ? hhmm : TimeSlots.format(minutes + delta);
    }

    private static MonthDay parse(String monthDay) {
        if (monthDay == null || monthDay.length() < 5) {
            return null;
        }
        try {
            return MonthDay.of(
                    Integer.parseInt(monthDay.substring(0, 2)),
                    Integer.parseInt(monthDay.substring(3, 5)));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public void normalize() {
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }
        if (name == null) {
            name = "";
        }
        if (slots == null) {
            slots = new ArrayList<>();
        }
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot == null) {
                slots.set(i, new Slot(TimeSlots.DEFAULT_START[0], TimeSlots.DEFAULT_END[0]));
            }
        }
        if (slots.isEmpty()) {
            slots.addAll(defaults().slots);
        }
        for (Slot slot : slots) {
            slot.normalize();
        }
        upgradeLegacyTimes();
    }

    /** 老版本内置的作息（黄岛校区换作息前的那套），只用来识别「用户没动过」的节次。 */
    private static final String[] LEGACY_START = {"08:00", "10:00", "14:00", "16:00", "19:00"};
    private static final String[] LEGACY_END = {"09:40", "11:40", "15:40", "17:40", "20:40"};

    /**
     * 把没动过的默认节次升到当前作息，并给上午三四节补上楼层错峰。
     *
     * <p>只认得出还是老内置值的节次才改，用户自己调过时间的方案原样保留。
     */
    private void upgradeLegacyTimes() {
        for (int i = 0; i < slots.size() && i < LEGACY_START.length; i++) {
            Slot slot = slots.get(i);
            if (LEGACY_START[i].equals(slot.start) && LEGACY_END[i].equals(slot.end)) {
                slot.start = TimeSlots.DEFAULT_START[i];
                slot.end = TimeSlots.DEFAULT_END[i];
            }
        }
        if (slots.size() < 2) {
            return;
        }
        // 上午三四节：1–3 层按基础时间，4 层及以上晚 15 分钟
        Slot morning = slots.get(1);
        if (!morning.hasFloorSplit()
                && TimeSlots.DEFAULT_START[1].equals(morning.start)
                && TimeSlots.DEFAULT_END[1].equals(morning.end)) {
            morning.floorFrom = 4;
            morning.floorStart = "10:20";
            morning.floorEnd = "12:10";
        }
    }

    public TimeScheme copy() {
        TimeScheme s = new TimeScheme();
        s.id = id;
        s.name = name;
        s.builtin = builtin;
        s.fromMonthDay = fromMonthDay;
        s.toMonthDay = toMonthDay;
        for (Slot slot : slots) {
            s.slots.add(slot.copy());
        }
        return s;
    }
}
