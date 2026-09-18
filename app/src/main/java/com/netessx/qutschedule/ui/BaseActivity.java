package com.netessx.qutschedule.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.netessx.qutschedule.R;

/**
 * 二级页基类：Material 3 顶栏 + 内容容器。
 *
 * <p>主题用的是 NoActionBar，顶栏由这里统一提供，子类只需 {@link #setPageContent(View)}。
 */
public abstract class BaseActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private FrameLayout container;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.colorBackground));

        toolbar = new MaterialToolbar(this);
        toolbar.setBackgroundColor(getColor(R.color.colorSurface));
        root.addView(toolbar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        container = new FrameLayout(this);
        root.addView(container, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // edge-to-edge 是强制开启的，自己把系统栏高度让出来
        Insets.applySystemBars(root);
        super.setContentView(root);
        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    /** 顶栏标题。 */
    public void setPageTitle(CharSequence title) {
        toolbar.setTitle(title);
    }

    public void setPageContent(View content) {
        container.removeAllViews();
        container.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }
}
