package com.netessx.qutschedule.util;

import android.content.Context;

import com.netessx.qutschedule.R;
import com.netessx.qutschedule.model.Course;
import com.netessx.qutschedule.model.CourseType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 课程配色：同名课程颜色统一，不同课程优先分配尚未被占用的颜色。
 *
 * <p>每次课表数据变化后调用 {@link #refresh(List)} 重建映射，之后 {@link #colorOf} 就是一次查表。
 */
public final class ColorPalette {

    private static final int[] BLOCKS = {
            R.color.block_0, R.color.block_1, R.color.block_2, R.color.block_3,
            R.color.block_4, R.color.block_5, R.color.block_6, R.color.block_7,
    };

    private static final Map<String, Integer> ASSIGNED = new HashMap<>();

    private ColorPalette() {
    }

    /** 重建「课程名 → 调色板槽位」。 */
    public static void refresh(List<Course> courses) {
        ASSIGNED.clear();
        if (courses == null || courses.isEmpty()) {
            return;
        }
        List<String> names = new ArrayList<>();
        for (Course course : courses) {
            if (course == null || course.name == null || course.name.isEmpty()) {
                continue;
            }
            if (isTyped(course)) {
                continue;
            }
            if (!names.contains(course.name)) {
                names.add(course.name);
            }
        }
        Collections.sort(names);

        boolean[] used = new boolean[BLOCKS.length];
        for (String name : names) {
            int slot = Math.floorMod(hash(name), BLOCKS.length);
            for (int probe = 0; probe < BLOCKS.length && used[slot]; probe++) {
                slot = (slot + 1) % BLOCKS.length;
            }
            used[slot] = true;
            ASSIGNED.put(name, slot);
        }
    }

    public static int colorOf(Context ctx, Course course) {
        if (course == null) {
            return ctx.getColor(BLOCKS[0]);
        }
        switch (course.type == null ? CourseType.COURSE : course.type) {
            case CourseType.CLUB:
                return ctx.getColor(R.color.type_club);
            case CourseType.RESEARCH:
                return ctx.getColor(R.color.type_research);
            case CourseType.EXAM:
                return ctx.getColor(R.color.type_exam);
            case CourseType.OTHER:
                return ctx.getColor(R.color.type_other);
            default:
                break;
        }
        String key = course.name == null ? "" : course.name;
        Integer slot = ASSIGNED.get(key);
        if (slot == null) {
            slot = Math.floorMod(hash(key), BLOCKS.length);
        }
        return ctx.getColor(BLOCKS[slot]);
    }

    private static boolean isTyped(Course course) {
        return course.type != null && !CourseType.COURSE.equals(course.type);
    }

    private static int hash(String value) {
        int hash = 0;
        for (int i = 0; i < value.length(); i++) {
            hash = hash * 31 + value.charAt(i);
        }
        return hash;
    }
}
