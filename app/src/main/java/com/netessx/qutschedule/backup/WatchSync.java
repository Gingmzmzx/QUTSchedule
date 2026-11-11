package com.netessx.qutschedule.backup;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 把课表经经典蓝牙（RFCOMM）推给手表。
 *
 * <p>手表没有 Google Play 服务，走不了 Wearable Data Layer，所以两端用同一个固定 UUID：
 * 手表开服务端 socket，手机作为客户端连上去写一份 JSON。两端在系统里已配对，
 * 不需要用户确认配对码。
 */
public final class WatchSync {

    private static final String TAG = "WatchSync";

    /** 必须和手表端 {@code BtSyncService.UUID_SYNC} 完全一致。 */
    public static final UUID UUID_SYNC = UUID.fromString("6b2f3c1e-8a4d-4f7b-9c5e-2d1a0b3c4e5f");

    private WatchSync() {
    }

    /** Android 12 起连接蓝牙设备需要运行时权限。 */
    public static boolean hasPermission(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** 已配对的设备；没有权限或蓝牙关着时返回空列表。 */
    @SuppressLint("MissingPermission")
    public static List<BluetoothDevice> bondedDevices(Context ctx) {
        List<BluetoothDevice> out = new ArrayList<>();
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) {
                return out;
            }
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded != null) {
                out.addAll(bonded);
            }
        } catch (Throwable t) {
            Log.w(TAG, "读取已配对设备失败", t);
        }
        return out;
    }

    public interface Callback {
        /** {@code error} 为 null 表示发送成功。 */
        void onDone(String error);
    }

    /** 后台线程发送，结果回主线程。 */
    public static void send(Context context, final BluetoothDevice device, final Callback callback) {
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            String error = null;
            try {
                sendBlocking(app, device);
            } catch (Throwable t) {
                Log.w(TAG, "发送课表失败", t);
                error = t.getMessage() == null ? t.toString() : t.getMessage();
            }
            final String result = error;
            new Handler(Looper.getMainLooper()).post(() -> callback.onDone(result));
        }, "watch-sync").start();
    }

    @SuppressLint("MissingPermission")
    private static void sendBlocking(Context app, BluetoothDevice device) throws Exception {
        File source = new File(app.getFilesDir(), "schedule.json");
        if (!source.exists()) {
            throw new IllegalStateException("还没有课表数据");
        }
        byte[] bytes = new byte[(int) source.length()];
        try (FileInputStream in = new FileInputStream(source)) {
            int offset = 0;
            while (offset < bytes.length) {
                int read = in.read(bytes, offset, bytes.length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
        }

        BluetoothSocket socket = device.createRfcommSocketToServiceRecord(UUID_SYNC);
        try {
            socket.connect();
            // 先写 4 字节长度再写正文：直接读到断开的话，close 太早会丢掉尾巴
            OutputStream out = socket.getOutputStream();
            java.io.DataOutputStream data = new java.io.DataOutputStream(out);
            data.writeInt(bytes.length);
            data.write(bytes);
            data.flush();
            // 给蓝牙栈一点时间把缓冲区送出去，再关闭连接
            try {
                Thread.sleep(800);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        } finally {
            try {
                socket.close();
            } catch (Throwable ignored) {
                // 已经关了
            }
        }
    }
}
