package com.gurthuchey.remindercall;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONObject;

import java.util.Calendar;

/** Local, date-scoped call progress used by the reminder cards. */
final class DailyCallStatus {
    static final String ACTION_CHANGED = "com.gurthuchey.remindercall.DAILY_STATUS_CHANGED";

    static final String UPCOMING = "upcoming";
    static final String CALLING = "calling";
    static final String RETRY = "retry";
    static final String COMPLETED = "completed";
    static final String MISSED = "missed";
    static final String NOT_TODAY = "not_today";
    static final String UNKNOWN = "unknown";

    private static final String PREFS = "daily_call_status";
    private static final String TRACKING_STARTED_AT = "tracking_started_at";
    private static final String STATE_RINGING = "ringing";
    private static final String STATE_RETRY = "retry";
    private static final String STATE_COMPLETED = "completed";
    private static final long DUE_GRACE_MS = 90_000L;
    private static final long STALE_RING_MS = 2L * 60_000L;

    static final class Display {
        final String kind;
        final long nextAt;

        Display(String kind, long nextAt) {
            this.kind = kind;
            this.nextAt = nextAt;
        }
    }

    private static final class Entry {
        final String state;
        final long nextAt;
        final long updatedAt;

        Entry(String state, long nextAt, long updatedAt) {
            this.state = state;
            this.nextAt = nextAt;
            this.updatedAt = updatedAt;
        }
    }

    private final Context context;
    private final SharedPreferences preferences;
    private final long trackingStartedAt;

    DailyCallStatus(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long savedStart = preferences.getLong(TRACKING_STARTED_AT, 0L);
        if (savedStart <= 0L) {
            savedStart = System.currentTimeMillis();
            preferences.edit().putLong(TRACKING_STARTED_AT, savedStart).commit();
        }
        trackingStartedAt = savedStart;
    }

    void markCalling(String scheduleId, String phase) {
        write(scheduleId, phase, STATE_RINGING, 0L, System.currentTimeMillis());
    }

    void markRetry(String scheduleId, String phase, long nextAt) {
        write(scheduleId, phase, STATE_RETRY, nextAt, System.currentTimeMillis());
    }

    void markCompleted(String scheduleId, String phase) {
        write(scheduleId, phase, STATE_COMPLETED, 0L, System.currentTimeMillis());
    }

    Display display(RemoteStore.Schedule schedule, long now) {
        Entry meal = schedule.preMinutes > 0
                ? read(schedule.id, ReminderScheduler.PHASE_MEAL, now) : null;
        Entry primary = read(schedule.id, ReminderScheduler.PHASE_MEDICINE, now);
        Entry confirmation = schedule.confirmationMinutes > 0
                ? read(schedule.id, ReminderScheduler.PHASE_CONFIRMATION, now) : null;

        boolean calling = isCalling(meal, now) || isCalling(primary, now)
                || isCalling(confirmation, now);
        long retryAt = earliestRetry(meal, primary, confirmation);
        boolean primaryDone = isCompleted(primary);
        boolean confirmationDone = isCompleted(confirmation);
        boolean scheduledToday = isScheduledToday(schedule.days, now);
        long scheduledAt = scheduledAt(schedule.hour, schedule.minute, now);
        String kind = resolve(scheduledToday, scheduledAt, calling, retryAt > 0L,
                primaryDone, schedule.confirmationMinutes > 0, confirmationDone, now);
        kind = applyTrackingBaseline(kind, scheduledAt, trackingStartedAt);
        return new Display(kind, retryAt);
    }

    void register(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.registerOnSharedPreferenceChangeListener(listener);
    }

    void unregister(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
    }

    static String resolve(boolean scheduledToday, long scheduledAt, boolean calling,
            boolean retry, boolean primaryDone, boolean confirmationRequired,
            boolean confirmationDone, long now) {
        if (calling) return CALLING;
        if (retry) return RETRY;
        if (primaryDone && (!confirmationRequired || confirmationDone)) return COMPLETED;
        if (primaryDone && confirmationRequired) return RETRY;
        if (!scheduledToday) return NOT_TODAY;
        return now <= scheduledAt + DUE_GRACE_MS ? UPCOMING : MISSED;
    }

