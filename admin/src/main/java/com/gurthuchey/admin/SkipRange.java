package com.gurthuchey.admin;

import java.util.Calendar;
import java.util.Locale;

/**
 * Whole local calendar days, inclusive, on which every reminder of the member is skipped.
 * Stored as ISO dates ("2026-10-14") so Admin, the phone and Firestore rules agree on days
 * without caring about time zones or the time the skip was chosen.
 */
final class SkipRange {
    static final int MAX_DAYS = 60;

    final String from;
    final String until;

    private SkipRange(String from, String until) {
        this.from = from;
        this.until = until;
    }

    /** Returns null for a missing, malformed, reversed or longer-than-allowed range. */
    static SkipRange of(String from, String until) {
        Calendar start = parse(from);
        Calendar end = parse(until);
        if (start == null || end == null || from.compareTo(until) > 0) return null;
        start.add(Calendar.DAY_OF_YEAR, MAX_DAYS - 1);
        if (dayKey(start.getTimeInMillis()).compareTo(until) < 0) return null;
        return new SkipRange(from, until);
    }

    boolean covers(long time) {
        String day = dayKey(time);
        return day.compareTo(from) >= 0 && day.compareTo(until) <= 0;
    }

    /** Local midnight that starts the first day after the range. */
    long resumeAt() {
        Calendar end = parse(until);
        end.add(Calendar.DAY_OF_YEAR, 1);
        return end.getTimeInMillis();
    }

    boolean isOver(long now) {
        return dayKey(now).compareTo(until) > 0;
    }

    static String dayKey(long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        return format(calendar);
    }

    static String dayKeyAfter(long time, int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        calendar.add(Calendar.DAY_OF_YEAR, days);
        return format(calendar);
    }

    private static String format(Calendar calendar) {
        return String.format(Locale.US, "%04d-%02d-%02d", calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH));
    }

    /** Local midnight of an ISO date, or null when the text is not a real calendar date. */
    private static Calendar parse(String value) {
        if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) return null;
        int year = Integer.parseInt(value.substring(0, 4));
        int month = Integer.parseInt(value.substring(5, 7)) - 1;
        int day = Integer.parseInt(value.substring(8, 10));
        Calendar calendar = Calendar.getInstance();
        calendar.setLenient(false);
        calendar.clear();
        calendar.set(year, month, day, 0, 0, 0);
        try {
            calendar.getTimeInMillis();
        } catch (IllegalArgumentException invalid) {
            return null;
        }
        return format(calendar).equals(value) ? calendar : null;
    }
}
