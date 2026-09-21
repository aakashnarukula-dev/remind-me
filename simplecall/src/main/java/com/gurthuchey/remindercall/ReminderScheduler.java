package com.gurthuchey.remindercall;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.Map;

final class ReminderScheduler {
    static final String PHASE_MEDICINE = "medicine";
    static final String PHASE_MEAL = "meal";
    static final String PHASE_CONFIRMATION = "confirmation";
    static final String EXTRA_ID = "scheduleId";
    static final String EXTRA_PHASE = "phase";
    static final String EXTRA_RECURRING = "recurring";
    static final String EXTRA_RETRY = "retry";
    static final String EXTRA_LABEL = "label";
    static final String EXTRA_MEMBER = "member";
    static final String EXTRA_PRE_MINUTES = "preMinutes";
    static final long RETRY_DELAY_MS = 5L * 60_000L;
    private static final int RETRY_POLICY_VERSION = 2;
    private static final String RETRIES = "pending_call_retries";

    private ReminderScheduler() {}

    static void scheduleAll(Context context, RemoteStore.Config config) {
        scheduleAll(context, config, System.currentTimeMillis() + 1000L);
    }

    static void scheduleAll(Context context, RemoteStore.Config config, long after) {
        if (config == null) return;
        for (RemoteStore.Schedule schedule : config.schedules) {
            if (!schedule.enabled) continue;
            schedulePhase(context, schedule, PHASE_MEDICINE, after);
            if (!schedule.custom() && schedule.preMinutes > 0) {
                schedulePhase(context, schedule, PHASE_MEAL, after);
            }
        }
    }

    static void cancelAll(Context context, RemoteStore.Config config) {
        if (config == null) return;
        cancelScheduledCalls(context, config);
        for (RemoteStore.Schedule schedule : config.schedules) {
            cancelRetry(context, schedule.id, PHASE_MEDICINE);
            cancelRetry(context, schedule.id, PHASE_MEAL);
            cancelRetry(context, schedule.id, PHASE_CONFIRMATION);
        }
    }

    /** Replaces daily alarms without erasing an unanswered call's five-minute retry. */
    static void replaceScheduledCalls(Context context, RemoteStore.Config previous,
            RemoteStore.Config replacement) {
        cancelScheduledCalls(context, previous);
        cancelRetriesRemovedByUpdate(context, previous, replacement);
        clearProgressChangedByUpdate(context, previous, replacement);
        scheduleAll(context, replacement);
    }

    static boolean occurrenceChanged(RemoteStore.Schedule previous,
            RemoteStore.Schedule replacement) {
        if (previous == null || replacement == null) return false;
        return previous.hour != replacement.hour
                || previous.minute != replacement.minute
                || previous.days != replacement.days
                || previous.preMinutes != replacement.preMinutes
                || previous.confirmationMinutes != replacement.confirmationMinutes
                || previous.enabled != replacement.enabled
                || previous.custom() != replacement.custom();
    }

    static void clearProgressIfOccurrenceChanged(Context context,
            RemoteStore.Schedule previous, RemoteStore.Schedule replacement) {
        if (!occurrenceChanged(previous, replacement)) return;
        clearProgress(context, replacement.id);
    }

    private static void clearProgressChangedByUpdate(Context context,
            RemoteStore.Config previous, RemoteStore.Config replacement) {
        if (previous == null || replacement == null) return;
        for (RemoteStore.Schedule before : previous.schedules) {
            RemoteStore.Schedule after = find(replacement, before.id);
            clearProgressIfOccurrenceChanged(context, before, after);
        }
    }

    private static RemoteStore.Schedule find(RemoteStore.Config config, String id) {
        if (config == null || id == null) return null;
        for (RemoteStore.Schedule schedule : config.schedules) {
            if (id.equals(schedule.id)) return schedule;
        }
        return null;
    }

    private static void clearProgress(Context context, String scheduleId) {
        if (scheduleId == null || scheduleId.trim().isEmpty()) return;
        cancelRetry(context, scheduleId, PHASE_MEDICINE);
        cancelRetry(context, scheduleId, PHASE_MEAL);
        cancelRetry(context, scheduleId, PHASE_CONFIRMATION);
        new DailyCallStatus(context).clearSchedule(scheduleId);
    }

