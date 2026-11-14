package com.netessx.qutschedule.auto;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.netessx.qutschedule.data.ScheduleStore;

/** 定时闹钟的接收器：先排下一次，再把抓取交给 JobService。 */
public class AutoRefreshReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !AutoRefreshScheduler.ACTION_REFRESH.equals(intent.getAction())) {
            return;
        }
        Context app = context.getApplicationContext();
        // 先排下一次，哪怕这次抓取失败也保持每天 / 每周的节奏
        AutoRefreshScheduler.reschedule(app);
        if (!ScheduleStore.get(app).data().prefs.autoRefreshEnabled) {
            return;
        }
        AutoRefreshJobService.enqueue(app);
    }
}
