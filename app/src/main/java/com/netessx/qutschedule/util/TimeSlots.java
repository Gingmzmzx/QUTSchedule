package com.netessx.qutschedule.util;

import java.util.Locale;

/** 节次定义：5 个时间段，每段两小节，课程按 1..10 节记录。 */
public final class TimeSlots {

    public static final int BANDS = 5;
    public static final String[] LABELS = {"1-2", "3-4", "5-6", "7-8", "9-10"};
    // 黄岛校区作息；上午三四节按 1–3 层，4 层及以上见 TimeScheme.defaults() 的楼层错峰
    public static final String[] DEFAULT_START = {"08:00", "10:05", "14:00", "16:10", "19:00"};
    public static final String[] DEFAULT_END = {"09:50", "11:55", "15:50", "18:00", "20:50"};

    private TimeSlots() {
    }

    public static int clamp(int slot) {
        return Math.max(1, Math.min(10, slot));
    }

    public static int bandOf(int slot) {
        return (clamp(slot) - 1) / 2;
    }

    /** 小节在整列中的纵向位置，第 1 节为 0，第 10 节为 9。 */
    public static float unitOf(int slot) {
        return clamp(slot) - 1;
    }

    public static String label(int slot) {
        return LABELS[bandOf(slot)];
    }

    /** "HH:mm" 转零点起分钟数，解析失败返回 -1。 */
    public static int minutes(String hhmm) {
        if (hhmm == null) {
            return -1;
        }
        int colon = hhmm.indexOf(':');
        if (colon <= 0) {
            return -1;
        }
        try {
            int h = Integer.parseInt(hhmm.substring(0, colon).trim());
            int m = Integer.parseInt(hhmm.substring(colon + 1).trim());
            if (h < 0 || h > 23 || m < 0 || m > 59) {
                return -1;
            }
            return h * 60 + m;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String format(int minutesOfDay) {
        int m = ((minutesOfDay % 1440) + 1440) % 1440;
        return String.format(Locale.CHINA, "%02d:%02d", m / 60, m % 60);
    }
}
