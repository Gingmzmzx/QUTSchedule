package com.netessx.qutschedule.auto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.netessx.qutschedule.data.ZfCaptureStore;
import com.netessx.qutschedule.data.ZfImporter;
import com.netessx.qutschedule.model.Course;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 自动更新只替换「当前学期 + 教务系统来源」的课程。 */
public class ZfRefresherTest {

    private static Course course(String name, String source, String semesterId) {
        Course course = new Course();
        course.name = name;
        course.source = source;
        course.semesterId = semesterId;
        course.dayOfWeek = 1;
        course.startSlot = 1;
        course.endSlot = 2;
        return course;
    }

    @Test
    public void replacesOnlyCapturedCoursesOfCurrentSemester() {
        Course manual = course("手动课", "", "s1");
        Course captured = course("旧抓取课", ZfCaptureStore.SOURCE_ZF, "s1");
        Course otherSemester = course("别的学期", ZfCaptureStore.SOURCE_ZF, "s2");
        List<Course> existing = new ArrayList<>(Arrays.asList(manual, captured, otherSemester));

        Course fresh = course("新抓取课", "", "");
        List<Course> incoming = new ArrayList<>(Arrays.asList(fresh));
        Course unscheduled = course("未排课", "", "");
        unscheduled.dayOfWeek = 0;
        List<Course> incomingUnscheduled = new ArrayList<>(Arrays.asList(unscheduled));

        List<Course> merged = ZfImporter.merge(existing, "s1", incoming, incomingUnscheduled);

        assertEquals(4, merged.size());
        assertTrue(merged.contains(manual));
        assertTrue(merged.contains(otherSemester));
        assertTrue(!merged.contains(captured));
        assertTrue(merged.contains(fresh));
        assertTrue(merged.contains(unscheduled));
        assertEquals("s1", fresh.semesterId);
        assertEquals(ZfCaptureStore.SOURCE_ZF, fresh.source);
        assertEquals("s1", unscheduled.semesterId);
        assertEquals(ZfCaptureStore.SOURCE_ZF, unscheduled.source);
    }

    @Test
    public void emptyCaptureLeavesManualCourses() {
        Course manual = course("手动课", "", "s1");
        List<Course> existing = new ArrayList<>(Arrays.asList(manual));

        List<Course> merged = ZfImporter.merge(existing, "s1", new ArrayList<>(), new ArrayList<>());

        assertEquals(1, merged.size());
        assertTrue(merged.contains(manual));
    }

    @Test
    public void setCookieReplacesSameNameAndStripsAttributes() {
        String merged = ZfRefresher.mergeSetCookies(
                "ENSSESSIONID=old; clientInfo=keep",
                Collections.singletonList("ENSSESSIONID=new; Path=/; HttpOnly"));

        assertEquals("ENSSESSIONID=new; clientInfo=keep", merged);
    }

    @Test
    public void setCookieAddsNewPair() {
        String merged = ZfRefresher.mergeSetCookies(
                "ENSSESSIONID=old",
                Collections.singletonList("vpn_timestamp=123"));

        assertEquals("ENSSESSIONID=old; vpn_timestamp=123", merged);
    }

    @Test
    public void emptySetCookieKeepsOriginal() {
        assertEquals("ENSSESSIONID=old",
                ZfRefresher.mergeSetCookies("ENSSESSIONID=old", Collections.emptyList()));
    }

    @Test
    public void nullCookieWithSetCookieBuildsFreshString() {
        assertEquals("ENSSESSIONID=new",
                ZfRefresher.mergeSetCookies(null,
                        Collections.singletonList("ENSSESSIONID=new; Domain=.qut.edu.cn")));
    }
}