    private static void cancelScheduledCalls(Context context, RemoteStore.Config config) {
        if (config == null) return;
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        for (RemoteStore.Schedule schedule : config.schedules) {
            alarms.cancel(alarmIntent(context, schedule.id, PHASE_MEDICINE, true));
            alarms.cancel(alarmIntent(context, schedule.id, PHASE_MEAL, true));
        }
    }

    private static void cancelRetriesRemovedByUpdate(Context context,
            RemoteStore.Config previous, RemoteStore.Config replacement) {
        if (previous == null) return;
        for (RemoteStore.Schedule schedule : previous.schedules) {
            if (active(replacement, schedule.id, PHASE_MEDICINE) == null) {
                cancelRetry(context, schedule.id, PHASE_MEDICINE);
            }
            if (active(replacement, schedule.id, PHASE_MEAL) == null) {
                cancelRetry(context, schedule.id, PHASE_MEAL);
            }
            if (active(replacement, schedule.id, PHASE_CONFIRMATION) == null) {
                cancelRetry(context, schedule.id, PHASE_CONFIRMATION);
            }
        }
    }

    static RemoteStore.Schedule active(RemoteStore.Config config, String id, String phase) {
        if (config == null || id == null) return null;
        for (RemoteStore.Schedule schedule : config.schedules) {
            if (!id.equals(schedule.id) || !schedule.enabled) continue;
            if (PHASE_MEAL.equals(phase) && (schedule.custom() || schedule.preMinutes <= 0)) {
                return null;
            }
            if (PHASE_CONFIRMATION.equals(phase) && schedule.confirmationMinutes <= 0) {
                return null;
            }
            return schedule;
        }
        return null;
    }

    static void scheduleNext(Context context, String id, String phase) {
        RemoteStore.Config config = new RemoteStore(context).load();
        RemoteStore.Schedule schedule = active(config, id, phase);
        if (schedule != null) schedulePhase(context, schedule, phase, System.currentTimeMillis() + 60_000L);
    }

    static void scheduleRetry(Context context, String id, String phase) {
        RemoteStore.Config config = new RemoteStore(context).load();
        RemoteStore.Schedule schedule = active(config, id, phase);
        if (schedule == null) return;
        scheduleRetry(context, id, phase, schedule.label,
                config.memberName, schedule.preMinutes);
    }

    /**
     * Persists the complete retry payload before registering the alarm. The active call already
     * has everything needed to repeat itself, so a Firebase refresh must not be able to erase an
     * unanswered call merely because the cached schedule is briefly unavailable.
     */
    static void scheduleRetry(Context context, String id, String phase, String label,
            String member, int preMinutes) {
        long at = System.currentTimeMillis() + RETRY_DELAY_MS;
        saveRetry(context, id, phase, label, member, preMinutes, at);
        scheduleRetryAt(context, id, phase, label, member, preMinutes, at);
    }

    static void scheduleRetryAfter(Context context, String id, String phase, String label,
            String member, int preMinutes, int delayMinutes) {
        long at = System.currentTimeMillis() + Math.max(1, delayMinutes) * 60_000L;
        saveRetry(context, id, phase, label, member, preMinutes, at);
        scheduleRetryAt(context, id, phase, label, member, preMinutes, at);
    }

    static boolean scheduleRetryAtTime(Context context, String id, String phase, String label,
            String member, int preMinutes, long at) {
        if (!isLaterToday(System.currentTimeMillis(), at)) return false;
        saveRetry(context, id, phase, label, member, preMinutes, at);
        scheduleRetryAt(context, id, phase, label, member, preMinutes, at);
        return true;
    }

    static boolean isLaterToday(long now, long at) {
        if (at <= now) return false;
        Calendar current = Calendar.getInstance();
        current.setTimeInMillis(now);
        Calendar selected = Calendar.getInstance();
        selected.setTimeInMillis(at);
        return current.get(Calendar.ERA) == selected.get(Calendar.ERA)
                && current.get(Calendar.YEAR) == selected.get(Calendar.YEAR)
                && current.get(Calendar.DAY_OF_YEAR) == selected.get(Calendar.DAY_OF_YEAR);
    }

