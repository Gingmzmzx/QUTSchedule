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

    /** 数据迁移版本：需要清空课表并强制重新引导时加一。 */
    public static final int SCHEMA_MIGRATION = 1;

    // 自动刷新频率
    public static final String REFRESH_DAILY = "DAILY";
    public static final String REFRESH_WEEKLY = "WEEKLY";
    public static final String[] REFRESH_MODES = {REFRESH_DAILY, REFRESH_WEEKLY};

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
    /** 是否把当前课程注册成媒体会话（锁屏 / 灵动岛的进度卡片）。关掉后不再注册。 */
    public boolean mediaSessionEnabled = true;
    public boolean dndEnabled;
    public String dndMode = DND_SILENT;
    public boolean holidaySkip = true;
    /** 节假日与调休是否影响课表：放假不上课、调休日按调整后的星期上课。 */
    public boolean holidayAware = true;
    /** 是否已经看过初次使用引导。 */
    public boolean onboarded;
    /** 已执行到的数据迁移版本，见 {@link #SCHEMA_MIGRATION}。 */
    public int schemaMigration;
    /** 每次启动是否自动检查更新。 */
    public boolean autoCheckUpdate = true;
    /** 已经展示过的最大公告 id；0 表示还没展示过任何公告。 */
    public int lastNoticeId;

    // ---- 教务系统自动刷新 ----
    public boolean autoRefreshEnabled;
    /** 刷新频率：每天或每周一次。 */
    public String autoRefreshMode = REFRESH_DAILY;
    public int autoRefreshHour = 8;
    public int autoRefreshMinute;
    /** 每周模式下刷新发生在星期几，1 = 周一。 */
    public int autoRefreshWeekday = 1;
    /** 上次自动刷新的时间，0 表示还没跑过。 */
    public long autoRefreshLastAt;
    /** 上次自动刷新结果：空、{@link #REFRESH_OK} 或 {@link #REFRESH_FAILED}。 */
    public String autoRefreshLastResult = "";

    /** 后台刷新时发现登录已失效；重新登录成功后清掉。 */
    public boolean autoRefreshSessionExpired;

    /** 上一次「登录过期」通知的时间，避免每次保活都弹。 */
    public long autoRefreshNoticeAt;

    public static final String REFRESH_OK = "ok";
    public static final String REFRESH_FAILED = "failed";

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
        if (lastNoticeId < 0) {
            lastNoticeId = 0;
        }
        if (schemaMigration < 0) {
            schemaMigration = 0;
        }
        if (!contains(REFRESH_MODES, autoRefreshMode)) {
            autoRefreshMode = REFRESH_DAILY;
        }
        if (autoRefreshHour < 0 || autoRefreshHour > 23) {
            autoRefreshHour = 8;
        }
        if (autoRefreshMinute < 0 || autoRefreshMinute > 59) {
            autoRefreshMinute = 0;
        }
        if (autoRefreshWeekday < 1 || autoRefreshWeekday > 7) {
            autoRefreshWeekday = 1;
        }
        if (autoRefreshLastAt < 0) {
            autoRefreshLastAt = 0;
        }
        if (!REFRESH_OK.equals(autoRefreshLastResult)
                && !REFRESH_FAILED.equals(autoRefreshLastResult)) {
            autoRefreshLastResult = "";
        }
        if (autoRefreshNoticeAt < 0) {
            autoRefreshNoticeAt = 0;
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
        p.mediaSessionEnabled = mediaSessionEnabled;
        p.dndEnabled = dndEnabled;
        p.dndMode = dndMode;
        p.holidaySkip = holidaySkip;
        p.holidayAware = holidayAware;
        p.onboarded = onboarded;
        p.schemaMigration = schemaMigration;
        p.autoCheckUpdate = autoCheckUpdate;
        p.lastNoticeId = lastNoticeId;
        p.autoRefreshEnabled = autoRefreshEnabled;
        p.autoRefreshMode = autoRefreshMode;
        p.autoRefreshHour = autoRefreshHour;
        p.autoRefreshMinute = autoRefreshMinute;
        p.autoRefreshWeekday = autoRefreshWeekday;
        p.autoRefreshLastAt = autoRefreshLastAt;
        p.autoRefreshLastResult = autoRefreshLastResult;
        p.autoRefreshSessionExpired = autoRefreshSessionExpired;
        p.autoRefreshNoticeAt = autoRefreshNoticeAt;
        p.holidayAware = holidayAware;
        return p;
    }
}
