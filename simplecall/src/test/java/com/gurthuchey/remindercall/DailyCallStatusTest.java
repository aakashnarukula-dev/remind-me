package com.gurthuchey.remindercall;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class DailyCallStatusTest {
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
}
