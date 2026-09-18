package com.netessx.qutschedule.ui;

import android.view.View;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * 系统栏内边距。
 *
 * <p>targetSdk 35 起 Android 会强制 edge-to-edge，应用无法退出该模式，{@code android:statusBarColor}
 * 也随之失效。内容想不被状态栏和导航栏压住，只能自己把系统栏尺寸加进根布局的 padding。
 */
public final class Insets {

    private Insets() {
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
