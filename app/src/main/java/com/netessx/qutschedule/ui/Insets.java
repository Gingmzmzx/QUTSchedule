package com.netessx.qutschedule.ui;

import android.app.Activity;
import android.content.res.Configuration;
import android.view.View;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * 系统栏内边距与图标明暗。
 *
 * <p>targetSdk 35 起 Android 会强制 edge-to-edge，应用无法退出该模式，{@code android:statusBarColor}
 * 也随之失效。内容想不被状态栏和导航栏压住，只能自己把系统栏尺寸加进根布局的 padding。
 *
 * <p>另外系统默认按深色界面画状态栏图标（白色），浅色主题下就成了白底白字看不见；
 * 这里按当前深浅色把图标反过来。
 */
public final class Insets {

    private Insets() {
    }

    /** 内边距 + 图标明暗。只能在 {@code setContentView} 之后调，见 {@link #applyBarAppearance}。 */
    public static void applySystemBars(final Activity activity, final View root) {
        applySystemBars(root);
        applyBarAppearance(activity);
    }

    /**
     * 按当前深浅色调整状态栏 / 导航栏图标明暗。
     *
     * <p>必须在 {@code setContentView} 之后调用：更早时 decor view 还没建好，
     * 取 {@code WindowInsetsController} 会直接 NPE。
     */
    public static void applyBarAppearance(final Activity activity) {
        if (activity == null) {
            return;
        }
        try {
            boolean dark = (activity.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                    activity.getWindow(), activity.getWindow().getDecorView());
            controller.setAppearanceLightStatusBars(!dark);
            controller.setAppearanceLightNavigationBars(!dark);
        } catch (Throwable ignored) {
            // 个别 ROM / 时机不对时取不到控制器，图标保持系统默认，不影响使用
        }
    }

    public static void applySystemBars(final View root) {
        if (root == null) {
            return;
        }
        final int baseLeft = root.getPaddingLeft();
        final int baseTop = root.getPaddingTop();
        final int baseRight = root.getPaddingRight();
        final int baseBottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            androidx.core.graphics.Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(
                    baseLeft + bars.left,
                    baseTop + bars.top,
                    baseRight + bars.right,
                    baseBottom + bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
