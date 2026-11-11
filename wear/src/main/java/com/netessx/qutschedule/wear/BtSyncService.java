package com.netessx.qutschedule.wear;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;

/**
 * 手表端的蓝牙接收服务：等手机把课表 JSON 传过来。
 *
 * <p>手表没有 Google Play 服务，用不了 Wearable Data Layer，所以直接走经典蓝牙的 RFCOMM：
 * 手表开一个带固定 UUID 的服务端 socket，手机按同一个 UUID 连上来，写一份 JSON 就完事。
 * 两台设备在系统里已经配对过，不需要额外授权（API 27 的蓝牙权限是安装时授予的）。
 *
 * <p>收完写入 {@code filesDir/schedule.json}，再发一条广播让界面重新加载。
 */
public class BtSyncService extends Service {

    /** 手机端用同一个 UUID 连接。 */
    static final UUID UUID_SYNC = UUID.fromString("6b2f3c1e-8a4d-4f7b-9c5e-2d1a0b3c4e5f");
    static final String ACTION_SYNCED = "com.netessx.qutschedule.wear.SYNCED";

    private static final String TAG = "BtSyncService";
    private static final String SERVICE_NAME = "QUTSchedule";

    private volatile boolean running;
    private BluetoothServerSocket server;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startListening();
        // 被系统杀掉后自动拉起，继续等手机连接
        return START_STICKY;
    }

    private synchronized void startListening() {
        if (running) {
            return;
        }
        BluetoothAdapter adapter;
        try {
            adapter = BluetoothAdapter.getDefaultAdapter();
        } catch (Throwable t) {
            Log.w(TAG, "取蓝牙适配器失败", t);
            return;
        }
        if (adapter == null || !adapter.isEnabled()) {
            // 蓝牙没开：等下次启动再试，不在这里阻塞
            return;
        }
        try {
            server = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, UUID_SYNC);
        } catch (Throwable t) {
            Log.w(TAG, "监听蓝牙失败", t);
            return;
        }
        running = true;
        Thread worker = new Thread(this::acceptLoop, "bt-sync");
        worker.setDaemon(true);
        worker.start();
    }

    private void acceptLoop() {
        while (running) {
            BluetoothSocket socket = null;
            try {
                socket = server.accept();
                byte[] bytes = readPayload(socket.getInputStream());
                Log.i(TAG, "收到 " + bytes.length + " 字节");
                if (!looksLikeSchedule(bytes)) {
                    // 半截数据或不是课表：保留原来那份，别把它覆盖成坏文件
                    Log.w(TAG, "收到的内容不像课表，已丢弃");
                    continue;
                }
                // 先写临时文件再改名：写到一半断开也不会毁掉旧课表
                File tmp = new File(getFilesDir(), "schedule.json.tmp");
                File target = new File(getFilesDir(), "schedule.json");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    out.write(bytes);
                }
                if (!tmp.renameTo(target)) {
                    Log.w(TAG, "改名失败，直接覆盖");
                    try (FileOutputStream out = new FileOutputStream(target)) {
                        out.write(bytes);
                    }
                    //noinspection ResultOfMethodCallIgnored
                    tmp.delete();
                }
                //noinspection ResultOfMethodCallIgnored
                target.setLastModified(System.currentTimeMillis());
                sendBroadcast(new Intent(ACTION_SYNCED).setPackage(getPackageName()));
            } catch (Throwable t) {
                if (!running) {
                    return;
                }
                Log.w(TAG, "接收课表失败", t);
                // 连接断了很正常（手机传完就关），继续等下一次
            } finally {
                closeQuietly(socket);
            }
        }
    }

    /**
     * 读一份数据：手机先写 4 字节长度，再写正文。
     *
     * <p>之前是「读到断开为止」，但手机 write 完立刻 close，蓝牙缓冲区还没发完就断了，
     * 手表只收到半截（日志：18810 字节 / 完整 21883 字节）。带长度前缀就没这个问题。
     *
     * <p>兼容旧格式：第一个字节是 {@code {} 时按裸 JSON 读到断开为止。
     *
     * <p>另：ColorOS Watch 在对端关闭时 {@code read()} 抛 IOException 而不是返回 -1，
     * 所以读取异常一律吞掉，保留已收到的部分。
     */
    private static byte[] readPayload(InputStream in) {
        int first;
        try {
            first = in.read();
        } catch (Exception e) {
            Log.w(TAG, "读取首字节失败", e);
            return new byte[0];
        }
        if (first < 0) {
            return new byte[0];
        }
        if (first == '{') {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            buffer.write(first);
            readInto(in, buffer);
            return buffer.toByteArray();
        }

        byte[] header = new byte[4];
        header[0] = (byte) first;
        int got = readFully(in, header, 1, 3);
        if (got != 3) {
            return new byte[0];
        }
        int length = ((header[0] & 0xFF) << 24) | ((header[1] & 0xFF) << 16)
                | ((header[2] & 0xFF) << 8) | (header[3] & 0xFF);
        if (length <= 0 || length > 8 * 1024 * 1024) {
            Log.w(TAG, "长度不合法：" + length);
            return new byte[0];
        }
        byte[] payload = new byte[length];
        got = readFully(in, payload, 0, length);
        if (got != length) {
            Log.w(TAG, "只收到 " + got + "/" + length + " 字节");
            byte[] partial = new byte[got];
            System.arraycopy(payload, 0, partial, 0, got);
            return partial;
        }
        return payload;
    }

    private static void readInto(InputStream in, ByteArrayOutputStream out) {
        byte[] chunk = new byte[4096];
        try {
            int read;
            while ((read = in.read(chunk)) != -1) {
                out.write(chunk, 0, read);
            }
        } catch (Exception e) {
            Log.w(TAG, "读取中断，保留已收到的 " + out.size() + " 字节", e);
        }
    }

    /** 尽量读满，返回实际读到的字节数。 */
    private static int readFully(InputStream in, byte[] target, int offset, int count) {
        int total = 0;
        try {
            while (total < count) {
                int read = in.read(target, offset + total, count - total);
                if (read < 0) {
                    break;
                }
                total += read;
            }
        } catch (Exception e) {
            Log.w(TAG, "读取中断，已收到 " + total + "/" + count + " 字节", e);
        }
        return total;
    }

    /** 只接受像样子的课表：以 { 开头、带 courses 字段、长度合理。 */
    private static boolean looksLikeSchedule(byte[] bytes) {
        if (bytes == null || bytes.length < 16) {
            return false;
        }
        // 去掉 UTF-8 BOM
        int start = bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF ? 3 : 0;
        if (bytes[start] != '{') {
            return false;
        }
        String head = new String(bytes, 0, Math.min(bytes.length, 4096));
        return head.contains("\"courses\"");
    }

    private static void closeQuietly(BluetoothSocket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (Throwable ignored) {
            // 已经关了
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        try {
            if (server != null) {
                server.close();
            }
        } catch (Throwable ignored) {
            // 已经关了
        }
        server = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
