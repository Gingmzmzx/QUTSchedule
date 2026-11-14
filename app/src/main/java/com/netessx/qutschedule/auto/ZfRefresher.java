package com.netessx.qutschedule.auto;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.util.Log;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.WebView;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.netessx.qutschedule.MainActivity;
import com.netessx.qutschedule.R;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.data.ZfCaptureStore;
import com.netessx.qutschedule.data.ZfImporter;
import com.netessx.qutschedule.data.ZfTimetableParser;
import com.netessx.qutschedule.ui.WebImportActivity;
import com.netessx.qutschedule.model.AppData;
import com.netessx.qutschedule.model.Prefs;
import com.netessx.qutschedule.reminder.ReminderScheduler;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 后台刷新教务系统课表。
 *
 * <p>用首次抓取时保存的 URL、请求头和 WebView 的登录 Cookie 重放请求，解析后只替换
 * 「来自教务系统」的课程，手动添加 / PDF 导入的课程原样保留。全程不打印 URL、Cookie
 * 或响应体；抓取结果仍只落在 {@link ZfCaptureStore} 的私有目录里。
 */
public final class ZfRefresher {

    private static final String TAG = "QutRefresh";

    private static final int MAX_CHARS = 8 * 1024 * 1024;
    private static final String CHANNEL = "auto_refresh";
    private static final int NOTIFY_ID = 0x41555246; // "AURF"

    /** 一次刷新的结果，用于通知文案与日志之外的状态展示。 */
    public static final class Result {
        public boolean ok;
        public int courses;
        public int unscheduled;
        public String error = "";
    }

    private ZfRefresher() {
    }

    /** 同步执行一次刷新，调用方负责放到后台线程。 */
    public static Result refresh(Context context) {
        Context app = context.getApplicationContext();
        Result result = new Result();
        ZfCaptureStore capture = ZfCaptureStore.get(app);
        JSONObject meta = capture.latestMeta();
        String url = meta == null ? "" : meta.optString("url", "");
        if (url.isEmpty()) {
            return fail(app, result, app.getString(R.string.auto_refresh_error_no_capture));
        }
        String cookie = cookieFor(app, url);
        boolean fromManager = cookie != null && !cookie.isEmpty();
        if (!fromManager) {
            // 极少数情况下 WebView 仍不可用，退回抓取时记下的那份；它可能已经过期，不能据此判定登录失效
            cookie = meta.optString("cookie", "");
        }
        Log.i(TAG, "cookie fromManager=" + fromManager);
        if (cookie == null || cookie.isEmpty()) {
            return failLoginExpired(app, result);
        }
        String body;
        int status;
        List<String> setCookies;
        try {
            HttpResult response = fetch(url, meta.optJSONObject("headers"), cookie);
            body = response.body;
            status = response.status;
            setCookies = response.setCookies;
        } catch (IOException e) {
            return fail(app, result,
                    app.getString(R.string.auto_refresh_network_failed, String.valueOf(e.getMessage())));
        }
        String trimmed = body == null ? "" : body.trim();
        // 只记状态与长度，不含 URL / Cookie / 响应内容
        Log.i(TAG, "refresh status=" + status + " chars=" + trimmed.length());
        // 登录失效时 WebVPN 会返回登录页或 302，两者都不是 JSON
        if (status < 200 || status >= 300 || !trimmed.startsWith("{")) {
            if (fromManager) {
                return failLoginExpired(app, result);
            }
            return fail(app, result, app.getString(R.string.auto_refresh_login_expired));
        }
        ZfTimetableParser.ParseResult parsed = ZfTimetableParser.parse(trimmed);
        if (!parsed.isOk()) {
            return fail(app, result, parsed.error);
        }
        try {
            capture.save(url, "GET", trimmed, status, "auto");
        } catch (IOException ignored) {
            // 缓存更新失败不影响课表本身
        }
        // 让兜底 Cookie 跟着本次刷新滚动：服务器续期过就以响应里的 Set-Cookie 为准，
        // 否则继续用这次请求实际发送的那份。
        capture.saveCookie(url, mergeSetCookies(cookie, setCookies));

        ScheduleStore store = ScheduleStore.get(app);
        AppData data = store.data();
        ZfImporter.apply(app, parsed, new ZfImporter.Result());

        Prefs prefs = data.prefs;
        prefs.autoRefreshLastAt = System.currentTimeMillis();
        prefs.autoRefreshLastResult = Prefs.REFRESH_OK;
        prefs.autoRefreshSessionExpired = false;
        prefs.normalize();
        store.save();
        ReminderScheduler.sync(app);

        result.ok = true;
        result.courses = parsed.courses.size();
        result.unscheduled = parsed.unscheduled.size();
        return result;
    }

