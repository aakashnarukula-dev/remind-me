package com.gurthuchey.remindercall;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

/** Device-only smoke runner used to trigger a sample call without exporting app components. */
public final class SampleLunchCallInstrumentation extends Instrumentation {
    private Bundle arguments;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        this.arguments = arguments;
        start();
    }

    @Override public void onStart() {
        Context context = getTargetContext();
        boolean medicine = arguments != null
                && "medicine".equals(arguments.getString("phase"));
        String requestedId = arguments == null ? null : arguments.getString("scheduleId");
        JSONObject requestedSchedule = null;
        JSONObject config = null;
        try {
            String raw = context.getSharedPreferences("remote_medicine_schedule",
                    Context.MODE_PRIVATE).getString("config", null);
            if (raw != null) {
                config = new JSONObject(raw);
                JSONArray schedules = config.optJSONArray("schedules");
                if (schedules != null) {
                    for (int index = 0; index < schedules.length(); index++) {
                        JSONObject candidate = schedules.optJSONObject(index);
                        if (candidate == null || !candidate.optBoolean("enabled", true)) continue;
                        boolean matches = requestedId == null || requestedId.trim().isEmpty()
                                ? candidate.optJSONArray("questions") != null
                                        && candidate.optJSONArray("questions").length() > 0
                                : requestedId.trim().equals(candidate.optString("id"));
                        if (matches) {
                            requestedSchedule = candidate;
                            break;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            requestedSchedule = null;
        }
        String scheduleId = requestedSchedule == null ? "test-call"
                : requestedSchedule.optString("id", "test-call");
        String requestedLabel = arguments == null ? null : arguments.getString("label");
        String label = requestedLabel == null || requestedLabel.trim().isEmpty()
                ? requestedSchedule == null ? "బి కాంప్లెక్స్"
                        : requestedSchedule.optString("label", "Reminder")
                : requestedLabel.trim();
        String member = config == null ? "నాన్నా"
                : config.optString("memberName", "Family member");
        Intent call = new Intent(context, CallService.class)
                .setAction(CallService.ACTION_RING)
                .putExtra(ReminderScheduler.EXTRA_ID, scheduleId)
                .putExtra(ReminderScheduler.EXTRA_PHASE, medicine
                        ? ReminderScheduler.PHASE_MEDICINE : ReminderScheduler.PHASE_MEAL)
                .putExtra("label", label)
                .putExtra("member", member)
                .putExtra("preMinutes", 30);
        context.startForegroundService(call);
        if (arguments != null && "true".equals(arguments.getString("autoAnswer"))) {
            SystemClock.sleep(1_000L);
            context.startForegroundService(new Intent(context, CallService.class)
                    .setAction(CallService.ACTION_ANSWER));
            SystemClock.sleep(500L);
            context.startForegroundService(new Intent(context, CallService.class)
                    .setAction(CallService.ACTION_CALL_SCREEN_HIDDEN));
        }
        // Ending instrumentation force-stops target package. Keep runner alive while sample call
        // is ringing or being answered, then cleanly release it. Device smoke tests may request a
        // shorter bounded window so the app can be relaunched and its real alarms re-armed quickly.
        long durationMs = 120_000L;
        if (arguments != null) {
            try {
                durationMs = Long.parseLong(arguments.getString("durationMs", "120000"));
            } catch (NumberFormatException ignored) {
                durationMs = 120_000L;
            }
        }
        SystemClock.sleep(Math.max(5_000L, Math.min(durationMs, 120_000L)));
        finish(Activity.RESULT_OK, new Bundle());
    }
}
