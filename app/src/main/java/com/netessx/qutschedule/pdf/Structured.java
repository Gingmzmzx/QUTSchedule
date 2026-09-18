package com.netessx.qutschedule.pdf;

import com.netessx.qutschedule.model.Course;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从备注 / 明细文本里识别结构化的课程字段。
 *
 * <p>教务系统的写法不统一，中英文都认：{@code 学分 / Credit / Score / XF}、
 * {@code 考核方式 / Assessment / ExamType}、{@code 实验课 / Lab / Experiment}。
 * 例如「备注：学分 3.5，考核方式：考试，实验课」会被识别为学分 {@code 3.5}、
 * 考核方式 {@code 考试}、实验课 {@code 是}。
 */
public final class Structured {

    private static final Pattern CREDIT = Pattern.compile(
            "(?:学分|Credit|Score|XF)\\s*[:：]?\\s*(\\d+(?:\\.\\d+)?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ASSESSMENT = Pattern.compile(
            "(?:考核方式|Assessment|ExamType)\\s*[:：]?\\s*([^\\s,，、;；/]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LAB = Pattern.compile(
            "实验课|上机课|Lab\\b|Experiment", Pattern.CASE_INSENSITIVE);

    private Structured() {
    }

    /** 只填空缺字段，不覆盖已有值。 */
    public static void fillFromNotes(Course course, String text) {
        if (course == null || text == null || text.isEmpty()) {
            return;
        }
        if (course.credit < 0) {
            Matcher credit = CREDIT.matcher(text);
            if (credit.find()) {
                try {
                    course.credit = Double.parseDouble(credit.group(1));
                } catch (NumberFormatException ignored) {
                    // 识别失败就保持未知
                }
            }
        }
        if (course.assessment == null || course.assessment.isEmpty()
                || "无".equals(course.assessment)) {
            Matcher assessment = ASSESSMENT.matcher(text);
            if (assessment.find()) {
                String value = assessment.group(1).trim();
                if (!value.isEmpty()) {
                    course.assessment = value;
                }
            }
        }
        if (!course.lab && LAB.matcher(text).find()) {
            course.lab = true;
        }
    }
}
