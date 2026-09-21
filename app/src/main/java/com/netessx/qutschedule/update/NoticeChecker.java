package com.netessx.qutschedule.update;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 拉取服务端公告。
 *
 * <p>接口是一份静态 JSON：{@code {"id":1,"time":1789992033,"title":"…","content":"…"}}，
 * {@code content} 里可以带 {@code <br>} 与 {@code <a>}，由界面按 HTML 渲染。
 *
 * <p>和检查更新同一套模式：后台请求、主线程回调、失败一律静默 —— 断网或服务器挂了
 * 都不该打扰用户。
 */
public final class NoticeChecker {

    private static final String TAG = "NoticeChecker";
    private static final String NOTICE_JSON = "https://qutschedule.netessx.com/notice.json";
    private static final int TIMEOUT_MS = 10000;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private NoticeChecker() {
    }

    /** 公告内容。{@code id} 不大于 0 视为没有公告。 */
    public static class Result {
        public boolean ok;
        public int id;
        /** 发布时间，Unix 秒；拿不到时为 0。 */
        public long time;
        public String title = "";
        public String content = "";
        public String error;
    }

    public interface Callback {
        void onResult(Result result);
    }

    public static void check(final Context context, final Callback callback) {
        EXECUTOR.execute(() -> {
            final Result result = request();
            MAIN.post(() -> callback.onResult(result));
        });
    }

    private static Result request() {
        Result result = new Result();
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(NOTICE_JSON).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "QUTSchedule-Android");

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                result.error = "服务器返回 " + code;
                return result;
            }
            String body = UpdateChecker.readAll(conn.getInputStream());
            JsonObject json = JsonParser.parseString(tolerant(body)).getAsJsonObject();
            result.id = json.has("id") && !json.get("id").isJsonNull()
                    ? json.get("id").getAsInt() : 0;
            result.time = json.has("time") && !json.get("time").isJsonNull()
                    ? json.get("time").getAsLong() : 0L;
            result.title = optString(json, "title");
            result.content = optString(json, "content");
            result.ok = result.id > 0;
            return result;
        } catch (Exception e) {
            Log.w(TAG, "获取公告失败", e);
            result.error = e.getMessage() == null ? e.toString() : e.getMessage();
            return result;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 容错：去掉 {@code } } / {@code ]} 前多余的逗号。
     *
     * <p>服务端是手写的静态文件，很容易写出「最后一项后面也带逗号」这种不合法 JSON ——
     * 严格解析会直接失败，而公告又不该因此静默消失，所以先清洗一遍。
     */
    private static String tolerant(String body) {
        return body == null ? "" : body.replaceAll(",\\s*([}\\]])", "$1");
    }

    private static String optString(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : "";
    }
}
