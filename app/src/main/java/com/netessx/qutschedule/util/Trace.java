package com.netessx.qutschedule.util;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 轻量运行轨迹：关键节点各写一行，用来排查「没有崩溃堆栈」的问题（联网失败、页面异常关闭等）。
 *
 * <p>只写应用私有目录下的 trace.log，不联网、不上传；超过 {@link #MAX_BYTES} 就丢掉前半段，
 * 保证文件不会无限增长。写日志本身出任何问题都静默 —— 排查工具不该影响主流程。
 */
public final class Trace {

    private static final String TAG = "Trace";
    private static final String FILE_NAME = "trace.log";
    private static final int MAX_BYTES = 64 * 1024;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static Context appContext;

    private Trace() {
    }

    /** 在 Application.onCreate 里调一次。 */
    public static void install(Context context) {
        appContext = context.getApplicationContext();
    }

    public static void add(String message) {
        Context ctx = appContext;
        if (ctx == null) {
            return;
        }
        String line = LocalTime.now().format(TIME) + "  " + message + "\n";
        File file = new File(ctx.getFilesDir(), FILE_NAME);
        try {
            if (file.length() > MAX_BYTES) {
                trim(file);
            }
            try (FileOutputStream out = new FileOutputStream(file, true)) {
                out.write(line.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable e) {
            Log.w(TAG, "写运行轨迹失败", e);
        }
    }

    /** 超长时只留后半段，避免文件越滚越大。 */
    private static void trim(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] all = readAll(in);
            int keepFrom = Math.max(0, all.length - MAX_BYTES / 2);
            try (FileOutputStream out = new FileOutputStream(file, false)) {
                out.write(all, keepFrom, all.length - keepFrom);
            }
        } catch (Throwable ignored) {
            // 清理失败就继续追加，下次再试
        }
    }

    private static byte[] readAll(FileInputStream in) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    public static boolean exists(Context ctx) {
        File file = new File(ctx.getFilesDir(), FILE_NAME);
        return file.exists() && file.length() > 0;
    }

    /** 没有记录时返回空串。 */
    public static String read(Context ctx) {
        File file = new File(ctx.getFilesDir(), FILE_NAME);
        if (!file.exists()) {
            return "";
        }
        try (FileInputStream in = new FileInputStream(file)) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        } catch (Throwable e) {
            Log.w(TAG, "读运行轨迹失败", e);
            return "";
        }
    }

    public static void clear(Context ctx) {
        File file = new File(ctx.getFilesDir(), FILE_NAME);
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "删除运行轨迹失败");
        }
    }
}
