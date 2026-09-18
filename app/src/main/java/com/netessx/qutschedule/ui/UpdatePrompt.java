package com.netessx.qutschedule.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.netessx.qutschedule.R;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.model.Prefs;
import com.netessx.qutschedule.update.UpdateChecker;

import java.time.LocalDate;

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

    /** 启动时静默检查，一天最多一次，且只在新版本存在时打扰用户。 */
    public static void checkSilently(final Activity activity) {
        Prefs prefs = ScheduleStore.get(activity).data().prefs;
        if (!prefs.autoCheckUpdate) {
            return;
        }
        String today = LocalDate.now().toString();
        if (today.equals(prefs.lastUpdateCheck)) {
            return;
        }
        prefs.lastUpdateCheck = today;
        ScheduleStore.get(activity).save();

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
        new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.update_available_title, result.latestTag))
                .setMessage(activity.getString(R.string.update_available_fmt, result.currentTag))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.update_go, (dialog, which) ->
                        openRelease(activity, result.releaseUrl))
                .show();
    }

    private static void openRelease(Activity activity, String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (RuntimeException e) {
            Toast.makeText(activity, R.string.update_no_browser, Toast.LENGTH_LONG).show();
        }
    }
}
