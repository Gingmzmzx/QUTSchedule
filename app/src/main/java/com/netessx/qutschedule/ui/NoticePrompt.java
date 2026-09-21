package com.netessx.qutschedule.ui;

import android.app.Activity;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.netessx.qutschedule.R;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.model.Prefs;
import com.netessx.qutschedule.update.NoticeChecker;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 服务端公告的展示。
 *
 * <p>只在「有更新的公告 id」时才弹；看过一次就把 id 记下来（{@code prefs.lastNoticeId}），
 * 之后不再重复打扰。拉取失败一律静默 —— 公告不是功能，断网时不该弹任何东西。
 */
public final class NoticePrompt {

    private NoticePrompt() {
    }

    /** 启动时与手动检查更新时调用。 */
    public static void check(final Activity activity) {
        NoticeChecker.check(activity, result -> {
            if (activity.isFinishing() || activity.isDestroyed() || !result.ok) {
                return;
            }
            Prefs prefs = ScheduleStore.get(activity).data().prefs;
            if (result.id <= prefs.lastNoticeId) {
                return;
            }
            // 先记下来再展示：万一展示过程被打断，也不会反复弹同一条
            prefs.lastNoticeId = result.id;
            ScheduleStore.get(activity).save();
            show(activity, result);
        });
    }

    private static void show(Activity activity, NoticeChecker.Result result) {
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);

        String posted = postedAt(result.time);
        if (!posted.isEmpty()) {
            TextView time = new TextView(activity);
            time.setText(activity.getString(R.string.notice_time_fmt, posted));
            time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            time.setTextColor(activity.getColor(R.color.text_secondary));
            column.addView(time);
        }

        TextView body = new TextView(activity);
        // content 里可以带 <br> 和 <a>，按 HTML 渲染；链接要能点，得自己设 MovementMethod
        body.setText(Html.fromHtml(result.content, Html.FROM_HTML_MODE_LEGACY));
        body.setMovementMethod(LinkMovementMethod.getInstance());
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        body.setLineSpacing(0, 1.25f);
        body.setTextColor(activity.getColor(R.color.colorOnSurfaceVariant));
        body.setPadding(0, SettingsUi.dp(activity, 8), 0, 0);
        column.addView(body);

        new MaterialAlertDialogBuilder(activity)
                .setTitle(result.title.isEmpty()
                        ? activity.getString(R.string.notice_title) : result.title)
                .setView(SettingsUi.dialogWrap(activity, column))
                .setPositiveButton(R.string.notice_ok, null)
                .show();
    }

    /** Unix 秒转本地时间；没有或解析不出就返回空串，界面上不显示这一行。 */
    private static String postedAt(long epochSeconds) {
        if (epochSeconds <= 0) {
            return "";
        }
        return Instant.ofEpochSecond(epochSeconds)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}
