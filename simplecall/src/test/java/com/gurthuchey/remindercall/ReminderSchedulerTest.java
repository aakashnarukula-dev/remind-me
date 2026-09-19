package com.gurthuchey.remindercall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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
}