    static void scheduleConfirmation(Context context, String id) {
        RemoteStore.Config config = new RemoteStore(context).load();
        RemoteStore.Schedule schedule = active(config, id, PHASE_CONFIRMATION);
        if (schedule == null) return;
        scheduleRetryAfter(context, schedule.id, PHASE_CONFIRMATION, schedule.label,
                config.memberName, schedule.preMinutes, schedule.confirmationMinutes);
    }

    private static void scheduleRetryAt(Context context, String id, String phase, String label,
            String member, int preMinutes, long at) {
        Intent intent = receiverIntent(context, id, phase, false)
                .putExtra(EXTRA_RETRY, true)
                .putExtra(EXTRA_LABEL, safe(label, "Medicine"))
                .putExtra(EXTRA_MEMBER, safe(member, "Family member"))
                .putExtra(EXTRA_PRE_MINUTES, Math.max(1, preMinutes))
                .setData(Uri.parse("remindercall://retry/" + Uri.encode(id) + "/" + phase));
        PendingIntent pending = PendingIntent.getBroadcast(context, retryCode(id, phase), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        set(context, at, pending);
        new DailyCallStatus(context).markRetry(id, phase, at);
    }

    static void cancelRetry(Context context, String id, String phase) {
        Intent intent = new Intent(context, AlarmReceiver.class)
                .setData(Uri.parse("remindercall://retry/" + Uri.encode(id) + "/" + phase));
        PendingIntent pending = PendingIntent.getBroadcast(context, retryCode(id, phase), intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pending != null) {
            context.getSystemService(AlarmManager.class).cancel(pending);
            pending.cancel();
        }
        context.getSharedPreferences(RETRIES, Context.MODE_PRIVATE).edit()
                .remove(retryKey(id, phase)).commit();
    }

    /** Cancels every remaining occurrence today, then restores the normal recurring schedule. */
    static void skipRemainingToday(Context context, String id) {
        if (id == null || id.trim().isEmpty() || "test-call".equals(id)) return;
        cancelRetry(context, id, PHASE_MEDICINE);
        cancelRetry(context, id, PHASE_MEAL);
        cancelRetry(context, id, PHASE_CONFIRMATION);

        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        alarms.cancel(alarmIntent(context, id, PHASE_MEDICINE, true));
        alarms.cancel(alarmIntent(context, id, PHASE_MEAL, true));

        RemoteStore.Config config = new RemoteStore(context).load();
        RemoteStore.Schedule schedule = active(config, id, PHASE_MEDICINE);
        if (schedule == null) return;
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        tomorrow.set(Calendar.HOUR_OF_DAY, 0);
        tomorrow.set(Calendar.MINUTE, 0);
        tomorrow.set(Calendar.SECOND, 0);
        tomorrow.set(Calendar.MILLISECOND, 0);
        long after = tomorrow.getTimeInMillis();
        schedulePhase(context, schedule, PHASE_MEDICINE, after);
        if (!schedule.custom() && schedule.preMinutes > 0) {
            schedulePhase(context, schedule, PHASE_MEAL, after);
        }
    }

    /** Restores unanswered-call retries after a reboot or app update. */
    static void restoreRetries(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(RETRIES, Context.MODE_PRIVATE);
        Map<String, ?> saved = preferences.getAll();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, ?> entry : saved.entrySet()) {
            if (!(entry.getValue() instanceof String)) continue;
            try {
                JSONObject json = new JSONObject((String) entry.getValue());
                String id = json.getString("id");
                String phase = json.getString("phase");
                RemoteStore.Config config = new RemoteStore(context).load();
                RemoteStore.Schedule current = active(config, id, phase);
                if (current == null) {
                    preferences.edit().remove(entry.getKey()).commit();
                    continue;
                }
                long at = restoredRetryAt(json.optInt("policyVersion", 1),
                        json.optLong("at", now), now);
                String member = config.memberName;
                scheduleRetryAt(context, id, phase, current.label, member,
                        current.preMinutes, at);
                saveRetry(context, id, phase, current.label, member,
                        current.preMinutes, at);
            } catch (Exception ignored) {
                preferences.edit().remove(entry.getKey()).commit();
            }
        }
    }

