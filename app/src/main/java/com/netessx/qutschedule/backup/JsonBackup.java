package com.netessx.qutschedule.backup;

import android.content.Context;
import android.net.Uri;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.netessx.qutschedule.data.ScheduleStore;
import com.netessx.qutschedule.model.AppData;
import com.netessx.qutschedule.reminder.ReminderScheduler;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/** JSON 完整备份与恢复：带上多学期、多作息方案与偏好，换机 / 重装后一键还原。 */
public final class JsonBackup {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JsonBackup() {
    }

    public static String suggestFileName() {
        return "qut-schedule-" + LocalDate.now() + ".json";
    }

    public static void export(Context ctx, Uri target) throws IOException {
        AppData data = ScheduleStore.get(ctx).data();
        try (OutputStream out = ctx.getContentResolver().openOutputStream(target, "wt")) {
            if (out == null) {
                throw new IOException("无法写入所选位置");
            }
            try (OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        }
    }

    /**
     * 从备份恢复，覆盖当前数据。
     *
     * @return 失败原因，成功时返回 null
     */
    public static String importFrom(Context ctx, Uri source) {
        try (InputStream in = ctx.getContentResolver().openInputStream(source)) {
            if (in == null) {
                return "无法读取所选文件";
            }
            JsonObject root;
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }
            if (!root.has("semesters") && !root.has("courses")) {
                return "不是课表备份文件";
            }
            int version = root.has("version") ? root.get("version").getAsInt() : 1;
            if (version < AppData.VERSION) {
                return "备份来自旧版本，请先用旧版升级后再导出";
            }
            ScheduleStore.get(ctx).replaceAll(GSON.fromJson(root, AppData.class));
            ReminderScheduler.sync(ctx);
            return null;
        } catch (IOException | RuntimeException e) {
            return e.getMessage() == null ? e.toString() : e.getMessage();
        }
    }
}
