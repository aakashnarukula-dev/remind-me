package com.gurthuchey.remindercall;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public final class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String id = intent.getStringExtra(ReminderScheduler.EXTRA_ID);
        String phase = intent.getStringExtra(ReminderScheduler.EXTRA_PHASE);
        RemoteStore.Config config = new RemoteStore(context).load();
        RemoteStore.Schedule schedule = ReminderScheduler.active(config, id, phase);
        boolean retry = intent.getBooleanExtra(ReminderScheduler.EXTRA_RETRY, false);
        if (schedule == null && !retry) return;
        String label = schedule == null
                ? intent.getStringExtra(ReminderScheduler.EXTRA_LABEL) : schedule.label;
        String member = config == null
                ? intent.getStringExtra(ReminderScheduler.EXTRA_MEMBER) : config.memberName;
        int preMinutes = schedule == null
                ? intent.getIntExtra(ReminderScheduler.EXTRA_PRE_MINUTES, 30)
                : schedule.preMinutes;
        boolean recurring = intent.getBooleanExtra(ReminderScheduler.EXTRA_RECURRING, true);
        if (recurring) {
            // Reconcile the complete queue before handling this call. This advances the alarm
            // that just fired and restores any sibling reminder an OEM may have dropped.
            ReminderScheduler.scheduleAll(context, config,
                    System.currentTimeMillis() + 60_000L);
        }
        if (CallConflictDetector.isAnotherCallActive(context)) {
            ReminderScheduler.scheduleRetry(context, id, phase, label, member, preMinutes);
            return;
        }
        Intent service = new Intent(context, CallService.class).setAction(CallService.ACTION_RING)
                .putExtra(ReminderScheduler.EXTRA_ID, id)
                .putExtra(ReminderScheduler.EXTRA_PHASE, phase)
                .putExtra("label", label)
                .putExtra("member", member)
                .putExtra("doseHour", schedule == null ? -1 : schedule.hour)
                .putExtra("preMinutes", preMinutes);
        context.startForegroundService(service);
    }
}
