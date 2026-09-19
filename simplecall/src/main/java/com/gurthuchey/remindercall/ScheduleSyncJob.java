package com.gurthuchey.remindercall;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

public final class ScheduleSyncJob extends JobService {
    private static final int CATCH_UP = 76101;
    private static final int IMMEDIATE = 76102;

    static void scheduleCatchUp(Context context) { schedule(context, CATCH_UP, true); }
    static void scheduleImmediate(Context context) { schedule(context, IMMEDIATE, false); }

    private static void schedule(Context context, int id, boolean persisted) {
        JobInfo info = new JobInfo.Builder(id, new ComponentName(context, ScheduleSyncJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(persisted)
                .build();
        context.getSystemService(JobScheduler.class).schedule(info);
    }

    @Override public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            boolean success = RemoteSync.syncBlocking(getApplicationContext());
            boolean retry = !success && new RemoteStore(this).pairing() != null;
            jobFinished(params, retry);
        }, "reminder-call-sync").start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) { return true; }
}
