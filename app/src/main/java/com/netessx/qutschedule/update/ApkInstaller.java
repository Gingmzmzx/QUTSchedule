package com.netessx.qutschedule.update;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.FileProvider;

import com.netessx.qutschedule.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * 应用内下载新版本并拉起安装。
 *
 * <p>下载到应用自己的外部私有目录（不需要存储权限），进度用一条通知展示；
 * 服务端给了 sha256 就校验一遍，对不上直接丢弃，避免装到被篡改或下载不全的包。
 *
 * <p>安装交给系统安装器（{@link Intent#ACTION_VIEW} + FileProvider），应用不参与静默安装
 * —— 那需要系统签名，普通应用做不到。系统会要求用户允许「安装未知应用」。
 */
public final class ApkInstaller {

    private static final String TAG = "ApkInstaller";
    private static final String CHANNEL = "app_update";
    private static final int NOTIFICATION_ID = 3001;

    private ApkInstaller() {
    }

    /** 后台下载并安装；所有失败都只提示，不抛给调用方。 */
    public static void downloadAndInstall(final Context context, final UpdateChecker.Result result) {
        final Context app = context.getApplicationContext();
        if (result.downloadUrl == null || result.downloadUrl.isEmpty()) {
            Toast.makeText(app, R.string.update_no_download_url, Toast.LENGTH_LONG).show();
            return;
        }
        // 先要「安装未知应用」权限再去下：否则几个 MB 下完才被弹到授权页，等于白下一遍
        if (!canInstall(app)) {
            toast(app, R.string.update_need_install_permission);
            openInstallPermissionSettings(app);
            return;
        }
        // 下载是后台线程干的，先给个即时反馈，否则用户会以为点了没反应
        toast(app, R.string.update_going_background);

        Thread worker = new Thread(() -> {
            try {
                install(app, result);
            } catch (Throwable t) {
                Log.w(TAG, "下载或安装失败", t);
                fail(app, R.string.update_download_failed);
            }
        }, "apk-download");
        worker.start();
    }

    /** 是否已允许「安装未知应用」；低版本没有这个限制。 */
    public static boolean canInstall(Context app) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || app.getPackageManager().canRequestPackageInstalls();
    }

    /** 跳到「安装未知应用」的开关页，授权后由调用方接着走后续流程。 */
    public static void openInstallPermissionSettings(Context app) {
        try {
            app.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + app.getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException ignored) {
            // 打不开就只留提示，用户自己去系统设置里开
        }
    }

    private static void install(Context app, UpdateChecker.Result result) throws Exception {
        File dir = app.getExternalFilesDir(null);
        File apk = new File(dir == null ? app.getCacheDir() : dir,
                "update-" + safe(result.latestTag) + ".apk");
        download(app, result, apk);

        if (!result.sha256.isEmpty()) {
            String actual = sha256(apk);
            if (!actual.equalsIgnoreCase(result.sha256)) {
                // 校验不过就别让用户装：删掉重下，比装出问题强
                Log.w(TAG, "sha256 不匹配：" + actual);
                //noinspection ResultOfMethodCallIgnored
                apk.delete();
                fail(app, R.string.update_checksum_failed);
                return;
            }
        }
        notifyDone(app, apk);
    }

    private static void download(Context app, UpdateChecker.Result result, File target)
            throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(result.downloadUrl).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "QUTSchedule-Android");
            conn.connect();

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("服务器返回 " + code);
            }
            long total = result.size > 0 ? result.size : conn.getContentLength();
            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                long done = 0;
                int progress = -1;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    done += read;
                    if (total > 0) {
                        int percent = (int) (done * 100 / total);
                        if (percent != progress) {
                            progress = percent;
                            notifyProgress(app, percent);
                        }
                    }
                }
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** 校验通过：收起进度通知，拉起系统安装界面。 */
    private static void notifyDone(Context app, File apk) {
        cancel(app);
        // 下载期间权限可能被撤销，这里再确认一次
        if (!canInstall(app)) {
            toast(app, R.string.update_need_install_permission);
            openInstallPermissionSettings(app);
            return;
        }
        launchInstaller(app, apk);
    }

    private static void launchInstaller(Context app, File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(app, app.getPackageName() + ".fileprovider", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            app.startActivity(intent);
        } catch (RuntimeException e) {
            Log.w(TAG, "拉起安装界面失败", e);
            toast(app, R.string.update_install_failed);
        }
    }

    private static void fail(Context app, int messageRes) {
        cancel(app);
        toast(app, messageRes);
    }

    private static void toast(Context app, int messageRes) {
        Toast.makeText(app, messageRes, Toast.LENGTH_LONG).show();
    }

    private static void notifyProgress(Context app, int percent) {
        ensureChannel(app);
        Notification notification = new NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(app.getString(R.string.update_downloading))
                .setProgress(100, percent, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
        try {
            NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, notification);
        } catch (SecurityException ignored) {
            // 没有通知权限：下载照常进行，只是看不到进度
        }
    }

    private static void cancel(Context app) {
        NotificationManagerCompat.from(app).cancel(NOTIFICATION_ID);
    }

    private static void ensureChannel(Context app) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = app.getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL,
                app.getString(R.string.channel_update_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    /** 版本号里可能有奇怪字符，落到文件名上前先过滤。 */
    private static String safe(String version) {
        String text = version == null ? "" : version.trim();
        return text.isEmpty() ? "latest" : text.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(String.format(Locale.ROOT, "%02x", b));
        }
        return hex.toString();
    }
}
