package com.netessx.qutschedule.backup;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.model.AppData;
import com.netessx.qutschedule.reminder.ReminderScheduler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * WebDAV 云备份：把整份课表 PUT 到自建服务器，或 GET 回来覆盖本地。
 *
 * <p>请求的是同一份 JSON，所以云端备份与本地导出可以互换。
 */
public final class WebDavClient {

    private static final String PREFS = "webdav";
    private static final String KEY_URL = "url";
    private static final String KEY_USER = "user";
    private static final String KEY_PASSWORD = "password";
    private static final String REMOTE_FILE = "qut-schedule.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int TIMEOUT_MS = 15000;

    private WebDavClient() {
    }

    /** 连接配置。 */
    public static class Config {
        public String url = "";
        public String user = "";
        public String password = "";

        public boolean isValid() {
            return url != null && (url.startsWith("http://") || url.startsWith("https://"));
        }
    }

    public static Config load(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Config config = new Config();
        config.url = prefs.getString(KEY_URL, "");
        config.user = prefs.getString(KEY_USER, "");
        config.password = prefs.getString(KEY_PASSWORD, "");
        return config;
    }

    public static void save(Context ctx, Config config) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_URL, config.url)
                .putString(KEY_USER, config.user)
                .putString(KEY_PASSWORD, config.password)
                .apply();
    }

    public static void upload(Context ctx, Config config) throws IOException {
        byte[] body = GSON.toJson(ScheduleStore.get(ctx).data())
                .getBytes(StandardCharsets.UTF_8);
        HttpURLConnection conn = open(config, "PUT");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setFixedLengthStreamingMode(body.length);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(body);
        }
        checkResponse(conn);
        conn.disconnect();
    }

    public static void download(Context ctx, Config config) throws IOException {
        HttpURLConnection conn = open(config, "GET");
        checkResponse(conn);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream in = conn.getInputStream()) {
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
        } finally {
            conn.disconnect();
        }
        AppData data = GSON.fromJson(
                new String(buffer.toByteArray(), StandardCharsets.UTF_8), AppData.class);
        if (data == null) {
            throw new IOException("云端返回的内容不是课表备份");
        }
        ScheduleStore.get(ctx).replaceAll(data);
        ReminderScheduler.sync(ctx);
    }

    private static HttpURLConnection open(Config config, String method) throws IOException {
        if (!config.isValid()) {
            throw new IOException("请先填写服务器地址");
        }
        String base = config.url.endsWith("/") ? config.url : config.url + "/";
        HttpURLConnection conn = (HttpURLConnection) new URL(base + REMOTE_FILE).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        if (config.user != null && !config.user.isEmpty()) {
            String token = config.user + ":" + (config.password == null ? "" : config.password);
            conn.setRequestProperty("Authorization", "Basic "
                    + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8)));
        }
        return conn;
    }

    private static void checkResponse(HttpURLConnection conn) throws IOException {
        int code = conn.getResponseCode();
        if (code == 401 || code == 403) {
            throw new IOException("认证失败（" + code + "）");
        }
        if (code < 200 || code >= 300) {
            throw new IOException("服务器返回 " + code);
        }
    }
}
