package com.netessx.qutschedule.ui;

import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import com.netessx.qutschedule.R;
import com.netessx.qutschedule.data.ScheduleStore;

/** 关于：版本与数据说明。 */
public class AboutActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout column = SettingsUi.column(this);
        SettingsUi.section(this, column, getString(R.string.app_name));
        column.addView(SettingsUi.label(this, getString(R.string.about_version_fmt,
                versionName(), versionCode())));
        column.addView(SettingsUi.label(this, getString(R.string.more_about_text)));
        column.addView(SettingsUi.buttonRow(this, getString(R.string.about_check_update),
                v -> UpdatePrompt.checkManual(this)));
        column.addView(SettingsUi.switchRow(this, getString(R.string.about_auto_check_update),
                ScheduleStore.get(this).data().prefs.autoCheckUpdate,
                (button, checked) -> {
                    ScheduleStore.get(this).data().prefs.autoCheckUpdate = checked;
                    ScheduleStore.get(this).save();
                }));
        column.addView(SettingsUi.buttonRow(this, getString(R.string.about_replay_onboarding),
                v -> startActivity(OnboardingActivity.newIntent(this))));
        setPageTitle(getString(R.string.mine_about));
        setPageContent(SettingsUi.scrollWrap(this, column));
    }

    private String versionName() {
        android.content.pm.PackageInfo info = packageInfo();
        return info == null || info.versionName == null ? "" : info.versionName;
    }

    /** versionCode 在 API 28 起是 long，低版本只能读已废弃的 int 字段。 */
    @SuppressWarnings("deprecation")
    private String versionCode() {
        android.content.pm.PackageInfo info = packageInfo();
        if (info == null) {
            return "";
        }
        long code = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P
                ? info.getLongVersionCode()
                : info.versionCode;
        return String.valueOf(code);
    }

    private android.content.pm.PackageInfo packageInfo() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