    /** 更新完成后的通知：成功时给出课程数，失败时说明原因。 */
    public static void notifyResult(Context context, Result result) {
        Context app = context.getApplicationContext();
        ensureChannel(app);
        boolean expired = !result.ok
                && ScheduleStore.get(app).data().prefs.autoRefreshSessionExpired;
        String text;
        if (result.ok) {
            text = result.unscheduled > 0
                    ? app.getString(R.string.auto_refresh_ok_extra_fmt, result.courses, result.unscheduled)
                    : app.getString(R.string.auto_refresh_ok_fmt, result.courses);
        } else if (expired) {
            text = app.getString(R.string.auto_refresh_expired_text);
        } else {
            text = result.error;
        }
        PendingIntent content = PendingIntent.getActivity(app, NOTIFY_ID,
                new Intent(app, expired ? WebImportActivity.class : MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_day)
                .setContentTitle(app.getString(result.ok
                        ? R.string.auto_refresh_ok_title
                        : (expired ? R.string.auto_refresh_expired_title : R.string.auto_refresh_fail_title)))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(content);
        try {
            NotificationManagerCompat.from(app).notify(NOTIFY_ID, builder.build());
        } catch (SecurityException ignored) {
            // 没有通知权限时静默失败，课表已经更新好了
        }
    }

    private static Result fail(Context app, Result result, String message) {
        result.ok = false;
        result.error = message;
        ScheduleStore store = ScheduleStore.get(app);
        Prefs prefs = store.data().prefs;
        prefs.autoRefreshLastAt = System.currentTimeMillis();
        prefs.autoRefreshLastResult = Prefs.REFRESH_FAILED;
        store.save();
        return result;
    }

    /** 登录态失效：记下标记并返回可读的错误文案。 */
    private static Result failLoginExpired(Context app, Result result) {
        Prefs prefs = ScheduleStore.get(app).data().prefs;
        prefs.autoRefreshSessionExpired = true;
        prefs.autoRefreshNoticeAt = System.currentTimeMillis();
        return fail(app, result, app.getString(R.string.auto_refresh_login_expired));
    }

    /** 会话过期通知：点击直接去重新登录。 */
    public static void notifyExpired(Context context) {
        Context app = context.getApplicationContext();
        ensureChannel(app);
        PendingIntent content = PendingIntent.getActivity(app, NOTIFY_ID,
                new Intent(app, WebImportActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_day)
                .setContentTitle(app.getString(R.string.auto_refresh_expired_title))
                .setContentText(app.getString(R.string.auto_refresh_expired_text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(content);
        try {
            NotificationManagerCompat.from(app).notify(NOTIFY_ID, builder.build());
        } catch (SecurityException ignored) {
            // 没有通知权限时静默失败
        }
    }

    private static void ensureChannel(Context app) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager =
                (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || manager.getNotificationChannel(CHANNEL) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL,
                app.getString(R.string.channel_auto_refresh_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(app.getString(R.string.channel_auto_refresh_desc));
        manager.createNotificationChannel(channel);
    }

    private static final class HttpResult {
        final String body;
        final int status;
        final List<String> setCookies;

        HttpResult(String body, int status, List<String> setCookies) {
            this.body = body;
            this.status = status;
            this.setCookies = setCookies;
        }
    }

    /**
     * 把响应的 Set-Cookie 合并进当前 Cookie 串：同名覆盖、其余保留。
     * 只取 name=value，属性（Path/Domain/HttpOnly 等）不参与请求复放。
     */
    static String mergeSetCookies(String cookie, List<String> setCookies) {
        if (setCookies == null || setCookies.isEmpty()) {
            return cookie == null ? "" : cookie;
        }
        Map<String, String> merged = new LinkedHashMap<>();
        if (cookie != null && !cookie.isEmpty()) {
            // 旧 Cookie 串本身是多段 name=value，必须逐段拆开，不能只取第一段
            for (String part : cookie.split(";")) {
                putCookiePairs(part, merged);
            }
        }
        for (String raw : setCookies) {
            putCookiePairs(raw, merged);
        }
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : merged.entrySet()) {
            if (out.length() > 0) {
                out.append("; ");
            }
            out.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return out.toString();
    }

    private static void putCookiePairs(String raw, Map<String, String> merged) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        int semicolon = raw.indexOf(';');
        String pair = semicolon >= 0 ? raw.substring(0, semicolon) : raw;
        int equals = pair.indexOf('=');
        if (equals <= 0) {
            return;
        }
        String name = pair.substring(0, equals).trim();
        String value = pair.substring(equals + 1).trim();
        if (!name.isEmpty()) {
            merged.put(name, value);
        }
    }

    private static HttpResult fetch(String url, JSONObject headers, String cookie) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            // 不跟随重定向：登录失效时的 302 直接当失败处理，避免把登录页当成课表
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Cookie", cookie);
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            if (headers != null) {
                Iterator<String> keys = headers.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    String lower = key.toLowerCase(Locale.ROOT);
                    if (lower.equals("cookie") || lower.equals("host") || lower.equals("content-length")
                            || lower.equals("accept-encoding") || lower.equals("connection")
                            || lower.equals("upgrade-insecure-requests")) {
                        continue;
                    }
                    String value = headers.optString(key, null);
                    if (value != null) {
                        conn.setRequestProperty(key, value);
                    }
                }
            }
            int code = conn.getResponseCode();
            List<String> setCookies = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase("Set-Cookie")) {
                    setCookies.addAll(entry.getValue());
                }
            }
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String body = "";
            if (in != null) {
                try {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        if (out.size() > MAX_CHARS) {
                            throw new IOException("响应过大");
                        }
                    }
                    body = out.toString("UTF-8");
                } finally {
                    in.close();
                }
            }
            return new HttpResult(body, code, setCookies);
        } finally {
            conn.disconnect();
        }
    }

    /**
     * 取 WebView 的 Cookie。{@link CookieManager} 在主线程访问。
     *
     * <p>进程刚起来时 WebView 的 provider 还没加载，getCookie 会先返回空；这里先用一个
     * 一次性 WebView 触发初始化，再重试几次，避免冷启动误用旧 Cookie 判定「登录失效」。
     */
    static String cookieFor(Context app, String url) {
        String cookie = readCookie(url);
        if (cookie != null && !cookie.isEmpty()) {
            return cookie;
        }
        warmUpWebView(app);
        for (int i = 0; i < 4; i++) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            cookie = readCookie(url);
            if (cookie != null && !cookie.isEmpty()) {
                return cookie;
            }
        }
        return cookie == null ? "" : cookie;
    }

    private static String readCookie(final String url) {
        final String[] holder = new String[1];
        final CountDownLatch latch = new CountDownLatch(1);
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                holder[0] = CookieManager.getInstance().getCookie(url);
            } catch (RuntimeException ignored) {
                // WebView 尚未初始化
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        return holder[0];
    }

    private static void warmUpWebView(final Context app) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                WebView view = new WebView(app);
                view.destroy();
            } catch (Throwable ignored) {
                // 设备没有 WebView 或已被禁用时，后面退回 meta 里的 Cookie
            }
        });
    }
}
