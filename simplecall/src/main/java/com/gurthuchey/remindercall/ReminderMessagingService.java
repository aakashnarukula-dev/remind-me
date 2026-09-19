package com.gurthuchey.remindercall;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public final class ReminderMessagingService extends FirebaseMessagingService {
    @Override public void onMessageReceived(RemoteMessage message) {
        ScheduleSyncJob.scheduleImmediate(this);
    }

    @Override public void onDeletedMessages() { ScheduleSyncJob.scheduleImmediate(this); }

    @Override public void onNewToken(String token) {
        new RemoteSync(this).updateToken(token);
        ScheduleSyncJob.scheduleImmediate(this);
    }
}
