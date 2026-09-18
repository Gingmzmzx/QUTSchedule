package com.netessx.qutschedule.ui;

import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

import com.netessx.qutschedule.R;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.model.Prefs;
import com.netessx.qutschedule.model.ThemePreset;

import java.util.Arrays;

/** 外观与样式：主题、深浅色、动态取色、课表外观微调、动效。 */
public class AppearanceActivity extends BaseActivity {

    private Prefs prefs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = ScheduleStore.get(this).data().prefs;

        LinearLayout column = SettingsUi.column(this);
        setPageTitle(getString(R.string.mine_appearance));

        SettingsUi.section(this, column, getString(R.string.appearance_theme));
        column.addView(SettingsUi.dropdown(this, getString(R.string.appearance_theme),
                Arrays.asList(getString(R.string.appearance_theme_scroll),
                        getString(R.string.appearance_theme_clear),
                        getString(R.string.appearance_theme_soft)),
                indexOf(Prefs.THEMES, prefs.theme),
                position -> {
                    // 下拉挂上监听会立刻回调一次，只在真的换主题时才重建，否则会无限 recreate
                    if (Prefs.THEMES[position].equals(prefs.theme)) {
                        return;
                    }
                    // 换主题会一并套用该主题的圆角 / 间距 / 不透明度预设
                    ThemePreset.apply(prefs, Prefs.THEMES[position]);
                    ScheduleStore.get(this).save();
                    recreate();
                }));

        SettingsUi.section(this, column, getString(R.string.appearance_mode));
        column.addView(SettingsUi.dropdown(this, getString(R.string.appearance_mode),
                Arrays.asList(getString(R.string.appearance_mode_system),
                        getString(R.string.appearance_mode_light),
                        getString(R.string.appearance_mode_dark)),
                indexOf(Prefs.MODES, prefs.themeMode),
                position -> {
                    if (Prefs.MODES[position].equals(prefs.themeMode)) {
                        return;
                    }
                    prefs.themeMode = Prefs.MODES[position];
                    ScheduleStore.get(this).save();
                    AppCompatDelegate.setDefaultNightMode(nightModeOf(prefs.themeMode));
                }));

        SettingsUi.section(this, column, getString(R.string.appearance_grid));
        LinearLayout dynamic = SettingsUi.card(this, column);
        dynamic.addView(SettingsUi.switchRow(this, getString(R.string.appearance_dynamic),
                prefs.dynamicColor, (button, checked) -> prefs.dynamicColor = checked));
        dynamic.addView(SettingsUi.label(this, getString(R.string.appearance_dynamic_hint)));

        LinearLayout grid = SettingsUi.card(this, column);
        SettingsUi.slider(this, grid, getString(R.string.appearance_grid_height),
                25, Math.round(prefs.gridHeightScale * 10),
                value -> prefs.gridHeightScale = value / 10f);
        SettingsUi.slider(this, grid, getString(R.string.appearance_grid_corner),
                32, prefs.gridCorner, value -> prefs.gridCorner = value);
        SettingsUi.slider(this, grid, getString(R.string.appearance_grid_gap),
                12, prefs.gridGap, value -> prefs.gridGap = value);
        SettingsUi.slider(this, grid, getString(R.string.appearance_grid_alpha),
                100, prefs.gridAlpha, value -> prefs.gridAlpha = Math.max(20, value));
        SettingsUi.slider(this, grid, getString(R.string.appearance_animation),
                20, Math.round(prefs.animationScale * 10),
                value -> prefs.animationScale = value / 10f);

        SettingsUi.section(this, column, getString(R.string.appearance_reduced_motion));
        LinearLayout motion = SettingsUi.card(this, column);
        motion.addView(SettingsUi.switchRow(this, getString(R.string.appearance_reduced_motion),
                prefs.reducedMotion, (button, checked) -> prefs.reducedMotion = checked));
        motion.addView(SettingsUi.buttonRow(this, getString(R.string.appearance_reset), v -> {
            prefs.gridHeightScale = 1f;
            prefs.gridCorner = 8;
            prefs.gridGap = 2;
            prefs.gridAlpha = 100;
            prefs.customColor = 0;
            prefs.dynamicColor = false;
            save();
            recreate();
        }));

        setPageContent(SettingsUi.scrollWrap(this, column));
    }

    /** 主题模式 → AppCompat 的夜间模式常量。 */
    static int nightModeOf(String themeMode) {
        if (Prefs.MODE_LIGHT.equals(themeMode)) {
            return AppCompatDelegate.MODE_NIGHT_NO;
        }
        if (Prefs.MODE_DARK.equals(themeMode)) {
            return AppCompatDelegate.MODE_NIGHT_YES;
        }
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    private void save() {
        prefs.normalize();
        ScheduleStore.get(this).save();
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }

    private static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) {
                return i;
            }
        }
        return 0;
    }
}
