package com.netessx.qutschedule.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.netessx.qutschedule.R;
import com.netessx.qutschedule.auto.AutoRefreshJobService;
import com.netessx.qutschedule.auto.AutoRefreshScheduler;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.data.ZfCaptureStore;
import com.netessx.qutschedule.model.Prefs;

/** 「我的 → 自动刷新课表」设置页。 */
public class AutoRefreshActivity extends BaseActivity {

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            });

    private AutoRefreshSettingsView view;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.title_auto_refresh);
        setPageTitle(getString(R.string.title_auto_refresh));

        ScheduleStore store = ScheduleStore.get(this);
        Prefs prefs = store.data().prefs;
        view = new AutoRefreshSettingsView(this);
        view.bind(prefs, ZfCaptureStore.get(this).hasCapture(), new AutoRefreshSettingsView.Listener() {
            @Override
            public void onChanged() {
                store.save();
                AutoRefreshScheduler.reschedule(AutoRefreshActivity.this);
                if (prefs.autoRefreshEnabled) {
                    ensureNotificationPermission();
                }
            }

            @Override
            public void onRefreshNow() {
                ensureNotificationPermission();
                AutoRefreshJobService.enqueue(AutoRefreshActivity.this);
                Toast.makeText(AutoRefreshActivity.this,
                        R.string.auto_refresh_started, Toast.LENGTH_SHORT).show();
            }
        });

        LinearLayout column = SettingsUi.column(this);
        column.addView(view);
        setPageContent(SettingsUi.scrollWrap(this, column));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (view != null) {
            view.refreshStatus();
        }
    }

    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }
}
