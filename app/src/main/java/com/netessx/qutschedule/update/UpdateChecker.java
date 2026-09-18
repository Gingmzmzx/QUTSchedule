package com.netessx.qutschedule.update;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 检查更新：读 GitHub 的最新 release 标签，和本机版本比大小。
 *
 * <p>只比较标签里的语义化版本号（{@code v1.2.0}），不比 versionCode —— 后者带日期和构建序号，
 * release 标签里不会有。
 */
public final class UpdateChecker {

    private static final String TAG = "UpdateChecker";
    private static final String LATEST_RELEASE =
            "https://api.github.com/repos/Gingmzmzx/QUTSchedule/releases/latest";
    private static final String RELEASES_PAGE =
            "https://github.com/Gingmzmzx/QUTSchedule/releases";
    private static final int TIMEOUT_MS = 10000;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private UpdateChecker() {
    }

    /** 检查结果。 */
    public static class Result {
        public boolean ok;
        public boolean hasUpdate;
        public String currentTag = "";
        public String latestTag = "";
        public String releaseUrl = RELEASES_PAGE;
        public String notes = "";
        public String error;
    }

    public interface Callback {
        void onResult(Result result);
    }

    /** 在后台线程发起请求，结果回到主线程。 */
    public static void check(final Context context, final Callback callback) {
        final Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            final Result result = request(app);
            MAIN.post(() -> callback.onResult(result));
        });
    }

    private static Result request(Context ctx) {
        Result result = new Result();
        result.currentTag = currentVersionName(ctx);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(LATEST_RELEASE).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            // GitHub 要求带 User-Agent，否则直接 403
            conn.setRequestProperty("User-Agent", "QUTSchedule-Android");

            int code = conn.getResponseCode();
            if (code == 404) {
                // 仓库还没发布任何 release，视为已是最新
                result.ok = true;
                result.latestTag = result.currentTag;
                return result;
            }
            if (code < 200 || code >= 300) {
                result.error = "服务器返回 " + code;
                return result;
            }

            String body = readAll(conn.getInputStream());
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            result.latestTag = optString(json, "tag_name");
            String url = optString(json, "html_url");
            if (!url.isEmpty()) {
                result.releaseUrl = url;
            }
            result.notes = optString(json, "body");
            result.ok = true;
            result.hasUpdate = !result.latestTag.isEmpty()
                    && compareVersions(result.latestTag, result.currentTag) > 0;
            return result;
        } catch (Exception e) {
            Log.w(TAG, "检查更新失败", e);
            result.error = e.getMessage() == null ? e.toString() : e.getMessage();
            return result;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readAll(InputStream in) throws Exception {
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String optString(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : "";
    }

    public static String currentVersionName(Context ctx) {
        try {
            PackageInfo info = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    /**
     * 语义化版本比较，忽略开头的 {@code v}，段数不同时短的补 0。
     *
     * @return 负数表示 a 更旧，0 表示相同，正数表示 a 更新；任一边无法解析时返回 0
     */
    public static int compareVersions(String a, String b) {
        int[] left = parse(a);
        int[] right = parse(b);
        if (left == null || right == null) {
            return 0;
        }
        int length = Math.max(left.length, right.length);
        for (int i = 0; i < length; i++) {
            int x = i < left.length ? left[i] : 0;
            int y = i < right.length ? right[i] : 0;
            if (x != y) {
                return x < y ? -1 : 1;
            }
        }
        return 0;
    }

    /** 解析失败返回 null。 */
    private static int[] parse(String version) {
        if (version == null) {
            return null;
        }
        String text = version.trim();
        if (text.startsWith("v") || text.startsWith("V")) {
            text = text.substring(1);
        }
        // 去掉 -beta、+build 这类后缀
        int cut = text.indexOf('-');
        if (cut < 0) {
            cut = text.indexOf('+');
        }
        if (cut >= 0) {
            text = text.substring(0, cut);
        }
        if (text.isEmpty()) {
            return null;
        }
        String[] parts = text.split("\\.");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                numbers[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return numbers;
    }
}
