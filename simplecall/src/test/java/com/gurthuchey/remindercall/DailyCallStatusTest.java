package com.gurthuchey.remindercall;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Calendar;

public final class DailyCallStatusTest {
    @Test public void dailyStatusesRollOverAtLocalMidnight() {
        Calendar now = Calendar.getInstance();
        now.set(2026, Calendar.SEPTEMBER, 22, 23, 59, 30);
        now.set(Calendar.MILLISECOND, 0);

        Calendar next = Calendar.getInstance();
        next.setTimeInMillis(DailyCallStatus.nextLocalDayStart(now.getTimeInMillis()));

        assertEquals(2026, next.get(Calendar.YEAR));
        assertEquals(Calendar.SEPTEMBER, next.get(Calendar.MONTH));
        assertEquals(23, next.get(Calendar.DAY_OF_MONTH));
        assertEquals(0, next.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, next.get(Calendar.MINUTE));
        assertEquals(0, next.get(Calendar.SECOND));
    }

    @Test public void onlyReminderWithCarriedRetryKeepsPreviousDay() {
        int yesterday = 2026265;
        int today = 2026266;
        long now = 1_000L;

        assertEquals(yesterday, DailyCallStatus.selectDisplayDay(today, now,
                new int[]{yesterday, -1, -1}, new long[]{2_000L, 0L, 0L}));
        assertEquals(today, DailyCallStatus.selectDisplayDay(today, now,
                new int[]{yesterday, -1, -1}, new long[]{0L, 0L, 0L}));
    }

    @Test public void expiredCarriedRetryReleasesPreviousDay() {
        assertEquals(2026266, DailyCallStatus.selectDisplayDay(2026266, 3_000L,
                new int[]{2026265}, new long[]{2_000L}));
    }

    @Test public void activeCallWinsOverEveryOtherStatus() {
        assertEquals(DailyCallStatus.CALLING, DailyCallStatus.resolve(
                true, 1_000L, true, true, true, true, true, 2_000L));
    }

    @Test public void retryMeansTheReminderIsNotFinished() {
        assertEquals(DailyCallStatus.RETRY, DailyCallStatus.resolve(
                true, 1_000L, false, true, true, false, false, 2_000L));
    }

    @Test public void confirmationMustFinishBeforeTheCardIsCompleted() {
        assertEquals(DailyCallStatus.RETRY, DailyCallStatus.resolve(
                true, 1_000L, false, false, true, true, false, 2_000L));
        assertEquals(DailyCallStatus.COMPLETED, DailyCallStatus.resolve(
                true, 1_000L, false, false, true, true, true, 2_000L));
    }

    @Test public void completedPrimaryCallIsCompletedWithoutConfirmation() {
        assertEquals(DailyCallStatus.COMPLETED, DailyCallStatus.resolve(
                true, 1_000L, false, false, true, false, false, 2_000L));
    }

    @Test public void untouchedPastReminderIsMissed() {
        assertEquals(DailyCallStatus.MISSED, DailyCallStatus.resolve(
                true, 1_000L, false, false, false, false, false, 100_000L));
    }

    @Test public void reminderNotScheduledTodayHasItsOwnStatus() {
        assertEquals(DailyCallStatus.NOT_TODAY, DailyCallStatus.resolve(
                false, 1_000L, false, false, false, false, false, 100_000L));
    }

    @Test public void onlyUnfinishedDueCallsReceiveThePill() {
        org.junit.Assert.assertTrue(DailyCallStatus.isIncomplete(DailyCallStatus.CALLING));
        org.junit.Assert.assertTrue(DailyCallStatus.isIncomplete(DailyCallStatus.RETRY));
        org.junit.Assert.assertTrue(DailyCallStatus.isIncomplete(DailyCallStatus.MISSED));
        org.junit.Assert.assertFalse(DailyCallStatus.isIncomplete(DailyCallStatus.COMPLETED));
        org.junit.Assert.assertFalse(DailyCallStatus.isIncomplete(DailyCallStatus.UPCOMING));
        org.junit.Assert.assertFalse(DailyCallStatus.isIncomplete(DailyCallStatus.NOT_TODAY));
        org.junit.Assert.assertFalse(DailyCallStatus.isIncomplete(DailyCallStatus.UNKNOWN));
    }

    @Test public void remindersBeforeTrackingStartedDoNotBecomeFalseFailures() {
        assertEquals(DailyCallStatus.UNKNOWN, DailyCallStatus.applyTrackingBaseline(
                DailyCallStatus.MISSED, 1_000L, 2_000L));
        assertEquals(DailyCallStatus.MISSED, DailyCallStatus.applyTrackingBaseline(
                DailyCallStatus.MISSED, 3_000L, 2_000L));
        assertEquals(DailyCallStatus.COMPLETED, DailyCallStatus.applyTrackingBaseline(
                DailyCallStatus.COMPLETED, 1_000L, 2_000L));
    }

