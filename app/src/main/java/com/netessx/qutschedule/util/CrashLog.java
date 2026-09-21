package com.netessx.qutschedule.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.netessx.qutschedule.ui.CrashActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 崩溃记录：把未捕获异常写到应用私有目录，供用户导出后反馈。
 *
 * <p>只保留最后一次，「关于」页可以分享或删除。全程不联网、不上传；写日志本身失败也不会再抛异常，
 * 否则会在崩溃处理里二次崩溃。
 */
public final class CrashLog {

    private static final String TAG = "CrashLog";
    private static final String FILE_NAME = "crash.log";
    private static final String PENDING_NAME = "crash.pending";
    /** 太长的话分享出去也没人看得完，够定位就行。 */
    private static final int MAX_CHARS = 20000;

    private CrashLog() {
    }

    /** 在 Application 里调一次即可。 */
    public static void install(final Context context) {
        final Context app = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            String text = "";
            try {
                write(app, throwable);
                markPending(app);
                text = read(app);
            } catch (Throwable ignored) {
                // 记录失败也必须往下走，否则用户连崩溃界面都看不到
            }
            openCrashScreen(app, text);
            // 交回系统默认处理，让它结束进程；崩溃界面由闹钟在进程结束后拉起
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        });
    }

    /**
     * 用闹钟拉起崩溃界面。
     *
     * <p>进程马上就要被系统结束，直接 {@code startActivity} 常常来不及显示；交给系统闹钟，
     * 等新进程起来再展示。这样即使「一启动就崩」，用户也一定看得到这个界面。
     */
    private static void openCrashScreen(Context app, String text) {
        Intent intent = new Intent(app, CrashActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(CrashActivity.EXTRA_LOG, text);
        try {
            PendingIntent pending = PendingIntent.getActivity(app, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager alarms = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
            if (alarms != null) {
                alarms.setExact(AlarmManager.RTC, System.currentTimeMillis() + 300, pending);
                return;
            }
        } catch (Throwable ignored) {
            // 没有精确闹钟权限等情况，退回直接启动
        }
        try {
            app.startActivity(intent);
        } catch (Throwable ignored) {
            // 实在起不来就只能靠系统默认的崩溃提示了
        }
    }

    private static void write(Context ctx, Throwable throwable) {
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file(ctx)), StandardCharsets.UTF_8)) {
            writer.write(header(ctx));
            writer.write("\n\n");
            writer.write(stackOf(throwable));
        } catch (Exception e) {
            Log.w(TAG, "写崩溃日志失败", e);
        }
    }

    private static String header(Context ctx) {
        String version = "";
        try {
            PackageInfo info = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            version = info.versionName + " (" + info.getLongVersionCode() + ")";
        } catch (PackageManager.NameNotFoundException ignored) {
            // 版本号取不到就算了，堆栈才是重点
        }
        return "time=" + LocalDateTime.now()
                + "\napp=" + version
                + "\ndevice=" + Build.MANUFACTURER + " " + Build.MODEL
                + "\nandroid=" + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
    }

    private static String stackOf(Throwable throwable) {
        StringWriter buffer = new StringWriter();
        PrintWriter printer = new PrintWriter(buffer);
        throwable.printStackTrace(printer);
        printer.flush();
        return buffer.toString();
    }

    public static File file(Context ctx) {
        return new File(ctx.getFilesDir(), FILE_NAME);
    }

    private static File pendingFile(Context ctx) {
        return new File(ctx.getFilesDir(), PENDING_NAME);
    }

    /**
     * 标记「有还没看过的崩溃」，下次打开应用时直接进崩溃界面。
     *
     * <p>崩溃当场那条路只能靠后台启动 Activity，从 Android 10 起系统默认拦掉，
     * 所以必须有这个兜底：即使当时什么都没弹出来，用户下次点开应用也能看到日志。
     */
    private static void markPending(Context ctx) {
        try (FileOutputStream out = new FileOutputStream(pendingFile(ctx))) {
            out.write('1');
        } catch (Exception e) {
            Log.w(TAG, "写崩溃标记失败", e);
        }
    }

    public static boolean hasPending(Context ctx) {
        return pendingFile(ctx).exists();
    }

    public static void clearPending(Context ctx) {
        File file = pendingFile(ctx);
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "删除崩溃标记失败");
        }
    }

    public static boolean exists(Context ctx) {
        File file = file(ctx);
        return file.exists() && file.length() > 0;
    }

    /** 没有记录时返回空串。 */
    public static String read(Context ctx) {
        File file = file(ctx);
        if (!file.exists()) {
            return "";
        }
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) Math.min(file.length(), MAX_CHARS)];
            int read = in.read(buffer);
            return read <= 0 ? "" : new String(buffer, 0, read, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.w(TAG, "读崩溃日志失败", e);
            return "";
        }
    }

    public static void clear(Context ctx) {
        File file = file(ctx);
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "删除崩溃日志失败");
        }
    }
}
