package com.netessx.qutschedule.ui;

import android.Manifest;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputLayout;
import com.netessx.qutschedule.R;
import com.netessx.qutschedule.backup.IcsExporter;
import com.netessx.qutschedule.backup.JsonBackup;
import com.netessx.qutschedule.backup.SystemCalendarSync;
import com.netessx.qutschedule.backup.WebDavClient;
import com.netessx.qutschedule.data.ScheduleStore;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 备份与恢复：JSON、ICS、系统日历、WebDAV。 */
public class BackupActivity extends BaseActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextInputLayout urlInput;
    private TextInputLayout userInput;
    private TextInputLayout passwordInput;

    private final ActivityResultLauncher<String> exportJson = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"), uri -> {
                if (uri != null) {
                    runExport(uri, true);
                }
            });

    private final ActivityResultLauncher<String> exportIcs = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/calendar"), uri -> {
                if (uri != null) {
                    runExport(uri, false);
                }
            });

    private final ActivityResultLauncher<String[]> importJson = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) {
                    return;
                }
                String error = JsonBackup.importFrom(this, uri);
                toast(error == null ? getString(R.string.backup_ok)
                        : getString(R.string.backup_failed_fmt, error));
                if (error == null) {
                    setResult(RESULT_OK);
                }
            });

    private final ActivityResultLauncher<String[]> calendarPermission = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), granted -> {
                if (Boolean.TRUE.equals(granted.get(Manifest.permission.WRITE_CALENDAR))) {
                    syncCalendar();
                } else {
                    toast(getString(R.string.backup_failed_fmt, "未授予日历权限"));
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout column = SettingsUi.column(this);
        SettingsUi.section(this, column, getString(R.string.mine_backup));
        column.addView(SettingsUi.buttonRow(this, getString(R.string.backup_export_json),
                v -> exportJson.launch(JsonBackup.suggestFileName())));
        column.addView(SettingsUi.buttonRow(this, getString(R.string.backup_import_json),
                v -> importJson.launch(new String[]{"application/json", "*/*"})));

        SettingsUi.section(this, column, getString(R.string.backup_export_ics));
        column.addView(SettingsUi.label(this, getString(R.string.backup_export_ics_hint)));
        column.addView(SettingsUi.buttonRow(this, getString(R.string.backup_export_ics),
                v -> exportIcs.launch("qut-schedule.ics")));

        SettingsUi.section(this, column, getString(R.string.backup_calendar));
        column.addView(SettingsUi.label(this, getString(R.string.backup_calendar_hint)));
        column.addView(SettingsUi.buttonRow(this, getString(R.string.backup_calendar),
                v -> requestCalendarSync()));

        SettingsUi.section(this, column, getString(R.string.backup_webdav));
        WebDavClient.Config config = WebDavClient.load(this);
        column.addView(SettingsUi.label(this, getString(R.string.backup_webdav_url)));
        urlInput = SettingsUi.textRow(this, "https://example.com/dav/", config.url);
        column.addView(urlInput);
        column.addView(SettingsUi.label(this, getString(R.string.backup_webdav_user)));
        userInput = SettingsUi.textRow(this, "", config.user);
        column.addView(userInput);
        column.addView(SettingsUi.label(this, getString(R.string.backup_webdav_password)));
        passwordInput = SettingsUi.textRow(this, "", config.password);
        column.addView(passwordInput);
        column.addView(SettingsUi.buttonRow(this, getString(R.string.backup_upload),
                v -> runWebDav(true)));
        column.addView(SettingsUi.buttonRow(this, getString(R.string.backup_download),
                v -> runWebDav(false)));

        setPageTitle(getString(R.string.mine_backup));
        setPageContent(SettingsUi.scrollWrap(this, column));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void runExport(Uri uri, boolean json) {
        try {
            if (json) {
                JsonBackup.export(this, uri);
            } else {
                IcsExporter.export(this, uri,
                        ScheduleStore.get(this).data().prefs.defaultReminderMinutes);
            }
            toast(getString(R.string.backup_ok));
        } catch (IOException | RuntimeException e) {
            toast(getString(R.string.backup_failed_fmt, message(e)));
        }
    }

    private void requestCalendarSync() {
        if (SystemCalendarSync.hasPermission(this)) {
            syncCalendar();
            return;
        }
        calendarPermission.launch(new String[]{
                Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR});
    }

    private void syncCalendar() {
        final int reminder = ScheduleStore.get(this).data().prefs.defaultReminderMinutes;
        executor.execute(() -> {
            final int added = SystemCalendarSync.sync(this, reminder);
            runOnUiThread(() -> toast(added < 0
                    ? getString(R.string.backup_failed_fmt, "无法写入系统日历")
                    : getString(R.string.course_manage_moved_fmt, added)));
        });
    }

    private void runWebDav(final boolean upload) {
        WebDavClient.Config config = new WebDavClient.Config();
        config.url = SettingsUi.text(urlInput);
        config.user = SettingsUi.text(userInput);
        config.password = SettingsUi.raw(passwordInput);
        WebDavClient.save(this, config);

        executor.execute(() -> {
            String error = null;
            try {
                if (upload) {
                    WebDavClient.upload(this, config);
                } else {
                    WebDavClient.download(this, config);
                }
            } catch (IOException | RuntimeException e) {
                error = message(e);
            }
            final String finalError = error;
            runOnUiThread(() -> {
                toast(finalError == null ? getString(R.string.backup_ok)
                        : getString(R.string.backup_failed_fmt, finalError));
                if (finalError == null && !upload) {
                    setResult(RESULT_OK);
                }
            });
        });
    }

    private static String message(Exception e) {
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
