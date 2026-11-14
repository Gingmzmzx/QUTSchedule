package com.netessx.qutschedule.auto;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/**
 * 真正执行抓取的 JobService。
 *
 * <p>要求联网，{@code setOverrideDeadline(0)} 让它尽快跑；抓取本身是阻塞 IO，所以放到
 * 工作线程里，完成后发通知。
 */
public class AutoRefreshJobService extends JobService {

    private static final int JOB_ID = 0x41555246; // "AURF"

    private Thread worker;

    /** 把任务排进 JobScheduler；重复调用只会保留一个任务。 */
    public static void enqueue(Context context) {
        Context app = context.getApplicationContext();
        JobScheduler scheduler = (JobScheduler) app.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            return;
        }
        JobInfo job = new JobInfo.Builder(JOB_ID,
                new ComponentName(app, AutoRefreshJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setOverrideDeadline(0)
                .build();
        scheduler.schedule(job);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        worker = new Thread(() -> {
            try {
                ZfRefresher.Result result = ZfRefresher.refresh(this);
                ZfRefresher.notifyResult(this, result);
            } finally {
                jobFinished(params, false);
            }
        }, "auto-refresh");
        worker.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
        return true;
    }
}
