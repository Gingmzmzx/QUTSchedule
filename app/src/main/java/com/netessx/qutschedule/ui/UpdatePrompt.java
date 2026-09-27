package com.netessx.qutschedule.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.netessx.qutschedule.R;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.model.Prefs;
import com.netessx.qutschedule.update.ApkInstaller;
import com.netessx.qutschedule.update.UpdateChecker;

/** 检查更新的界面部分：什么时候查、查完怎么显示。 */
public final class UpdatePrompt {

    private UpdatePrompt() {
    }

    /** 用户主动点「检查更新」：无论结果如何都给个反馈。 */
    public static void checkManual(final Activity activity) {
        Toast.makeText(activity, R.string.update_checking, Toast.LENGTH_SHORT).show();
        UpdateChecker.check(activity, result -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            showResult(activity, result);
        });
    }

    /**
     * 启动时静默检查，只在新版本存在时打扰用户。
     *
     * <p>失败一律忽略：GitHub 在国内网络下不稳，为一次请求失败弹窗只会打扰用户，
     * 想确认结果可以走「关于」里的手动检查。
     */
    public static void checkSilently(final Activity activity) {
        Prefs prefs = ScheduleStore.get(activity).data().prefs;
        if (!prefs.autoCheckUpdate) {
            return;
        }

        UpdateChecker.check(activity, result -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            if (result.ok && result.hasUpdate) {
                showUpdate(activity, result);
            }
        });
    }

    private static void showResult(Activity activity, UpdateChecker.Result result) {
        if (!result.ok) {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.update_failed_title)
                    .setMessage(activity.getString(R.string.update_failed_fmt,
                            String.valueOf(result.error)))
                    .setPositiveButton(R.string.save, null)
                    .show();
            return;
        }
        if (!result.hasUpdate) {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.update_latest_title)
                    .setMessage(activity.getString(R.string.update_latest_fmt, result.currentTag))
                    .setPositiveButton(R.string.save, null)
                    .show();
            return;
        }
        showUpdate(activity, result);
    }

    private static void showUpdate(final Activity activity, final UpdateChecker.Result result) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.update_available_title, result.latestTag))
                .setMessage(message(activity, result))
                .setPositiveButton(R.string.update_install, (dialog, which) ->
                        requestInstall(activity, result));
        if (result.forceUpdate) {
            // 强制更新：不给「稍后」，返回键也关不掉
            builder.setCancelable(false);
        } else if (result.downloadUrl.isEmpty()) {
            // 接口没给直链，只能去官网下载
            builder.setNegativeButton(R.string.update_go, (dialog, which) ->
                    openRelease(activity, result.releaseUrl));
        } else {
            builder.setNegativeButton(R.string.cancel, null);
        }
        builder.show();
    }

    /** 等用户去系统设置里授权的那次更新；回来时接着下，不用再点一遍。 */
    private static UpdateChecker.Result pending;

    /** 用户点了「立即更新」：有安装权限就直接下，没有先去授权。 */
    private static void requestInstall(Activity activity, UpdateChecker.Result result) {
        if (!ApkInstaller.canInstall(activity)) {
            pending = result;
            Toast.makeText(activity, R.string.update_need_install_permission,
                    Toast.LENGTH_LONG).show();
            ApkInstaller.openInstallPermissionSettings(activity);
            return;
        }
        ApkInstaller.downloadAndInstall(activity, result);
    }

    /**
     * 从系统授权页回来时调用（{@code MainActivity.onResume}）。
     *
     * <p>已经授权就接着下载；还没授权就把更新弹窗重新弹一次 —— 跳走再回来时 Activity 会重建，
     * 原来的对话框不会自动恢复，用户会以为点了没反应。
     */
    public static void resumePending(Activity activity) {
        if (pending == null) {
            return;
        }
        UpdateChecker.Result result = pending;
        pending = null;
        if (ApkInstaller.canInstall(activity)) {
            ApkInstaller.downloadAndInstall(activity, result);
        } else {
            checkManual(activity);
        }
    }

    /** 正文 = 当前版本 + 是否强制 + 安装包大小 + 接口里的更新说明。 */
    private static String message(Activity activity, UpdateChecker.Result result) {
        StringBuilder text = new StringBuilder(
                activity.getString(R.string.update_available_fmt, result.currentTag));
        text.append("\n").append(activity.getString(result.forceUpdate
                ? R.string.update_force_hint : R.string.update_optional_hint));
        if (result.size > 0) {
            text.append("\n").append(activity.getString(R.string.update_size_fmt,
                    sizeText(result.size)));
        }
        String notes = result.notes == null ? "" : result.notes.trim();
        if (!notes.isEmpty()) {
            text.append("\n\n").append(activity.getString(R.string.update_notes_title))
                    .append("\n").append(notes);
        }
        return text.toString();
    }

    private static String sizeText(long bytes) {
        if (bytes < 1024 * 1024) {
            return Math.max(1, bytes / 1024) + " KB";
        }
        return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1024.0 / 1024.0);
    }

    private static void openRelease(Activity activity, String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (RuntimeException e) {
            Toast.makeText(activity, R.string.update_no_browser, Toast.LENGTH_LONG).show();
        }
    }
}
