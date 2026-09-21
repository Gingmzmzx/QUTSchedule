package com.netessx.qutschedule.live;

import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;

/**
 * 把「正在上的这节课」伪装成一个正在播放的媒体会话。
 *
 * <p>系统的灵动岛 / 锁屏 / 状态栏媒体卡片认的是 {@link MediaSession}，不管内容是不是真的音频，
 * 所以把进度按上课时间喂给它，各家的岛就会显示课程名和进度条 —— 一套代码适配所有机型，
 * 不用去猜各家 ROM 的私有接口。
 *
 * <p>不播放任何音频，也不申请音频焦点：只是把状态标成 {@code STATE_PLAYING} 并给出速度 1.0，
 * 系统自己会把进度往前推，我们每 30 秒（{@link LiveUpdateService} 的刷新周期）校正一次。
 */
final class ClassMediaSession {

    private static MediaSession session;

    private ClassMediaSession() {
    }

    static MediaSession.Token token(Context ctx) {
        return obtain(ctx).getSessionToken();
    }

    private static MediaSession obtain(Context ctx) {
        if (session == null) {
            session = new MediaSession(ctx.getApplicationContext(), "QUTSchedule");
            // 不注册 Callback：用户按播放/暂停没有副作用，系统会自行恢复状态
            session.setActive(true);
        }
        return session;
    }

    /**
     * 更新当前进度。
     *
     * @param positionMs 这节课已经过去的毫秒数
     * @param durationMs 这节课的总时长
     */
    static void update(Context ctx, String title, String detail, long positionMs, long durationMs) {
        try {
            MediaSession target = obtain(ctx);
            MediaMetadata metadata = new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, detail)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
                    .build();
            target.setMetadata(metadata);

            PlaybackState state = new PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING, positionMs, 1f)
                    .build();
            target.setPlaybackState(state);
            target.setActive(true);
        } catch (Throwable ignored) {
            // 厂商 ROM 对 MediaSession 的限制各异，失败只是没有媒体卡片，不该影响通知
        }
    }

    /** 课程结束或功能关闭：收回会话，媒体卡片与岛会跟着消失。 */
    static void stop() {
        if (session == null) {
            return;
        }
        try {
            session.setActive(false);
            session.release();
        } catch (Throwable ignored) {
            // 同上
        }
        session = null;
    }
}
