package com.netessx.qutschedule.model;

/** 我的信息：头像与学校信息，仅本地保存。 */
public class Profile {

    public String avatarUri;
    public String nickname = "";
    public String signature = "";
    public String school = "";
    public String college = "";
    public String major = "";
    public String grade = "";

    public void normalize() {
        nickname = safe(nickname);
        signature = safe(signature);
        school = safe(school);
        college = safe(college);
        major = safe(major);
        grade = safe(grade);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public Profile copy() {
        Profile p = new Profile();
        p.avatarUri = avatarUri;
        p.nickname = nickname;
        p.signature = signature;
        p.school = school;
        p.college = college;
        p.major = major;
        p.grade = grade;
        return p;
    }
}
