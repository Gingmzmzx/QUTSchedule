package com.netessx.qutschedule.model;

import com.netessx.qutschedule.util.Dates;

import java.time.LocalDate;
import java.util.List;

/**
 * 用户自己配置的放假 / 调休规则。应用不内置任何节假日数据。
 *
 * <ul>
 *   <li>{@code OFF}：放假、没有课。可以是单日（{@code endDate} 留空或等于 {@code date}），
 *       也可以是一个闭区间 {@code date ~ endDate}。</li>
 *   <li>{@code SWAP}：这一天按另一天的课表上课，对应学校通知里的
 *       「10 月 11 日上 10 月 8 日的课」，即 {@code date=2026-10-11, refDate=2026-10-08}。</li>
 * </ul>
 */
public class DayRule {

    public static final String OFF = "OFF";
    public static final String SWAP = "SWAP";

    public String type = OFF;
    /** 规则生效的起始日期，yyyy-MM-dd。 */
    public String date = "";
    /** 放假日区间的结束日期（含当天）；为空表示只有 {@code date} 这一天。 */
    public String endDate;
    /** {@link #SWAP} 时参照的那一天，yyyy-MM-dd。 */
    public String refDate;

    public DayRule() {
    }

    public DayRule(String type, String date) {
        this.type = type;
        this.date = date;
    }

    public DayRule(String type, String date, String endDate) {
        this.type = type;
        this.date = date;
        this.endDate = endDate;
    }

    public static DayRule swap(String date, String refDate) {
        DayRule rule = new DayRule(SWAP, date);
        rule.refDate = refDate;
        return rule;
    }

    public boolean isSwap() {
        return SWAP.equals(type);
    }

    /** 精确匹配某一天，调休规则与「同一天是否已有规则」都用它。 */
    public boolean matches(String isoDate) {
        return date != null && date.equals(isoDate);
    }

    /** 放假日是否覆盖这一天；调休规则永远返回 false。 */
    public boolean isOffOn(String isoDate) {
        if (isSwap()) {
            return false;
        }
        LocalDate target = Dates.parse(isoDate);
        LocalDate start = Dates.parse(date);
        if (target == null || start == null) {
            return false;
        }
        LocalDate end = Dates.parse(endDate);
        if (end == null || end.isBefore(start)) {
            end = start;
        }
        return !target.isBefore(start) && !target.isAfter(end);
    }

    public LocalDate referenceDate() {
        return Dates.parse(refDate);
    }

    /** 放假日是否跨多天。 */
    public boolean isRange() {
        return !isSwap() && endDate != null && !endDate.isEmpty() && !endDate.equals(date);
    }

    public void normalize() {
        if (date == null) {
            date = "";
        }
        if (endDate == null) {
            endDate = "";
        }
        if (refDate == null) {
            refDate = "";
        }
        if (!SWAP.equals(type)) {
            type = OFF;
        }
        // 参照日无效时退化成普通的一天，避免留下一条永远算不出来的规则
        if (isSwap() && Dates.parse(refDate) == null) {
            type = OFF;
            refDate = "";
        }
        // 区间反向或写错就退回成单日
        if (!isSwap()) {
            LocalDate start = Dates.parse(date);
            LocalDate end = Dates.parse(endDate);
            if (start == null || end == null || end.isBefore(start)) {
                endDate = date;
            }
        }
    }

    public DayRule copy() {
        DayRule rule = new DayRule(type, date, endDate);
        rule.refDate = refDate;
        return rule;
    }

    /** 取该日的调休规则；没有则返回 null。同一天有多条时以最后一条为准。 */
    public static DayRule swapRuleFor(List<DayRule> rules, String isoDate) {
        if (rules == null) {
            return null;
        }
        DayRule found = null;
        for (DayRule rule : rules) {
            if (rule.isSwap() && rule.matches(isoDate) && rule.referenceDate() != null) {
                found = rule;
            }
        }
        return found;
    }

    /** 这一天实际按周几上课；没有调休规则返回 0。同一天有多条时以最后一条为准。 */
    public static int effectiveWeekday(List<DayRule> rules, String isoDate) {
        DayRule rule = swapRuleFor(rules, isoDate);
        if (rule == null) {
            return 0;
        }
        LocalDate reference = rule.referenceDate();
        return reference == null ? 0 : reference.getDayOfWeek().getValue();
    }

    /** 这一天是否被用户设为放假。 */
    public static boolean isOff(List<DayRule> rules, String isoDate) {
        if (rules == null) {
            return false;
        }
        for (DayRule rule : rules) {
            if (rule.isOffOn(isoDate)) {
                return true;
            }
        }
        return false;
    }
}
