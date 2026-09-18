package com.netessx.qutschedule.util;

/**
 * 一节课的进度与倒计时计算，参数都是「当天零点起的分钟数」。
 *
 * <p>「还剩多久下课」和「还有多久上课」是两个不同的量。之前状态栏通知把两者混用，
 * 未开课时显示的是到下课的时间，所以这里单独抽出来并加了测试。
 */
public final class Countdown {

    private Countdown() {
    }

    public static boolean isOngoing(int startMinutes, int endMinutes, int nowMinutes) {
        return nowMinutes >= startMinutes && nowMinutes < endMinutes;
    }

    /** 距离上课还有几分钟；已开课返回 0。 */
    public static int minutesToStart(int startMinutes, int nowMinutes) {
        return Math.max(0, startMinutes - nowMinutes);
    }

    /** 距离下课还有几分钟；已下课返回 0。 */
    public static int minutesToEnd(int endMinutes, int nowMinutes) {
        return Math.max(0, endMinutes - nowMinutes);
    }

    /** 上课进度百分比，未开始为 0，已结束为 100。 */
    public static int percent(int startMinutes, int endMinutes, int nowMinutes) {
        int total = Math.max(1, endMinutes - startMinutes);
        int elapsed = Math.max(0, Math.min(total, nowMinutes - startMinutes));
        return elapsed * 100 / total;
    }
}
