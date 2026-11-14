package com.netessx.qutschedule.data;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 教务系统抓取结果的本地仓库。
 *
 * <p>抓到的 URL 和响应体里可能带学号、姓名、课表等个人信息，属于账号数据，因此只写入
 * 应用私有目录（{@code getFilesDir()/zf/}）：不打印日志、不参与系统备份、也不进入
 * JSON 导出。文件权限再尽力收紧到仅本应用可读写。
 */
public final class ZfCaptureStore {

    private static final String DIR = "zf";
    private static final String BODY_FILE = "xskb.json";
    private static final String META_FILE = "xskb.meta.json";
    private static final int MAX_CHARS = 8 * 1024 * 1024;

    /** 课程来源标记：来自教务系统抓取。 */
    public static final String SOURCE_ZF = "zf";

    /** meta 的读改写要跨实例互斥：{@link #get(Context)} 每次都返回新对象。 */
    private static final Object META_LOCK = new Object();

    private final File baseDir;

    /** 供单元测试指定临时目录，正式代码走 {@link #get(Context)}。 */
    ZfCaptureStore(File baseDir) {
        this.baseDir = baseDir;
    }

    public static ZfCaptureStore get(Context context) {
        return new ZfCaptureStore(
                new File(context.getApplicationContext().getFilesDir(), DIR));
    }

    private File dir() throws IOException {
        if (!baseDir.isDirectory() && !baseDir.mkdirs() && !baseDir.isDirectory()) {
            throw new IOException("无法创建私有目录");
        }
        return baseDir;
    }

    /** 原子写入：先写临时文件再改名，避免抓取中断留下半截 JSON。 */
    public synchronized void save(String url, String method, String body, int status, String source)
            throws IOException {
        save(url, method, body, status, source, null);
    }

    /**
     * 同上，并记录一份脱敏后的请求头，供后台自动刷新时复放请求。
     * 调用方必须剔除 Cookie、Host 等会泄漏登录态或导致请求非法的字段。
     */
    public synchronized void save(String url, String method, String body, int status, String source,
                                  JSONObject headers) throws IOException {
        if (body == null || body.isEmpty()) {
            throw new IOException("响应为空");
        }
        if (body.length() > MAX_CHARS) {
            throw new IOException("响应过大");
        }
        File d = dir();
        writeAtomically(new File(d, BODY_FILE), body.getBytes(StandardCharsets.UTF_8));

        synchronized (META_LOCK) {
            File metaFile = new File(d, META_FILE);
            JSONObject meta = new JSONObject();
            // 兜底 Cookie 的生命周期比单次抓取长：整体重写 meta 前先继承下来，
            // 否则自动刷新成功一次后，冷启动就再也读不到登录态了。
            inheritCookie(metaFile, meta);
            try {
                meta.put("url", url == null ? "" : url);
                meta.put("method", method == null ? "GET" : method);
                meta.put("capturedAt", System.currentTimeMillis());
                meta.put("status", status);
                meta.put("source", source == null ? "" : source);
                meta.put("chars", body.length());
                if (headers != null && headers.length() > 0) {
                    meta.put("headers", headers);
                }
            } catch (JSONException ignored) {
                // 键都是常量，不会发生
            }
            writeAtomically(metaFile, meta.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    /** 旧 meta 里的兜底 Cookie 原样带进新 meta；旧文件损坏时直接放弃，不影响本次保存。 */
    private static void inheritCookie(File metaFile, JSONObject meta) {
        if (!metaFile.isFile()) {
            return;
        }
        try {
            JSONObject old = new JSONObject(new String(
                    Files.readAllBytes(metaFile.toPath()), StandardCharsets.UTF_8));
            String cookie = old.optString("cookie", "");
            if (!cookie.isEmpty()) {
                meta.put("cookie", cookie);
                meta.put("cookieAt", old.optLong("cookieAt", 0));
            }
        } catch (IOException | JSONException ignored) {
            // 旧元数据损坏时丢掉兜底 Cookie，本次抓取仍然成功
        }
    }

    /**
     * 记住当前登录 Cookie，供后台自动刷新在 WebView 尚未初始化（冷启动）时兜底。
     * 与响应体一样只落在私有目录，不打印、不导出、不备份。
     */
    public synchronized void saveCookie(String url, String cookie) {
        if (cookie == null || cookie.trim().isEmpty()) {
            return;
        }
        synchronized (META_LOCK) {
            try {
                File d = dir();
                File f = new File(d, META_FILE);
                JSONObject meta = f.isFile()
                        ? new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8))
                        : new JSONObject();
                meta.put("cookie", cookie);
                meta.put("cookieAt", System.currentTimeMillis());
                writeAtomically(f, meta.toString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException | JSONException ignored) {
                // 存不下就算了，自动刷新还有 CookieManager 这条主路径
            }
        }
    }

    private static void writeAtomically(File target, byte[] data) throws IOException {
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
            out.getFD().sync();
        }
        if (!tmp.renameTo(target)) {
            // 部分文件系统不允许 rename 覆盖已有文件，先删旧文件再改名。
            if (target.exists() && !target.delete()) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                throw new IOException("无法替换旧文件");
            }
            if (!tmp.renameTo(target)) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                throw new IOException("无法写入文件");
            }
        }
        restrict(target);
    }

    private static void restrict(File file) {
        // 应用私有目录本来就只有本 uid 可访问，这里再收紧到 0600。
        //noinspection ResultOfMethodCallIgnored
        file.setReadable(false, false);
        //noinspection ResultOfMethodCallIgnored
        file.setReadable(true, true);
        //noinspection ResultOfMethodCallIgnored
        file.setWritable(false, false);
        //noinspection ResultOfMethodCallIgnored
        file.setWritable(true, true);
    }

    public synchronized boolean hasCapture() {
        try {
            return new File(dir(), BODY_FILE).isFile();
        } catch (IOException e) {
            return false;
        }
    }

    /** 已保存的响应体；没有则返回 null。 */
    public synchronized String latestBody() {
        try {
            File f = new File(dir(), BODY_FILE);
            if (!f.isFile()) {
                return null;
            }
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /** 请求元数据（URL、时间、状态）；没有则返回 null。 */
    public synchronized JSONObject latestMeta() {
        try {
            File f = new File(dir(), META_FILE);
            if (!f.isFile()) {
                return null;
            }
            return new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    /** 连同抓取结果一起清掉。登录态由调用方清理。 */
    public synchronized void clear() {
        File d = baseDir;
        File[] files = d.listFiles();
        if (files != null) {
            for (File f : files) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
        //noinspection ResultOfMethodCallIgnored
        d.delete();
    }
}
