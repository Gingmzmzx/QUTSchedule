package com.netessx.qutschedule;

import android.app.Application;
import android.content.res.Configuration;
import android.os.Build;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.model.Prefs;
import com.netessx.qutschedule.widget.ScheduleWidgetProvider;

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

    /**
     * 深浅色切换后重建小组件。
     *
     * <p>背景、文字色是资源，桌面重新装载布局时会跟着变；但课程色条是用 {@code setInt}
     * 把颜色值写死在 RemoteViews 里的，不重新渲染就会一直停在旧主题的配色上。
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        ScheduleWidgetProvider.updateAll(this);
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
