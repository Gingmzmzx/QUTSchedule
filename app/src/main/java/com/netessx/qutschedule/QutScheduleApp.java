package com.netessx.qutschedule;

import android.app.Application;
import android.os.Build;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.model.Prefs;

/**
 * 应用入口：启动时套用主题模式与动态取色。
 *
 * <p>课表数据不在这里加载，{@link ScheduleStore} 首次取用时才读盘。
 */
public class QutScheduleApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Prefs prefs = ScheduleStore.get(this).data().prefs;
        AppCompatDelegate.setDefaultNightMode(nightModeOf(prefs.themeMode));
        if (prefs.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Material You：Android 12 起按系统壁纸生成配色
            DynamicColors.applyToActivitiesIfAvailable(this);
        }
    }

    static int nightModeOf(String themeMode) {
        if (Prefs.MODE_LIGHT.equals(themeMode)) {
            return AppCompatDelegate.MODE_NIGHT_NO;
        }
        if (Prefs.MODE_DARK.equals(themeMode)) {
            return AppCompatDelegate.MODE_NIGHT_YES;
        }
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }
}