    @Test public void manualIncompleteOverridesDerivedCardStatus() {
        assertEquals(DailyCallStatus.MISSED, DailyCallStatus.applyManualIncomplete(
                DailyCallStatus.COMPLETED, true));
        assertEquals(DailyCallStatus.MISSED, DailyCallStatus.applyManualIncomplete(
                DailyCallStatus.UPCOMING, true));
        assertEquals(DailyCallStatus.CALLING, DailyCallStatus.applyManualIncomplete(
                DailyCallStatus.CALLING, true));
        assertEquals(DailyCallStatus.RETRY, DailyCallStatus.applyManualIncomplete(
                DailyCallStatus.RETRY, true));
        assertEquals(DailyCallStatus.COMPLETED, DailyCallStatus.applyManualIncomplete(
                DailyCallStatus.COMPLETED, false));
    }

    @Test public void completedAndUpcomingRemainDistinct() {
        assertEquals("Completed", DailyCallStatus.completedLabel("en"));
        org.junit.Assert.assertFalse(DailyCallStatus.isIncomplete(DailyCallStatus.COMPLETED));
        org.junit.Assert.assertFalse(DailyCallStatus.isIncomplete(DailyCallStatus.UPCOMING));
    }

    @Test public void notTodayTagSupportsEveryAppLanguage() {
        assertEquals("Not today", DailyCallStatus.notTodayLabel("en"));
        assertEquals("ఈ రోజు లేదు", DailyCallStatus.notTodayLabel("te"));
        assertEquals("आज नहीं", DailyCallStatus.notTodayLabel("hi"));
        assertEquals("இன்று இல்லை", DailyCallStatus.notTodayLabel("ta"));
        assertEquals("ಇಂದು ಇಲ್ಲ", DailyCallStatus.notTodayLabel("kn"));
        assertEquals("ഇന്ന് ഇല്ല", DailyCallStatus.notTodayLabel("ml"));
    }

    @Test public void skippedReminderUsesItsOwnCardState() {
        assertEquals(DailyCallStatus.SKIPPED, DailyCallStatus.applySkippedToday(
                DailyCallStatus.UPCOMING, true));
        assertEquals(DailyCallStatus.SKIPPED, DailyCallStatus.applySkippedToday(
                DailyCallStatus.COMPLETED, true));
        assertEquals(DailyCallStatus.COMPLETED, DailyCallStatus.applySkippedToday(
                DailyCallStatus.COMPLETED, false));
        org.junit.Assert.assertFalse(DailyCallStatus.isIncomplete(DailyCallStatus.SKIPPED));
    }

    @Test public void skippedTagSupportsEveryAppLanguage() {
        assertEquals("Skipped", DailyCallStatus.skippedLabel("en"));
        assertEquals("దాటవేశారు", DailyCallStatus.skippedLabel("te"));
        assertEquals("छोड़ा गया", DailyCallStatus.skippedLabel("hi"));
        assertEquals("தவிர்க்கப்பட்டது", DailyCallStatus.skippedLabel("ta"));
        assertEquals("ಬಿಟ್ಟುಬಿಡಲಾಗಿದೆ", DailyCallStatus.skippedLabel("kn"));
        assertEquals("ഒഴിവാക്കി", DailyCallStatus.skippedLabel("ml"));
    }

    @Test public void legacyAfterMidnightSkipIsClearedOnceAfterUpgrade() {
        Calendar updated = Calendar.getInstance();
        updated.set(2026, Calendar.SEPTEMBER, 23, 0, 20, 0);
        updated.set(Calendar.MILLISECOND, 0);
        Calendar installed = (Calendar) updated.clone();
        installed.add(Calendar.MINUTE, 50);
        Calendar now = (Calendar) updated.clone();
        now.add(Calendar.MINUTE, 55);
        int day = updated.get(Calendar.YEAR) * 1000 + updated.get(Calendar.DAY_OF_YEAR);

        org.junit.Assert.assertTrue(DailyCallStatus.shouldClearLegacyAfterMidnightSkip(
                day, true, updated.getTimeInMillis(), now.getTimeInMillis(),
                installed.getTimeInMillis()));
    }

    @Test public void currentAndDaytimeSkipsAreNeverTreatedAsLegacyCarry() {
        Calendar updated = Calendar.getInstance();
        updated.set(2026, Calendar.SEPTEMBER, 23, 10, 20, 0);
        updated.set(Calendar.MILLISECOND, 0);
        Calendar later = (Calendar) updated.clone();
        later.add(Calendar.HOUR, 1);
        int day = updated.get(Calendar.YEAR) * 1000 + updated.get(Calendar.DAY_OF_YEAR);

        org.junit.Assert.assertFalse(DailyCallStatus.shouldClearLegacyAfterMidnightSkip(
                day, true, updated.getTimeInMillis(), later.getTimeInMillis(),
                later.getTimeInMillis()));
        org.junit.Assert.assertFalse(DailyCallStatus.shouldClearLegacyAfterMidnightSkip(
                day, false, updated.getTimeInMillis(), later.getTimeInMillis(),
                later.getTimeInMillis()));
        org.junit.Assert.assertFalse(DailyCallStatus.shouldClearLegacyAfterMidnightSkip(
                day, true, updated.getTimeInMillis(), later.getTimeInMillis(),
                updated.getTimeInMillis()));
    }
}
