package com.netessx.qutschedule.reminder;

import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioManager;
import android.os.Build;

import com.netessx.qutschedule.model.Prefs;

/**
 * 上课期间自动免打扰，下课恢复。
 *
 * <p>两种方式：{@code SILENT} 只把铃音调成静音，{@code DND} 直接切系统勿扰，后者需要用户在系统里
 * 授予「勿扰访问」权限。恢复时统一回到「响铃 + 允许全部通知」。
 */
public final class DndController {

    private DndController() {
    }

    public static boolean hasPolicyAccess(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }
        NotificationManager manager = ctx.getSystemService(NotificationManager.class);
        return manager != null && manager.isNotificationPolicyAccessGranted();
    }

    public static void enable(Context ctx, Prefs prefs) {
        if (prefs == null || !prefs.dndEnabled) {
            return;
        }
        if (Prefs.DND_MODE.equals(prefs.dndMode)) {
            setInterruptionFilter(ctx, NotificationManager.INTERRUPTION_FILTER_NONE);
            return;
        }
        setRingerMode(ctx, AudioManager.RINGER_MODE_SILENT);
    }

    public static void restore(Context ctx) {
        setInterruptionFilter(ctx, NotificationManager.INTERRUPTION_FILTER_ALL);
        setRingerMode(ctx, AudioManager.RINGER_MODE_NORMAL);
    }

    private static void setInterruptionFilter(Context ctx, int filter) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        NotificationManager manager = ctx.getSystemService(NotificationManager.class);
        if (manager == null || !manager.isNotificationPolicyAccessGranted()) {
            return;
        }
        try {
            manager.setInterruptionFilter(filter);
        } catch (RuntimeException ignored) {
            // 权限被系统回收时忽略，不影响其它功能
        }
    }

    private static void setRingerMode(Context ctx, int mode) {
        AudioManager audio = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null || audio.getRingerMode() == mode) {
            return;
        }
        try {
            audio.setRingerMode(mode);
        } catch (RuntimeException ignored) {
            // 同上
        }
    }
}
