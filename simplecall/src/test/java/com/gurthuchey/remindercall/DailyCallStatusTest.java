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
    }
}
