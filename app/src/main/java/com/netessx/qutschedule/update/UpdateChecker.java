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
 * 检查更新：读自建接口 {@code latest.json} 里的最新版本，和本机比大小。
 *
 * <p>接口是一份静态 JSON，形如
 * {@code {"versionName":"v1.3.1","versionCode":"26092002","desc":"修复了…"}}。
 * 先比 {@code versionCode}（带日期与构建序号，最可靠），拿不到再退回比语义化版本号。
 * 用自建接口而不是 GitHub API，是因为后者在国内经常连不上。
 */
public final class UpdateChecker {

    private static final String TAG = "UpdateChecker";
    private static final String LATEST_JSON = "https://qutschedule.netessx.com/latest.json";
    private static final String RELEASES_PAGE =
            "https://github.com/Gingmzmzx/QUTSchedule/releases/latest";
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
            conn = (HttpURLConnection) new URL(LATEST_JSON).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            // 带上 UA，免得被中间的 CDN / 防火墙当成脚本请求拦掉
            conn.setRequestProperty("User-Agent", "QUTSchedule-Android");

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                result.error = "服务器返回 " + code;
                return result;
            }

            String body = readAll(conn.getInputStream());
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            result.latestTag = optString(json, "versionName");
            result.notes = optString(json, "desc");
            result.ok = true;
            result.hasUpdate = isNewer(ctx, optString(json, "versionCode"), result.latestTag);
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

    /**
     * 有没有新版本：能拿到两边 versionCode 时以它为准，否则退回比语义化版本号。
     *
     * <p>版本号写错只会让人错过更新，versionCode 比错却会让人装到更旧的包，所以宁可退回版本号比。
     */
    private static boolean isNewer(Context ctx, String remoteCode, String remoteTag) {
        long remote = parseLong(remoteCode);
        long local = currentVersionCode(ctx);
        if (remote > 0 && local > 0) {
            return remote > local;
        }
        return !remoteTag.isEmpty() && compareVersions(remoteTag, currentVersionName(ctx)) > 0;
    }

    private static long parseLong(String text) {
        if (text == null || text.isEmpty()) {
            return -1;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** versionCode 在 API 28 起是 long，低版本只能读已废弃的 int 字段。 */
    public static long currentVersionCode(Context ctx) {
        try {
            PackageInfo info = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P
                    ? info.getLongVersionCode()
                    : info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
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
