package com.netessx.qutschedule.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.netessx.qutschedule.R;

/**
 * 全屏看图：黑底、可双指缩放 / 双击放大 / 拖动，单击返回。
 *
 * <p>不用主题里的对话框样式，那个会带浅色背景卡片，看截图反而更小。
 */
public final class ImagePreviewDialog {

    private ImagePreviewDialog() {
    }

    public static void show(Context context, @DrawableRes int drawableRes,
                            @Nullable CharSequence title) {
        if (drawableRes == 0) {
            return;
        }
        final Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Color.BLACK);

        ZoomableImageView image = new ZoomableImageView(context);
        image.setImageResource(drawableRes);
        image.setOnDismissTapListener(dialog::dismiss);
        root.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (title != null && title.length() > 0) {
            TextView caption = new TextView(context);
            caption.setText(title);
            caption.setTextColor(Color.WHITE);
            caption.setTextSize(14);
            int pad = SettingsUi.dp(context, 16);
            caption.setPadding(pad, pad, pad, pad);
            root.addView(caption, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP));
        }

        // 底部说明手势，不然用户不知道能放大
        TextView hint = new TextView(context);
        hint.setText(R.string.image_preview_hint);
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(13);
        hint.setGravity(Gravity.CENTER);
        int hintPad = SettingsUi.dp(context, 16);
        hint.setPadding(hintPad, hintPad, hintPad, hintPad);
        root.addView(hint, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
            window.setDimAmount(0f);
        }
        dialog.show();
    }
}