    static boolean isIncomplete(String kind) {
        return CALLING.equals(kind) || RETRY.equals(kind) || MISSED.equals(kind);
    }

    static String applyTrackingBaseline(String kind, long scheduledAt, long trackingStartedAt) {
        if (MISSED.equals(kind) && scheduledAt < trackingStartedAt) return UNKNOWN;
        return kind;
    }

    static String incompleteLabel(String language) {
        int index = "te".equals(language) ? 1 : "hi".equals(language) ? 2
                : "ta".equals(language) ? 3 : "kn".equals(language) ? 4
                : "ml".equals(language) ? 5 : 0;
        String[] values = {"Not completed", "పూర్తి కాలేదు", "पूरा नहीं हुआ",
                "முடிக்கவில்லை", "ಪೂರ್ಣಗೊಂಡಿಲ್ಲ", "പൂർത്തിയായില്ല"};
        return values[index];
    }

    static String completedLabel(String language) {
        int index = "te".equals(language) ? 1 : "hi".equals(language) ? 2
                : "ta".equals(language) ? 3 : "kn".equals(language) ? 4
                : "ml".equals(language) ? 5 : 0;
        String[] values = {"Completed", "పూర్తయింది", "पूरा हुआ",
                "முடிந்தது", "ಪೂರ್ಣಗೊಂಡಿದೆ", "പൂർത്തിയായി"};
        return values[index];
    }

    private void write(String scheduleId, String phase, String state, long nextAt, long now) {
        if (scheduleId == null || scheduleId.trim().isEmpty()
                || "test-call".equals(scheduleId)) return;
        try {
            String value = new JSONObject()
                    .put("day", dayKey(now))
                    .put("state", state)
                    .put("nextAt", Math.max(0L, nextAt))
                    .put("updatedAt", now)
                    .toString();
            preferences.edit().putString(key(scheduleId, phase), value).apply();
            context.sendBroadcast(new Intent(ACTION_CHANGED).setPackage(context.getPackageName()));
        } catch (Exception ignored) {}
    }

    private Entry read(String scheduleId, String phase, long now) {
        String raw = preferences.getString(key(scheduleId, phase), null);
        if (raw == null) return null;
        try {
            JSONObject value = new JSONObject(raw);
            if (value.optInt("day", -1) != dayKey(now)) return null;
            return new Entry(value.optString("state", ""), value.optLong("nextAt", 0L),
                    value.optLong("updatedAt", 0L));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isCalling(Entry entry, long now) {
        return entry != null && STATE_RINGING.equals(entry.state)
                && now - entry.updatedAt <= STALE_RING_MS;
    }

    private static boolean isCompleted(Entry entry) {
        return entry != null && STATE_COMPLETED.equals(entry.state);
    }

    private static long earliestRetry(Entry... entries) {
        long earliest = Long.MAX_VALUE;
        for (Entry entry : entries) {
            if (entry != null && STATE_RETRY.equals(entry.state) && entry.nextAt > 0L) {
                earliest = Math.min(earliest, entry.nextAt);
            }
        }
        return earliest == Long.MAX_VALUE ? 0L : earliest;
    }

    private static boolean isScheduledToday(int days, long now) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        int mondayBased = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        return (days & (1 << mondayBased)) != 0;
    }

    private static long scheduledAt(int hour, int minute, long now) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        calendar.set(Calendar.HOUR_OF_DAY, Math.max(0, Math.min(23, hour)));
        calendar.set(Calendar.MINUTE, Math.max(0, Math.min(59, minute)));
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static int dayKey(long value) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(value);
        return calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR);
    }

    private static String key(String scheduleId, String phase) {
        return "status:" + Uri.encode(scheduleId) + ":" + Uri.encode(phase == null ? "" : phase);
    }
}
