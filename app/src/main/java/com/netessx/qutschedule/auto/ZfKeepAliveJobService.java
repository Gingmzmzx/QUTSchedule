package com.netessx.qutschedule.auto;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.data.ZfCaptureStore;
import com.netessx.qutschedule.model.Prefs;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;

/**
 * 教务系统会话保活。
 *
 * <p>WebVPN 的登录态是服务端会话，空闲一段时间就会失效；如果等到每天/每周的刷新时间
 * 才发请求，多半已经被踢下线。这里用一个 15 分钟的周期任务，带着 Cookie 轻量请求一次
 * 课表接口，让服务端把会话的活跃时间往后推。
 *
 * <p>保活本身不写课表、不解析响应，失败也只更新「登录是否过期」这个标记；真正的失败
 * 通知由 {@link ZfRefresher} 统一发。只在开启自动刷新且已经抓过课表时才运行。
 */
public class ZfKeepAliveJobService extends JobService {

    private static final String TAG = "QutKeepAlive";
    private static final int JOB_ID = 0x41554B41; // "AUKA"
    private static final long PERIOD_MS = 15 * 60 * 1000L;

    /** 按当前设置决定是否保活。 */
    public static void sync(Context context) {
        Context app = context.getApplicationContext();
        Prefs prefs = ScheduleStore.get(app).data().prefs;
        if (prefs.autoRefreshEnabled && ZfCaptureStore.get(app).hasCapture()) {
            schedule(app);
        } else {
            cancel(app);
        }
    }

    public static void schedule(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            return;
        }
        if (scheduler.getPendingJob(JOB_ID) != null) {
            return;
        }
        JobInfo job = new JobInfo.Builder(JOB_ID,
                new ComponentName(context, ZfKeepAliveJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(PERIOD_MS, JobInfo.getMinFlexMillis())
                .setPersisted(true)
                .build();
        scheduler.schedule(job);
    }

    public static void cancel(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler != null) {
            scheduler.cancel(JOB_ID);
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            ping(this);
            jobFinished(params, false);
        }, "zf-keepalive").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }

    /** 一次轻量请求：只看状态码，不读响应体。 */
    static void ping(Context context) {
        Context app = context.getApplicationContext();
        ScheduleStore store = ScheduleStore.get(app);
        Prefs prefs = store.data().prefs;
        if (!prefs.autoRefreshEnabled) {
            cancel(app);
            return;
        }
        ZfCaptureStore capture = ZfCaptureStore.get(app);
        JSONObject meta = capture.latestMeta();
        String url = meta == null ? "" : meta.optString("url", "");
        if (url.isEmpty()) {
            cancel(app);
            return;
        }
        String cookie = ZfRefresher.cookieFor(app, url);
        boolean fromManager = cookie != null && !cookie.isEmpty();
        if (!fromManager) {
            cookie = meta.optString("cookie", "");
        }
        if (cookie == null || cookie.isEmpty()) {
            markExpired(app);
            return;
        }
        try {
            Probe probe = probe(url, meta.optJSONObject("headers"), cookie);
            // 只记状态码/来源/是否 JSON，不含 URL / Cookie / 响应内容
            Log.i(TAG, "keepalive status=" + probe.code
                    + " fromManager=" + fromManager + " json=" + probe.json);
            if (probe.code >= 200 && probe.code < 300 && probe.json) {
                if (prefs.autoRefreshSessionExpired) {
                    prefs.autoRefreshSessionExpired = false;
                    store.save();
                }
            } else if ((probe.code >= 300 && probe.code < 400) || probe.code == 401
                    || probe.code == 403 || (probe.code >= 200 && probe.code < 300)) {
                // 3xx/401/403，或 2xx 但返回的是登录页 HTML（不是 JSON），都算登录失效。
                // 但只有 Cookie 来自 WebView（可信来源）时才敢判定，避免拿旧 meta Cookie 误报。
                if (fromManager) {
                    markExpired(app);
                } else {
                    Log.i(TAG, "keepalive suspect status=" + probe.code + " (stale meta cookie)");
                }
            }
            // 5xx 等其它状态多半是服务端临时问题，不算登录失效
        } catch (IOException e) {
            // 断网、超时都不代表登录失效，等下一轮
            Log.i(TAG, "keepalive io=" + e.getClass().getSimpleName());
        }
    }

    static void markExpired(Context app) {
        ScheduleStore store = ScheduleStore.get(app);
        Prefs prefs = store.data().prefs;
        boolean changed = false;
        if (!prefs.autoRefreshSessionExpired) {
            prefs.autoRefreshSessionExpired = true;
            changed = true;
        }
        long now = System.currentTimeMillis();
        if (now - prefs.autoRefreshNoticeAt >= 24 * 60 * 60 * 1000L) {
            prefs.autoRefreshNoticeAt = now;
            changed = true;
            ZfRefresher.notifyExpired(app);
        }
        if (changed) {
            store.save();
        }
    }

    private static final class Probe {
        int code;
        boolean json;
    }

    private static Probe probe(String url, JSONObject headers, String cookie) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
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
            Probe result = new Probe();
            result.code = conn.getResponseCode();
            if (result.code >= 200 && result.code < 300) {
                try (InputStream in = conn.getInputStream()) {
                    byte[] buffer = new byte[64];
                    int read = in.read(buffer);
                    String head = read > 0
                            ? new String(buffer, 0, read, StandardCharsets.UTF_8).trim()
                            : "";
                    result.json = head.startsWith("{") || head.startsWith("[");
                }
            }
            return result;
        } finally {
            conn.disconnect();
        }
    }

}
