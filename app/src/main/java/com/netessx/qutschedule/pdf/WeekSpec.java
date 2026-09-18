package com.netessx.qutschedule.pdf;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 周次表达式的解析与生成。
 *
 * <p>教务系统里出现过的写法：{@code 4-17周}、{@code 第4周}、{@code 5-15周(单)}、{@code 6-16周(双)}。
 */
public final class WeekSpec {

    private static final Pattern RANGE = Pattern.compile("(\\d+)\\s*-\\s*(\\d+)");
    private static final Pattern SINGLE = Pattern.compile("第\\s*(\\d+)\\s*周");

    private WeekSpec() {
    }

    /** 解析失败返回空列表，调用方按「每周都上」处理。 */
    public static List<Integer> parse(String spec, int maxWeek) {
        List<Integer> weeks = new ArrayList<>();
        if (spec == null || spec.isEmpty()) {
            return weeks;
        }
        boolean odd = spec.contains("单");
        boolean even = spec.contains("双");

        Matcher range = RANGE.matcher(spec);
        if (range.find()) {
            addRange(weeks, parseInt(range.group(1)), parseInt(range.group(2)), maxWeek, odd, even);
            return weeks;
        }
        Matcher single = SINGLE.matcher(spec);
        if (single.find()) {
            int week = parseInt(single.group(1));
            if (week >= 1 && week <= maxWeek) {
                weeks.add(week);
            }
        }
        return weeks;
    }

    private static void addRange(List<Integer> weeks, int from, int to, int maxWeek,
                                 boolean odd, boolean even) {
        int start = Math.max(1, Math.min(from, to));
        int end = Math.min(maxWeek, Math.max(from, to));
        for (int w = start; w <= end; w++) {
            if (odd && w % 2 == 0) {
                continue;
            }
            if (even && w % 2 == 1) {
                continue;
            }
            weeks.add(w);
        }
    }

    public static boolean isValid(String spec) {
        if (spec == null || spec.trim().isEmpty()) {
            return true;
        }
        return RANGE.matcher(spec).find() || SINGLE.matcher(spec).find();
    }

    /** 把周次列表压缩成便于阅读的短串，如 {@code 4-17周}、{@code 1-5,7,9周}。 */
    public static String describe(List<Integer> weeks) {
        if (weeks == null || weeks.isEmpty()) {
            return "每周";
        }
        List<Integer> sorted = new ArrayList<>(weeks);
        sorted.sort(Integer::compareTo);
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < sorted.size()) {
            int start = sorted.get(i);
            int end = start;
            while (i + 1 < sorted.size() && sorted.get(i + 1) == end + 1) {
                end = sorted.get(++i);
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(start == end ? String.valueOf(start) : start + "-" + end);
            i++;
        }
        return sb.append("周").toString();
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
