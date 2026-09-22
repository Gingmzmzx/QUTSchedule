package com.netessx.qutschedule.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.netessx.qutschedule.live.LiveUpdateService;

/** 闹钟在重启、改时间、改时区后会被系统清空，这里统一重建。 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        ReminderScheduler.sync(context);
        LiveUpdateService.refresh(context);
        // 开机后把常驻服务也拉回来；部分机型不允许开机启动前台服务，起不来就算了
        LiveUpdateService.start(context);
    }
}
