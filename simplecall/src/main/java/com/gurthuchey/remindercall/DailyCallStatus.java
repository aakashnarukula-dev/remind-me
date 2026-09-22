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
    static final String SKIPPED = "skipped";
    static final String NOT_TODAY = "not_today";
    static final String UNKNOWN = "unknown";

    private static final String PREFS = "daily_call_status";
    private static final String TRACKING_STARTED_AT = "tracking_started_at";
    private static final String STATE_RINGING = "ringing";
    private static final String STATE_RETRY = "retry";
    private static final String STATE_COMPLETED = "completed";
    private static final String STATE_INCOMPLETE = "incomplete";
    private static final String STATE_SKIPPED = "skipped_today";
    private static final long DUE_GRACE_MS = 90_000L;
    private static final long STALE_RING_MS = 2L * 60_000L;
    private static final long ROLLOVER_GRACE_MS = 2L * 60_000L;

    static final class Display {
        final String kind;
        final long nextAt;

        Display(String kind, long nextAt) {
            this.kind = kind;
            this.nextAt = nextAt;
        }
    }

    private static final class Entry {
        final int day;
        final String state;
        final long nextAt;
        final long updatedAt;

        Entry(int day, String state, long nextAt, long updatedAt) {
            this.day = day;
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
        long now = System.currentTimeMillis();
        write(scheduleId, phase, STATE_RINGING, 0L, now,
                occurrenceDay(scheduleId, phase, now));
    }

    void markRetry(String scheduleId, String phase, long nextAt) {
        long now = System.currentTimeMillis();
        write(scheduleId, phase, STATE_RETRY, nextAt, now,
                occurrenceDay(scheduleId, phase, now));
    }

    void markCompleted(String scheduleId, String phase) {
        long now = System.currentTimeMillis();
        write(scheduleId, phase, STATE_COMPLETED, 0L, now,
                occurrenceDay(scheduleId, phase, now));
    }

    void markIncomplete(String scheduleId) {
        long now = System.currentTimeMillis();
        write(scheduleId, ReminderScheduler.PHASE_MEDICINE, STATE_INCOMPLETE, 0L, now,
                displayDay(now));
    }

    void markSkippedToday(String scheduleId) {
        markSkippedOccurrence(scheduleId, ReminderScheduler.PHASE_MEDICINE);
    }

    void markSkippedOccurrence(String scheduleId, String occurrencePhase) {
        long now = System.currentTimeMillis();
        write(scheduleId, ReminderScheduler.PHASE_MEDICINE, STATE_SKIPPED, 0L, now,
                occurrenceDay(scheduleId, occurrencePhase, now));
    }

    boolean isCarriedOccurrence(String scheduleId, String phase, long now) {
        Entry entry = readRaw(scheduleId, phase);
        return entry != null && entry.day != dayKey(now)
                && (STATE_RETRY.equals(entry.state) || STATE_RINGING.equals(entry.state));
    }

    boolean isSkippedToday(String scheduleId, long now) {
        if (scheduleId == null || scheduleId.trim().isEmpty()) return false;
        return isSkipped(readForDay(scheduleId, ReminderScheduler.PHASE_MEDICINE,
                dayKey(now)));
    }

    void clearSchedule(String scheduleId) {
        if (scheduleId == null || scheduleId.trim().isEmpty()
                || "test-call".equals(scheduleId)) return;
        preferences.edit()
                .remove(key(scheduleId, ReminderScheduler.PHASE_MEDICINE))
                .remove(key(scheduleId, ReminderScheduler.PHASE_MEAL))
                .remove(key(scheduleId, ReminderScheduler.PHASE_CONFIRMATION))
                .commit();
        context.sendBroadcast(new Intent(ACTION_CHANGED).setPackage(context.getPackageName()));
    }

    Display display(RemoteStore.Schedule schedule, long now) {
        int displayDay = displayDay(now);
        long referenceTime = referenceTime(displayDay, now);
        Entry meal = schedule.preMinutes > 0
                ? readForDay(schedule.id, ReminderScheduler.PHASE_MEAL, displayDay) : null;
        Entry primary = readForDay(schedule.id, ReminderScheduler.PHASE_MEDICINE, displayDay);
        Entry confirmation = schedule.confirmationMinutes > 0
                ? readForDay(schedule.id, ReminderScheduler.PHASE_CONFIRMATION, displayDay) : null;

        boolean calling = isCalling(meal, now) || isCalling(primary, now)
                || isCalling(confirmation, now);
        long retryAt = earliestRetry(meal, primary, confirmation);
        boolean primaryDone = isCompleted(primary);
        boolean confirmationDone = isCompleted(confirmation);
        boolean scheduledToday = isScheduledToday(schedule.days, referenceTime);
        long scheduledAt = scheduledAt(schedule.hour, schedule.minute, referenceTime);
        String kind = resolve(scheduledToday, scheduledAt, calling, retryAt > 0L,
                primaryDone, schedule.confirmationMinutes > 0, confirmationDone, now);
        kind = applyTrackingBaseline(kind, scheduledAt, trackingStartedAt);
        kind = applyManualIncomplete(kind, isIncomplete(primary));
        kind = applySkippedToday(kind, isSkipped(primary));
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

    static String applyManualIncomplete(String kind, boolean manuallyIncomplete) {
        if (!manuallyIncomplete || CALLING.equals(kind) || RETRY.equals(kind)) return kind;
        return MISSED;
    }

    static String applySkippedToday(String kind, boolean skippedToday) {
        return skippedToday ? SKIPPED : kind;
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

    static String skippedLabel(String language) {
        int index = "te".equals(language) ? 1 : "hi".equals(language) ? 2
                : "ta".equals(language) ? 3 : "kn".equals(language) ? 4
                : "ml".equals(language) ? 5 : 0;
        String[] values = {"Skipped", "దాటవేశారు", "छोड़ा गया",
                "தவிர்க்கப்பட்டது", "ಬಿಟ್ಟುಬಿಡಲಾಗಿದೆ", "ഒഴിവാക്കി"};
        return values[index];
    }

    static String notTodayLabel(String language) {
        int index = "te".equals(language) ? 1 : "hi".equals(language) ? 2
                : "ta".equals(language) ? 3 : "kn".equals(language) ? 4
                : "ml".equals(language) ? 5 : 0;
        String[] values = {"Not today", "ఈ రోజు లేదు", "आज नहीं",
                "இன்று இல்லை", "ಇಂದು ಇಲ್ಲ", "ഇന്ന് ഇല്ല"};
        return values[index];
    }

    private void write(String scheduleId, String phase, String state, long nextAt, long now,
            int statusDay) {
        if (scheduleId == null || scheduleId.trim().isEmpty()
                || "test-call".equals(scheduleId)) return;
        try {
            String value = new JSONObject()
                    .put("day", statusDay)
                    .put("state", state)
                    .put("nextAt", Math.max(0L, nextAt))
                    .put("updatedAt", now)
                    .toString();
            preferences.edit().putString(key(scheduleId, phase), value).apply();
            context.sendBroadcast(new Intent(ACTION_CHANGED).setPackage(context.getPackageName()));
        } catch (Exception ignored) {}
    }

    private Entry readForDay(String scheduleId, String phase, int statusDay) {
        Entry entry = readRaw(scheduleId, phase);
        return entry != null && entry.day == statusDay ? entry : null;
    }

    private Entry readRaw(String scheduleId, String phase) {
        String raw = preferences.getString(key(scheduleId, phase), null);
        if (raw == null) return null;
        try {
            JSONObject value = new JSONObject(raw);
            return new Entry(value.optInt("day", -1), value.optString("state", ""),
                    value.optLong("nextAt", 0L), value.optLong("updatedAt", 0L));
        } catch (Exception ignored) {
            return null;
        }
    }

    private int occurrenceDay(String scheduleId, String phase, long now) {
        Entry existing = readRaw(scheduleId, phase);
        if (existing != null && (STATE_RETRY.equals(existing.state)
                || STATE_RINGING.equals(existing.state))) {
            return existing.day;
        }
        return dayKey(now);
    }

    private int displayDay(long now) {
        int current = dayKey(now);
        int heldDay = current;
        long latestHold = 0L;
        for (Object raw : preferences.getAll().values()) {
            if (!(raw instanceof String)) continue;
            Entry entry = parse((String) raw);
            if (entry == null || entry.day == current) continue;
            long holdUntil = holdUntil(entry);
            if (holdUntil > now && holdUntil > latestHold) {
                latestHold = holdUntil;
                heldDay = entry.day;
            }
        }
        return heldDay;
    }

    long nextRolloverAt(long now) {
        int current = dayKey(now);
        long midnight = nextLocalDayStart(now);
        long rollover = midnight;
        boolean carriedDay = false;
        for (Object raw : preferences.getAll().values()) {
            if (!(raw instanceof String)) continue;
            Entry entry = parse((String) raw);
            if (entry == null) continue;
            long holdUntil = holdUntil(entry);
            if (holdUntil <= now) continue;
            if (entry.day != current) {
                carriedDay = true;
                rollover = Math.max(rollover == midnight ? 0L : rollover, holdUntil);
            } else if (holdUntil > midnight) {
                rollover = Math.max(rollover, holdUntil);
            }
        }
        return carriedDay ? Math.max(now + 1_000L, rollover) : rollover;
    }

    private static Entry parse(String raw) {
        try {
            JSONObject value = new JSONObject(raw);
            if (!value.has("day") || !value.has("state")) return null;
            return new Entry(value.optInt("day", -1), value.optString("state", ""),
                    value.optLong("nextAt", 0L), value.optLong("updatedAt", 0L));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static long holdUntil(Entry entry) {
        if (entry == null) return 0L;
        if (STATE_RETRY.equals(entry.state) && entry.nextAt > 0L) {
            return entry.nextAt + ROLLOVER_GRACE_MS;
        }
        if (STATE_RINGING.equals(entry.state) && entry.updatedAt > 0L) {
            return entry.updatedAt + STALE_RING_MS;
        }
        return 0L;
    }

    private long referenceTime(int statusDay, long now) {
        if (statusDay == dayKey(now)) return now;
        for (Object raw : preferences.getAll().values()) {
            if (!(raw instanceof String)) continue;
            Entry entry = parse((String) raw);
            if (entry != null && entry.day == statusDay && entry.updatedAt > 0L) {
                return entry.updatedAt;
            }
        }
        return now;
    }

    private static boolean isCalling(Entry entry, long now) {
        return entry != null && STATE_RINGING.equals(entry.state)
                && now - entry.updatedAt <= STALE_RING_MS;
    }

    private static boolean isCompleted(Entry entry) {
        return entry != null && STATE_COMPLETED.equals(entry.state);
    }

    private static boolean isIncomplete(Entry entry) {
        return entry != null && STATE_INCOMPLETE.equals(entry.state);
    }

    private static boolean isSkipped(Entry entry) {
        return entry != null && STATE_SKIPPED.equals(entry.state);
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

    static long nextLocalDayStart(long now) {
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.setTimeInMillis(now);
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        tomorrow.set(Calendar.HOUR_OF_DAY, 0);
        tomorrow.set(Calendar.MINUTE, 0);
        tomorrow.set(Calendar.SECOND, 0);
        tomorrow.set(Calendar.MILLISECOND, 0);
        return tomorrow.getTimeInMillis();
    }

    private static String key(String scheduleId, String phase) {
        return "status:" + Uri.encode(scheduleId) + ":" + Uri.encode(phase == null ? "" : phase);
    }
}
