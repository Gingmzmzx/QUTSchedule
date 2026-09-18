package com.netessx.qutschedule.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 锁住「到上课」与「到下课」两个倒计时不再被混用。 */
public class CountdownTest {

    /** 14:00 - 15:40 的一节课。 */
    private static final int START = 14 * 60;
    private static final int END = 15 * 60 + 40;

    @Test
    public void countsDownToStartBeforeClassBegins() {
        int now = 13 * 60 + 50; // 13:50
        assertFalse(Countdown.isOngoing(START, END, now));
        assertEquals(10, Countdown.minutesToStart(START, now));
        // 关键回归：未开课时不能报到下课的时间（那是 110 分钟）
        assertEquals(110, Countdown.minutesToEnd(END, now));
        assertEquals(0, Countdown.percent(START, END, now));
    }

    @Test
    public void countsDownToEndWhileClassIsRunning() {
        int now = 14 * 60 + 30; // 14:30
        assertTrue(Countdown.isOngoing(START, END, now));
        assertEquals(70, Countdown.minutesToEnd(END, now));
        assertEquals(0, Countdown.minutesToStart(START, now));
        assertEquals(30, Countdown.percent(START, END, now));
    }

    @Test
    public void stopsAtTheBoundaries() {
        assertTrue(Countdown.isOngoing(START, END, START));
        assertFalse(Countdown.isOngoing(START, END, END));
        assertEquals(0, Countdown.minutesToEnd(END, END));
        assertEquals(100, Countdown.percent(START, END, END));
        assertEquals(100, Countdown.percent(START, END, END + 30));
        assertEquals(0, Countdown.minutesToEnd(END, END + 30));
    }
}
