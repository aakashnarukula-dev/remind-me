package com.gurthuchey.remindercall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public final class SkipRangeTest {
    private static long at(int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2026, month, day, hour, minute, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static RemoteStore.Schedule daily(int hour, int minute, int preMinutes) {
        RemoteStore.Schedule schedule = new RemoteStore.Schedule();
        schedule.id = "bp";
        schedule.enabled = true;
        schedule.hour = hour;
        schedule.minute = minute;
        schedule.days = 0b1111111;
        schedule.preMinutes = preMinutes;
        return schedule;
    }

    @Test public void parsesOnlyValidOrderedRanges() {
        assertNotNull(SkipRange.of("2026-10-14", "2026-10-18"));
        assertNotNull(SkipRange.of("2026-10-14", "2026-10-14"));
        assertNull(SkipRange.of(null, null));
        assertNull(SkipRange.of("", ""));
        assertNull(SkipRange.of("2026-10-18", "2026-10-14"));
        assertNull(SkipRange.of("14-10-2026", "2026-10-18"));
        assertNull(SkipRange.of("2026-02-30", "2026-03-02"));
        assertNull(SkipRange.of("2026-10-14", null));
    }

    @Test public void rangeLongerThanSixtyDaysIsRejected() {
        assertNotNull(SkipRange.of("2026-10-01", "2026-11-29"));
        assertNull(SkipRange.of("2026-10-01", "2026-11-30"));
    }

    @Test public void coversEveryMomentOfEachDayInclusive() {
        SkipRange range = SkipRange.of("2026-10-14", "2026-10-16");
        assertFalse(range.covers(at(Calendar.OCTOBER, 13, 23, 59)));
        assertTrue(range.covers(at(Calendar.OCTOBER, 14, 0, 0)));
        assertTrue(range.covers(at(Calendar.OCTOBER, 16, 23, 59)));
        assertFalse(range.covers(at(Calendar.OCTOBER, 17, 0, 0)));
    }

    @Test public void resumesAtMidnightAfterTheLastSkippedDay() {
        SkipRange range = SkipRange.of("2026-10-14", "2026-10-16");
        assertEquals(at(Calendar.OCTOBER, 17, 0, 0), range.resumeAt());
    }

    @Test public void endedOnlyAfterTheLastDayIsOver() {
        SkipRange range = SkipRange.of("2026-10-14", "2026-10-16");
        assertFalse(range.isOver(at(Calendar.OCTOBER, 12, 9, 0)));
        assertFalse(range.isOver(at(Calendar.OCTOBER, 16, 23, 0)));
        assertTrue(range.isOver(at(Calendar.OCTOBER, 17, 0, 0)));
    }

    @Test public void dayKeyUsesLocalCalendarDate() {
        assertEquals("2026-10-05", SkipRange.dayKey(at(Calendar.OCTOBER, 5, 0, 30)));
        assertEquals("2026-10-12", SkipRange.dayKey(at(Calendar.OCTOBER, 12, 23, 59)));
    }

    @Test public void dayKeyAfterAddsWholeDays() {
        assertEquals("2026-10-12", SkipRange.dayKeyAfter(at(Calendar.OCTOBER, 12, 9, 0), 0));
        assertEquals("2026-11-01", SkipRange.dayKeyAfter(at(Calendar.OCTOBER, 12, 9, 0), 20));
    }

    @Test public void skipTodayMovesTheNextCallToTomorrow() {
        SkipRange today = SkipRange.of("2026-10-12", "2026-10-12");
        long trigger = ReminderScheduler.nextTrigger(daily(20, 0, 0),
                ReminderScheduler.PHASE_MEDICINE, at(Calendar.OCTOBER, 12, 15, 0), today);
        assertEquals(at(Calendar.OCTOBER, 13, 20, 0), trigger);
    }

    @Test public void futureRangeSkipsOnlyThoseDays() {
        SkipRange range = SkipRange.of("2026-10-14", "2026-10-16");
        RemoteStore.Schedule schedule = daily(9, 0, 0);
        assertEquals(at(Calendar.OCTOBER, 13, 9, 0), ReminderScheduler.nextTrigger(schedule,
                ReminderScheduler.PHASE_MEDICINE, at(Calendar.OCTOBER, 12, 15, 0), range));
        assertEquals(at(Calendar.OCTOBER, 17, 9, 0), ReminderScheduler.nextTrigger(schedule,
                ReminderScheduler.PHASE_MEDICINE, at(Calendar.OCTOBER, 13, 9, 1), range));
    }

    @Test public void rangeLongerThanAWeekStillFindsTheResumeDay() {
        SkipRange range = SkipRange.of("2026-10-12", "2026-11-05");
        assertEquals(at(Calendar.NOVEMBER, 6, 9, 0), ReminderScheduler.nextTrigger(
                daily(9, 0, 0), ReminderScheduler.PHASE_MEDICINE,
                at(Calendar.OCTOBER, 12, 8, 0), range));
    }

    @Test public void mealCallBelongsToItsMedicineDay() {
        SkipRange range = SkipRange.of("2026-10-13", "2026-10-13");
        // Medicine at 00:15 on the 14th: its 30-minute meal call rings at 23:45 on the 13th,
        // a skipped day, yet it belongs to the 14th, so it must still ring.
        assertEquals(at(Calendar.OCTOBER, 13, 23, 45), ReminderScheduler.nextTrigger(
                daily(0, 15, 30), ReminderScheduler.PHASE_MEAL,
                at(Calendar.OCTOBER, 13, 12, 0), range));
    }

    @Test public void noRangeKeepsTheNormalSchedule() {
        assertEquals(at(Calendar.OCTOBER, 12, 20, 0), ReminderScheduler.nextTrigger(
                daily(20, 0, 0), ReminderScheduler.PHASE_MEDICINE,
                at(Calendar.OCTOBER, 12, 15, 0), null));
    }

    @Test public void globalSkipKeepsNotTodayAndCompleted() {
        assertEquals(DailyCallStatus.SKIPPED,
                DailyCallStatus.applyGlobalSkip(DailyCallStatus.UPCOMING, true));
        assertEquals(DailyCallStatus.SKIPPED,
                DailyCallStatus.applyGlobalSkip(DailyCallStatus.RETRY, true));
        assertEquals(DailyCallStatus.NOT_TODAY,
                DailyCallStatus.applyGlobalSkip(DailyCallStatus.NOT_TODAY, true));
        assertEquals(DailyCallStatus.COMPLETED,
                DailyCallStatus.applyGlobalSkip(DailyCallStatus.COMPLETED, true));
        assertEquals(DailyCallStatus.UPCOMING,
                DailyCallStatus.applyGlobalSkip(DailyCallStatus.UPCOMING, false));
    }

    @Test public void memberDocumentCarriesTheSkipRange() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "Amma");
        data.put("schedules", new ArrayList<>());
        data.put("skipFrom", "2026-10-14");
        data.put("skipUntil", "2026-10-18");
        RemoteStore.Config config = RemoteStore.Config.fromRemote("m1", data);
        assertEquals("2026-10-14", config.skip.from);
        assertEquals("2026-10-18", config.skip.until);

        data.remove("skipFrom");
        data.remove("skipUntil");
        assertNull(RemoteStore.Config.fromRemote("m1", data).skip);
    }
}
