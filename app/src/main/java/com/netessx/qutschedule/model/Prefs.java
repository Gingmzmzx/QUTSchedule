package com.netessx.qutschedule.model;

/** 全局偏好：外观、课表显示、提醒。 */
public class Prefs {

    // 主题风格
    public static final String THEME_SCROLL = "SCROLL";
    public static final String THEME_CLEAR = "CLEAR";
    public static final String THEME_SOFT = "SOFT";
    public static final String[] THEMES = {THEME_SCROLL, THEME_CLEAR, THEME_SOFT};

    // 主题模式
    public static final String MODE_SYSTEM = "SYSTEM";
    public static final String MODE_LIGHT = "LIGHT";
    public static final String MODE_DARK = "DARK";
    public static final String[] MODES = {MODE_SYSTEM, MODE_LIGHT, MODE_DARK};

    // 免打扰方式
    public static final String DND_SILENT = "SILENT";
    public static final String DND_MODE = "DND";
    public static final String[] DND_MODES = {DND_SILENT, DND_MODE};

    public String theme = THEME_SCROLL;
    public String themeMode = MODE_SYSTEM;
    /** Material You 动态取色：默认开启，Android 12 以下自动忽略。 */
    public boolean dynamicColor = true;
    /** 自定义主色，ARGB；为 0 表示不启用。 */
    public int customColor;
    /** 课表背景图，content:// 或 file://；可空。 */
    public String backgroundUri;
    public float gridHeightScale = 1f;
    public int gridCorner = 8;
    public int gridGap = 2;
    public int gridAlpha = 100;
    public float animationScale = 1f;
    public boolean reducedMotion;

    public boolean showWeekend = true;
    public boolean showNonCurrentWeek = true;
    /** 每周起始日，1 = 周一。 */
    public int weekStartDay = 1;

    public boolean reminderEnabled = true;
    public int defaultReminderMinutes = 15;
    public boolean liveUpdateEnabled = true;
    public boolean dndEnabled;
    public String dndMode = DND_SILENT;
    public boolean holidaySkip = true;
    /** 节假日与调休是否影响课表：放假不上课、调休日按调整后的星期上课。 */
    public boolean holidayAware = true;
    /** 是否已经看过初次使用引导。 */
    public boolean onboarded;
    /** 启动时是否自动检查更新。 */
    public boolean autoCheckUpdate = true;
    /** 上次检查更新的日期，yyyy-MM-dd，保证一天最多查一次。 */
    public String lastUpdateCheck = "";

    public boolean darkOn(boolean systemDark) {
        if (MODE_LIGHT.equals(themeMode)) {
            return false;
        }
        if (MODE_DARK.equals(themeMode)) {
            return true;
        }
        return systemDark;
    }

    public void normalize() {
        if (!contains(THEMES, theme)) {
            theme = THEME_SCROLL;
        }
        if (!contains(MODES, themeMode)) {
            themeMode = MODE_SYSTEM;
        }
        if (!contains(DND_MODES, dndMode)) {
            dndMode = DND_SILENT;
        }
        gridHeightScale = clamp(gridHeightScale, 0.6f, 2.5f, 1f);
        animationScale = clamp(animationScale, 0f, 2f, 1f);
        gridCorner = (int) clamp(gridCorner, 0, 32, 8);
        gridGap = (int) clamp(gridGap, 0, 12, 2);
        gridAlpha = (int) clamp(gridAlpha, 20, 100, 100);
        if (defaultReminderMinutes < 0 || defaultReminderMinutes > 240) {
            defaultReminderMinutes = 15;
        }
        if (weekStartDay < 1 || weekStartDay > 7) {
            weekStartDay = 1;
        }
    }

    private static boolean contains(String[] values, String value) {
        for (String item : values) {
            if (item.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static float clamp(float value, float min, float max, float fallback) {
        if (Float.isNaN(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    public Prefs copy() {
        Prefs p = new Prefs();
        p.theme = theme;
        p.themeMode = themeMode;
        p.dynamicColor = dynamicColor;
        p.customColor = customColor;
        p.backgroundUri = backgroundUri;
        p.gridHeightScale = gridHeightScale;
        p.gridCorner = gridCorner;
        p.gridGap = gridGap;
        p.gridAlpha = gridAlpha;
        p.animationScale = animationScale;
        p.reducedMotion = reducedMotion;
        p.showWeekend = showWeekend;
        p.showNonCurrentWeek = showNonCurrentWeek;
        p.weekStartDay = weekStartDay;
        p.reminderEnabled = reminderEnabled;
        p.defaultReminderMinutes = defaultReminderMinutes;
        p.liveUpdateEnabled = liveUpdateEnabled;
        p.dndEnabled = dndEnabled;
        p.dndMode = dndMode;
        p.holidaySkip = holidaySkip;
        p.holidayAware = holidayAware;
        p.onboarded = onboarded;
        p.autoCheckUpdate = autoCheckUpdate;
        p.lastUpdateCheck = lastUpdateCheck;
        p.holidayAware = holidayAware;
        return p;
    }
}
