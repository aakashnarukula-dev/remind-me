package com.gurthuchey.remindercall;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class CallActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        Intent service = new Intent(context, CallService.class).setAction(intent.getAction());
        service.putExtras(intent);
        context.startService(service);
    }
}
