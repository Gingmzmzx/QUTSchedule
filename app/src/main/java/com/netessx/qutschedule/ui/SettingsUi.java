package com.netessx.qutschedule.ui;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.netessx.qutschedule.R;

import java.util.List;

/** 二级页共用的 Material 3 控件工厂，避免为每个设置页单独写一份 XML。 */
public final class SettingsUi {

    /** 滑杆取值回调。 */
    public interface ValueListener {
        void onValue(int value);
    }

    /** 下拉选中回调。 */
    public interface OnPicked {
        void onPicked(int position);
    }

    private SettingsUi() {
    }

    public static int dp(Context ctx, float value) {
        return Math.round(ctx.getResources().getDisplayMetrics().density * value);
    }

    public static ScrollView scroll(Context ctx) {
        ScrollView scrollView = new ScrollView(ctx);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        return scrollView;
    }

    /** 设置页内容统一套一层 ScrollView。 */
    public static ScrollView scrollWrap(Context ctx, View content) {
        ScrollView scrollView = new ScrollView(ctx);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        scrollView.addView(content);
        return scrollView;
    }

    public static LinearLayout column(Context ctx) {
        LinearLayout column = new LinearLayout(ctx);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(ctx, 16);
        column.setPadding(pad, dp(ctx, 8), pad, dp(ctx, 32));
        return column;
    }

    public static void section(Context ctx, LinearLayout parent, String text) {
        TextView view = new TextView(ctx);
        view.setText(text);
        view.setTextSize(12);
        view.setTextColor(ctx.getColor(R.color.colorPrimary));
        view.setPadding(dp(ctx, 4), dp(ctx, 18), 0, dp(ctx, 4));
        parent.addView(view);
    }

    public static TextView label(Context ctx, String text) {
        TextView view = new TextView(ctx);
        view.setText(text);
        view.setTextSize(13);
        view.setTextColor(ctx.getColor(R.color.colorOnSurfaceVariant));
        view.setPadding(dp(ctx, 4), dp(ctx, 10), dp(ctx, 4), 0);
        return view;
    }

