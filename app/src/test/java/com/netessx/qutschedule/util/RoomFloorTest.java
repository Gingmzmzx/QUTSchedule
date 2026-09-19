package com.netessx.qutschedule.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** 从课程地点猜楼层：A302 → 3 楼，认不出返回 0。 */
public class RoomFloorTest {

    @Test
    public void readsTheFirstDigitOfTheRoomNumber() {
        assertEquals(3, RoomFloor.of("A302"));
        assertEquals(4, RoomFloor.of("B404"));
        assertEquals(1, RoomFloor.of("C101"));
    }

    @Test
    public void takesTheLastNumberGroup() {
        assertEquals(3, RoomFloor.of("J14-302"));
        assertEquals(2, RoomFloor.of("文理楼204"));
        assertEquals(4, RoomFloor.of("B1-401"));
    }

    @Test
    public void ignoresTheBuildingNumber() {
        // 楼号是两位，不能当成楼层
        assertEquals(2, RoomFloor.of("J14 204"));
    }

    @Test
    public void unknownWhenNoRoomNumber() {
        assertEquals(0, RoomFloor.of("机房"));
        assertEquals(0, RoomFloor.of("3 号教学楼"));
        assertEquals(0, RoomFloor.of(null));
        assertEquals(0, RoomFloor.of(""));
    }

    @Test
    public void unknownWhenTheRoomNumberStartsWithZero() {
        assertEquals(0, RoomFloor.of("A012"));
    }
}
