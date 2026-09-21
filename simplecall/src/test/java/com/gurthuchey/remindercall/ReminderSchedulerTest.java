package com.gurthuchey.remindercall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public final class ReminderSchedulerTest {
    @Test public void missedAndIncompleteCallsRetryAfterFiveMinutes() {
        assertEquals(5L * 60_000L, ReminderScheduler.RETRY_DELAY_MS);
    }

    @Test public void rescheduleChoicesContainFiveAndExcludeFortyFiveMinutes() {
        assertArrayEquals(new int[]{5, 15, 30, 60}, CallService.DELAY_MINUTES);
    }

    @Test public void customRetryTimeMustBeLaterOnTheSameDay() {
        Calendar now = Calendar.getInstance();
        now.set(2026, Calendar.SEPTEMBER, 21, 14, 0, 0);
        now.set(Calendar.MILLISECOND, 0);
        Calendar later = (Calendar) now.clone();
        later.set(Calendar.HOUR_OF_DAY, 17);
        later.set(Calendar.MINUTE, 45);
        Calendar earlier = (Calendar) now.clone();
        earlier.set(Calendar.HOUR_OF_DAY, 13);
        Calendar tomorrow = (Calendar) later.clone();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);

        assertTrue(ReminderScheduler.isLaterToday(
                now.getTimeInMillis(), later.getTimeInMillis()));
        assertFalse(ReminderScheduler.isLaterToday(
                now.getTimeInMillis(), earlier.getTimeInMillis()));
        assertFalse(ReminderScheduler.isLaterToday(
                now.getTimeInMillis(), tomorrow.getTimeInMillis()));
    }

    @Test public void legacyOneMinuteRetryMigratesToFiveMinutesFromUpdate() {
        long now = 1_000_000L;
        assertEquals(now + 5L * 60_000L,
                ReminderScheduler.restoredRetryAt(1, now + 60_000L, now));
        assertEquals(now + 15L * 60_000L,
                ReminderScheduler.restoredRetryAt(2, now + 15L * 60_000L, now));
    }

    @Test public void nextMedicineAndMealTimesAreCalculatedLocally() {
        RemoteStore.Schedule schedule = new RemoteStore.Schedule();
        schedule.hour = 14;
        schedule.minute = 30;
        schedule.days = 0b1111111;
        schedule.preMinutes = 30;

        Calendar after = Calendar.getInstance();
        after.set(2026, Calendar.SEPTEMBER, 5, 10, 0, 0);
        after.set(Calendar.MILLISECOND, 0);

        Calendar medicine = Calendar.getInstance();
        medicine.setTimeInMillis(ReminderScheduler.nextTrigger(
                schedule, ReminderScheduler.PHASE_MEDICINE, after.getTimeInMillis()));
        assertEquals(14, medicine.get(Calendar.HOUR_OF_DAY));
        assertEquals(30, medicine.get(Calendar.MINUTE));

        Calendar meal = Calendar.getInstance();
        meal.setTimeInMillis(ReminderScheduler.nextTrigger(
                schedule, ReminderScheduler.PHASE_MEAL, after.getTimeInMillis()));
        assertEquals(14, meal.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, meal.get(Calendar.MINUTE));
    }

    @Test public void activeRetrySurvivesAnUnrelatedScheduleRefresh() {
        RemoteStore.Schedule schedule = new RemoteStore.Schedule();
        schedule.id = "cholesterol";
        schedule.enabled = true;
        schedule.preMinutes = 30;
        RemoteStore.Config replacement = new RemoteStore.Config();
        replacement.schedules.add(schedule);

        assertNotNull(ReminderScheduler.active(replacement, "cholesterol",
                ReminderScheduler.PHASE_MEDICINE));
        assertNotNull(ReminderScheduler.active(replacement, "cholesterol",
                ReminderScheduler.PHASE_MEAL));
    }

    @Test public void retryBecomesObsoleteOnlyWhenItsPhaseIsRemoved() {
        RemoteStore.Schedule schedule = new RemoteStore.Schedule();
        schedule.id = "cholesterol";
        schedule.enabled = true;
        schedule.preMinutes = 0;
        RemoteStore.Config replacement = new RemoteStore.Config();
        replacement.schedules.add(schedule);

        assertNotNull(ReminderScheduler.active(replacement, "cholesterol",
                ReminderScheduler.PHASE_MEDICINE));
        assertNull(ReminderScheduler.active(replacement, "cholesterol",
                ReminderScheduler.PHASE_MEAL));
    }

    @Test public void movingReminderTimeInvalidatesOldOccurrenceProgress() {
        RemoteStore.Schedule before = scheduleAt(17, 0);
        RemoteStore.Schedule after = scheduleAt(17, 15);

        assertTrue(ReminderScheduler.occurrenceChanged(before, after));
    }

    @Test public void changingOnlyReminderTitlePreservesOccurrenceProgress() {
        RemoteStore.Schedule before = scheduleAt(17, 15);
        before.label = "Egg omelette";
        RemoteStore.Schedule after = scheduleAt(17, 15);
        after.label = "Two egg omelette";

        assertFalse(ReminderScheduler.occurrenceChanged(before, after));
    }

    @Test public void removingReminderInvalidatesOldOccurrenceProgress() {
        assertTrue(ReminderScheduler.occurrenceChanged(scheduleAt(17, 15), null));
        assertFalse(ReminderScheduler.occurrenceChanged(null, scheduleAt(17, 15)));
    }

    @Test public void staleRetryRunsOnlyIfScheduleCacheIsUnavailable() {
        assertTrue(AlarmReceiver.shouldDeliver(false, true, true));
        assertTrue(AlarmReceiver.shouldDeliver(true, true, true));
        assertTrue(AlarmReceiver.shouldDeliver(true, false, false));
        assertFalse(AlarmReceiver.shouldDeliver(false, false, false));
        assertFalse(AlarmReceiver.shouldDeliver(true, true, false));
    }

    @Test public void confirmationPhaseExistsOnlyWhenEnabledForThatReminder() {
        RemoteStore.Schedule schedule = new RemoteStore.Schedule();
        schedule.id = "cholesterol";
        schedule.enabled = true;
        RemoteStore.Config config = new RemoteStore.Config();
        config.schedules.add(schedule);

        assertNull(ReminderScheduler.active(config, "cholesterol",
                ReminderScheduler.PHASE_CONFIRMATION));
        schedule.confirmationMinutes = 10;
        assertNotNull(ReminderScheduler.active(config, "cholesterol",
                ReminderScheduler.PHASE_CONFIRMATION));
    }

    @Test public void customReminderNeverCreatesAMealCall() {
        RemoteStore.Schedule schedule = new RemoteStore.Schedule();
        schedule.id = "doctor";
        schedule.category = "appointment";
        schedule.enabled = true;
        schedule.preMinutes = 30;
        RemoteStore.Config replacement = new RemoteStore.Config();
        replacement.schedules.add(schedule);

        assertNotNull(ReminderScheduler.active(replacement, "doctor",
                ReminderScheduler.PHASE_MEDICINE));
        assertNull(ReminderScheduler.active(replacement, "doctor",
                ReminderScheduler.PHASE_MEAL));
    }

    @Test public void allFourteenCurrentRemindersHaveUniqueCorrectDailyAlarms() {
        String[] ids = {
                "health-food-0800", "health-supp-0800", "health-food-1000",
                "health-supp-1000", "health-food-1300", "health-supp-1300",
                "health-food-1600", "health-supp-1600", "health-activity-1800",
                "health-food-1900", "health-supp-1900", "health-food-2030",
                "health-supp-2030", "health-supp-2300"
        };
        int[][] times = {
                {8, 0}, {8, 30}, {10, 0}, {10, 30}, {13, 0}, {13, 30}, {16, 0},
                {16, 30}, {18, 0}, {19, 0}, {19, 30}, {20, 30}, {21, 0}, {23, 0}
        };
        Calendar after = Calendar.getInstance();
        after.set(2026, Calendar.SEPTEMBER, 21, 0, 0, 0);
        after.set(Calendar.MILLISECOND, 0);
        Set<Integer> identities = new HashSet<>();
        long previous = 0L;
        for (int index = 0; index < ids.length; index++) {
            RemoteStore.Schedule schedule = new RemoteStore.Schedule();
            schedule.id = ids[index];
            schedule.hour = times[index][0];
            schedule.minute = times[index][1];
            schedule.days = 0b1111111;
            long trigger = ReminderScheduler.nextTrigger(schedule,
                    ReminderScheduler.PHASE_MEDICINE, after.getTimeInMillis());
            Calendar actual = Calendar.getInstance();
            actual.setTimeInMillis(trigger);
            assertEquals(times[index][0], actual.get(Calendar.HOUR_OF_DAY));
            assertEquals(times[index][1], actual.get(Calendar.MINUTE));
            if (index > 0) org.junit.Assert.assertTrue(trigger > previous);
            previous = trigger;
            org.junit.Assert.assertTrue(identities.add(ReminderScheduler.alarmCode(
                    ids[index], ReminderScheduler.PHASE_MEDICINE)));
        }
        assertEquals(14, identities.size());
    }

    private static RemoteStore.Schedule scheduleAt(int hour, int minute) {
        RemoteStore.Schedule schedule = new RemoteStore.Schedule();
        schedule.id = "egg-omelette";
        schedule.category = "meal";
        schedule.hour = hour;
        schedule.minute = minute;
        schedule.days = 0b1111111;
        schedule.enabled = true;
        return schedule;
    }
}
