package com.netessx.qutschedule.model;

import com.netessx.qutschedule.R;

/**
 * 三套主题风格。每套同时给出配色倾向与课表外观参数，切主题即可整体换一种观感。
 *
 * <ul>
 *   <li>书卷：方正、紧凑、实色块，信息密度高。</li>
 *   <li>通透：大圆角、留白多、低不透明度，看得到背景。</li>
 *   <li>柔绘：中等圆角、柔和留白，介于两者之间。</li>
 * </ul>
 */
public final class ThemePreset {

    private ThemePreset() {
    }

    public static int labelRes(String theme) {
        if (Prefs.THEME_CLEAR.equals(theme)) {
            return R.string.appearance_theme_clear;
        }
        if (Prefs.THEME_SOFT.equals(theme)) {
            return R.string.appearance_theme_soft;
        }
        return R.string.appearance_theme_scroll;
    }

    /** 把主题对应的外观参数写进偏好。 */
    public static void apply(Prefs prefs, String theme) {
        if (prefs == null) {
            return;
        }
        prefs.theme = theme;
        if (Prefs.THEME_CLEAR.equals(theme)) {
            prefs.gridCorner = 18;
            prefs.gridGap = 5;
            prefs.gridAlpha = 62;
            prefs.gridHeightScale = 1.15f;
        } else if (Prefs.THEME_SOFT.equals(theme)) {
            prefs.gridCorner = 12;
            prefs.gridGap = 3;
            prefs.gridAlpha = 82;
            prefs.gridHeightScale = 1.05f;
        } else {
            prefs.gridCorner = 6;
            prefs.gridGap = 2;
            prefs.gridAlpha = 100;
            prefs.gridHeightScale = 1f;
        }
        prefs.normalize();
    }
}
