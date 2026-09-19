package com.netessx.qutschedule.util;

/**
 * 从课程地点里猜楼层。
 *
 * <p>教室编号一般是「楼号字母 + 三位数字」，首位数字就是楼层：{@code A302} 是 3 楼，
 * {@code B404} 是 4 楼；{@code J14-302}、{@code 文理楼204} 这类取最后一串数字，
 * 同样看首位。认不出（没有三位以上的数字、或首位是 0）就返回 0，调用方按普通作息处理。
 */
public final class RoomFloor {

    private RoomFloor() {
    }

    /** 楼层；认不出返回 0。 */
    public static int of(String location) {
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
            // 只认三位以上的数字串，免得「3 号教学楼」「第 2 机房」被当成楼层
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
}
