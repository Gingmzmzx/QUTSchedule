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
import com.netessx.qutschedule.backup.WatchSync;
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

        SettingsUi.section(this, column, getString(R.string.backup_watch));
        column.addView(SettingsUi.label(this, getString(R.string.backup_watch_hint)));
        column.addView(SettingsUi.buttonRow(this, getString(R.string.backup_watch_export),
                v -> exportWatch()));
        column.addView(SettingsUi.buttonRow(this, getString(R.string.backup_watch_send),
                v -> sendToWatch()));

        SettingsUi.section(this, column, getString(R.string.backup_webdav));
        WebDavClient.Config config = WebDavClient.load(this);
        // hint 用字段名（它会浮在输入框上当作标题），示例地址挪到下面当说明
        urlInput = SettingsUi.textRow(this, getString(R.string.backup_webdav_url), config.url);
        column.addView(urlInput);
        userInput = SettingsUi.textRow(this, getString(R.string.backup_webdav_user), config.user);
        column.addView(userInput);
        passwordInput = SettingsUi.textRow(this, getString(R.string.backup_webdav_password),
                config.password);
        column.addView(passwordInput);
        column.addView(SettingsUi.label(this, getString(R.string.backup_webdav_url_hint)));
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

    /**
     * 把手表端要用的课表写到应用外部私有目录，再用 adb 推到手表。
     *
     * <p>路径 {@code /sdcard/Android/data/com.netessx.qutschedule/files/watch-schedule.json}，
     * 手机侧不需要存储权限；手表那边读自己的同名目录，同样不用授权。
     */
    private void exportWatch() {
        ScheduleStore.get(this).save();
        java.io.File dir = getExternalFilesDir(null);
        java.io.File target = new java.io.File(dir == null ? getCacheDir() : dir,
                "watch-schedule.json");
        java.io.File source = new java.io.File(getFilesDir(), "schedule.json");
        try (java.io.InputStream in = new java.io.FileInputStream(source);
             java.io.OutputStream out = new java.io.FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            toast(getString(R.string.backup_watch_done_fmt, target.getAbsolutePath()));
        } catch (IOException | RuntimeException e) {
            toast(getString(R.string.backup_failed_fmt, message(e)));
        }
    }

    /** 蓝牙发送前先要权限：Android 12 起连接蓝牙设备需要运行时授权。 */
    private final ActivityResultLauncher<String> bluetoothPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    pickWatch();
                } else {
                    toast(getString(R.string.backup_watch_no_permission));
                }
            });

    /** 把当前课表经蓝牙推给手表。 */
    private void sendToWatch() {
        ScheduleStore.get(this).save();
        if (!WatchSync.hasPermission(this)) {
            bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT);
            return;
        }
        pickWatch();
    }

    /** 从已配对的设备里选手表；两台设备需要先在系统蓝牙里配对过。 */
    @android.annotation.SuppressLint("MissingPermission")
    private void pickWatch() {
        java.util.List<android.bluetooth.BluetoothDevice> devices = WatchSync.bondedDevices(this);
        if (devices.isEmpty()) {
            toast(getString(R.string.backup_watch_no_device));
            return;
        }
        String[] items = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            android.bluetooth.BluetoothDevice device = devices.get(i);
            items[i] = device.getName() + "\n" + device.getAddress();
        }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.backup_watch_pick)
                .setItems(items, (dialog, which) -> sendToWatch(devices.get(which)))
                .show();
    }

    private void sendToWatch(android.bluetooth.BluetoothDevice device) {
        toast(getString(R.string.backup_watch_sending));
        WatchSync.send(this, device, error -> toast(error == null
                ? getString(R.string.backup_watch_sent)
                : getString(R.string.backup_failed_fmt, error)));
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
