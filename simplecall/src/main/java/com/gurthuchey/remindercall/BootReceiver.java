package com.gurthuchey.remindercall;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        ReminderScheduler.scheduleAll(context, new RemoteStore(context).load());
        ReminderScheduler.restoreRetries(context);
        ScheduleSyncJob.scheduleCatchUp(context);
    }
}