    /** M3 卡片分组：把一组设置放进一张 surfaceContainer 卡片。 */
    public static LinearLayout card(Context ctx, LinearLayout parent) {
        MaterialCardView card = new MaterialCardView(ctx);
        card.setCardBackgroundColor(ctx.getColor(R.color.colorSurfaceContainer));
        card.setRadius(dp(ctx, 16));
        card.setCardElevation(0f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(ctx, 6);
        card.setLayoutParams(params);

        LinearLayout inner = new LinearLayout(ctx);
        inner.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(ctx, 14);
        inner.setPadding(pad, dp(ctx, 6), pad, dp(ctx, 10));
        card.addView(inner);
        parent.addView(card);
        return inner;
    }

    public static MaterialSwitch switchRow(Context ctx, String text, boolean checked,
                                           CompoundButton.OnCheckedChangeListener listener) {
        MaterialSwitch view = new MaterialSwitch(ctx);
        view.setText(text);
        view.setTextSize(15);
        view.setChecked(checked);
        view.setPadding(dp(ctx, 4), dp(ctx, 10), dp(ctx, 4), dp(ctx, 10));
        view.setOnCheckedChangeListener(listener);
        return view;
    }

    public static MaterialButton buttonRow(Context ctx, String text, View.OnClickListener listener) {
        MaterialButton view = new MaterialButton(ctx);
        view.setText(text);
        view.setAllCaps(false);
        view.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        view.setOnClickListener(listener);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return view;
    }

    /** M3 描边输入框。取值用 {@link #text(TextInputLayout)}。 */
    public static TextInputLayout textRow(Context ctx, String hint, String value) {
        return field(ctx, hint, value, InputType.TYPE_CLASS_TEXT);
    }

    public static TextInputLayout numberRow(Context ctx, String hint, String value) {
        return field(ctx, hint, value, InputType.TYPE_CLASS_NUMBER);
    }

    /** 对话框里的输入框，配 {@link #dialogWrap(Context, View)} 使用。 */
    public static TextInputLayout dialogField(Context ctx, String hint, String value) {
        return field(ctx, hint, value, InputType.TYPE_CLASS_TEXT);
    }

    /** 回填内容。{@code TextInputLayout} 自身没有 setText，得转发给里面的输入框。 */
    public static void setText(TextInputLayout field, String value) {
        if (field != null && field.getEditText() != null) {
            field.getEditText().setText(value);
        }
    }

    /** 原样读取，不做 trim。密码这类凭据不该被静默改掉首尾字符。 */
    public static String raw(TextInputLayout field) {
        if (field == null || field.getEditText() == null) {
            return "";
        }
        CharSequence value = field.getEditText().getText();
        return value == null ? "" : value.toString();
    }

    /** 读取输入框内容并去掉首尾空白。 */
    public static String text(TextInputLayout field) {
        if (field == null || field.getEditText() == null) {
            return "";
        }
        CharSequence value = field.getEditText().getText();
        return value == null ? "" : value.toString().trim();
    }

    private static TextInputLayout field(Context ctx, String hint, String value, int inputType) {
        // 必须从 XML 解析：代码 new 出来的控件拿不到 M3 输入框的完整样式
        TextInputLayout layout = (TextInputLayout) LayoutInflater.from(ctx)
                .inflate(R.layout.field_text, null, false);
        layout.setHint(hint);

        TextInputEditText edit = layout.findViewById(R.id.field_input);
        edit.setText(value);
        edit.setInputType(inputType);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(ctx, 8);
        layout.setLayoutParams(params);
        return layout;
    }

    /**
     * 包一层给对话框用。
     *
     * <p>对话框的 {@code setView} 会按自己的容器重新生成 LayoutParams，
     * 直接给输入框设外边距会被丢掉，所以用内边距来留白。
     */
    public static View dialogWrap(Context ctx, View field) {
        FrameLayout wrapper = new FrameLayout(ctx);
        int pad = dp(ctx, 24);
        wrapper.setPadding(pad, dp(ctx, 8), pad, 0);
        wrapper.addView(field, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        return wrapper;
    }

    /**
     * M3 下拉：Spinner 在 Material 3 里没有对应外观，统一用
     * {@code TextInputLayout} + 暴露式下拉的 {@code MaterialAutoCompleteTextView}。
     */
    public static TextInputLayout dropdown(Context ctx, String hint, List<String> items,
                                           int selected, final OnPicked listener) {
        TextInputLayout layout = (TextInputLayout) LayoutInflater.from(ctx)
                .inflate(R.layout.field_dropdown, null, false);
        layout.setHint(hint);

        MaterialAutoCompleteTextView field = layout.findViewById(R.id.field_input);
        field.setInputType(InputType.TYPE_NULL);
        // 必须用 Material 自己的 item 布局，Spinner 的布局在 M3 菜单里高度与文字颜色都不对
        field.setAdapter(new ArrayAdapter<>(ctx,
                com.google.android.material.R.layout.mtrl_auto_complete_simple_item, items));
        field.setText(items.isEmpty()
                ? "" : items.get(Math.max(0, Math.min(selected, items.size() - 1))), false);
        field.setOnItemClickListener((parent, view, position, id) -> listener.onPicked(position));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(ctx, 8);
        layout.setLayoutParams(params);
        return layout;
    }

    /** 带标题与当前值的滑杆，用于格子高度、圆角、动效速度这类连续参数。 */
    public static SeekBar slider(Context ctx, LinearLayout parent, String title,
                                 int max, int progress, final ValueListener listener) {
        final TextView label = label(ctx, title + "：" + progress);
        parent.addView(label);
        SeekBar bar = new SeekBar(ctx);
        bar.setMax(max);
        bar.setProgress(progress);
        bar.setPadding(dp(ctx, 4), 0, dp(ctx, 4), 0);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                label.setText(title + "：" + value);
                if (fromUser) {
                    listener.onValue(value);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        parent.addView(bar);
        return bar;
    }

    /** 让滑动内容撑满 FrameLayout，供 {@code setPageContent} 使用。 */
    public static View fill(View content) {
        FrameLayout wrapper = new FrameLayout(content.getContext());
        wrapper.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return wrapper;
    }
}
