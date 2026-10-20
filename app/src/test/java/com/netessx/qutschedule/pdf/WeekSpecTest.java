package com.netessx.qutschedule.pdf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** 周次表达式：单段、多段、单双周与非法输入。 */
public class WeekSpecTest {

    private static final int MAX = 40;

    @Test
    public void parsesSingleRange() {
        assertEquals(Arrays.asList(4, 5, 6, 7), WeekSpec.parse("4-7周", MAX));
    }

    @Test
    public void parsesMultipleSegments() {
        List<Integer> weeks = WeekSpec.parse("4-5周、7-10周", MAX);
        assertEquals(Arrays.asList(4, 5, 7, 8, 9, 10), weeks);
    }

    @Test
    public void acceptsCommasAndSemicolons() {
        assertEquals(Arrays.asList(1, 2, 5), WeekSpec.parse("1-2,5周", MAX));
        assertEquals(Arrays.asList(1, 3, 6), WeekSpec.parse("1周;3周；6周", MAX));
    }

    @Test
    public void oddEvenAppliesPerSegment() {
        // 第一段只取单周，第二段整体保留
        assertEquals(Arrays.asList(5, 7, 16, 17),
                WeekSpec.parse("5-8周(单)、16-17周", MAX));
    }

    @Test
    public void describeCanBeParsedBack() {
        List<Integer> weeks = Arrays.asList(4, 5, 7, 8, 9);
        assertEquals(weeks, WeekSpec.parse(WeekSpec.describe(weeks), MAX));
    }

    @Test
    public void deduplicatesOverlappingSegments() {
        assertEquals(Arrays.asList(4, 5, 6), WeekSpec.parse("4-6周、5-6周", MAX));
    }

    @Test
    public void validatesInput() {
        assertTrue(WeekSpec.isValid("4-5周、7-18周"));
        assertTrue(WeekSpec.isValid(""));
        assertFalse(WeekSpec.isValid("随便写的"));
    }
}