    static long nextTrigger(RemoteStore.Schedule schedule, String phase, long after) {
        long best = Long.MAX_VALUE;
        for (int offset = 0; offset <= 8; offset++) {
            Calendar candidate = Calendar.getInstance();
            candidate.setTimeInMillis(after);
            candidate.add(Calendar.DAY_OF_YEAR, offset);
            candidate.set(Calendar.HOUR_OF_DAY, schedule.hour);
            candidate.set(Calendar.MINUTE, schedule.minute);
            candidate.set(Calendar.SECOND, 0);
            candidate.set(Calendar.MILLISECOND, 0);
            int mondayBased = (candidate.get(Calendar.DAY_OF_WEEK) + 5) % 7;
            if ((schedule.days & (1 << mondayBased)) == 0) continue;
            if (PHASE_MEAL.equals(phase)) candidate.add(Calendar.MINUTE, -schedule.preMinutes);
            if (candidate.getTimeInMillis() >= after && candidate.getTimeInMillis() < best) {
                best = candidate.getTimeInMillis();
            }
        }
        return best;
    }

    private static void schedulePhase(Context context, RemoteStore.Schedule schedule,
            String phase, long after) {
        long at = nextTrigger(schedule, phase, after);
        if (at == Long.MAX_VALUE) return;
        set(context, at, alarmIntent(context, schedule.id, phase, true));
    }

    private static PendingIntent alarmIntent(Context context, String id, String phase, boolean recurring) {
        Intent intent = receiverIntent(context, id, phase, recurring)
                .setData(Uri.parse("remindercall://alarm/" + Uri.encode(id) + "/" + phase));
        return PendingIntent.getBroadcast(context, alarmCode(id, phase), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static Intent receiverIntent(Context context, String id, String phase, boolean recurring) {
        return new Intent(context, AlarmReceiver.class).putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_PHASE, phase).putExtra(EXTRA_RECURRING, recurring);
    }

    private static void set(Context context, long at, PendingIntent operation) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
            // A medicine call is a deliberately scheduled, user-visible event.
            // Alarm-clock alarms are Android's most reliable exact alarms: they
            // wake the device from Doze and are not shifted to a maintenance window.
            alarms.setAlarmClock(new AlarmManager.AlarmClockInfo(at,
                    scheduleScreenIntent(context)), operation);
        } else {
            // Keep a best-effort alarm while the Android 12 exact-alarm access
            // screen is waiting for the user. The home screen clearly exposes
            // the missing access and reschedules exactly as soon as it is granted.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation);
        }
    }

    private static PendingIntent scheduleScreenIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 9100, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static int retryCode(String id, String phase) {
        return positiveHash("retry:" + id + ":" + phase);
    }

    static int alarmCode(String id, String phase) {
        return positiveHash(id + ":" + phase);
    }

    private static void saveRetry(Context context, String id, String phase, String label,
            String member, int preMinutes, long at) {
        try {
            String value = new JSONObject().put("id", id).put("phase", phase)
                    .put("label", safe(label, "Medicine"))
                    .put("member", safe(member, "Family member"))
                    .put("preMinutes", Math.max(1, preMinutes)).put("at", at)
                    .put("policyVersion", RETRY_POLICY_VERSION).toString();
            context.getSharedPreferences(RETRIES, Context.MODE_PRIVATE).edit()
                    .putString(retryKey(id, phase), value).commit();
        } catch (Exception ignored) {}
    }

    private static String retryKey(String id, String phase) {
        return Uri.encode(id) + ":" + phase;
    }

    static long restoredRetryAt(int policyVersion, long savedAt, long now) {
        if (policyVersion < RETRY_POLICY_VERSION) {
            return now + RETRY_DELAY_MS;
        }
        return Math.max(savedAt, now + 1_000L);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static int positiveHash(String value) { return value.hashCode() & 0x7fffffff; }
}
