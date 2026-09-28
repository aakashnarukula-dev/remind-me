package com.gurthuchey.remindercall;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public final class TimelineProgressTest {
    private final int[] reminders = {360, 420, 780, 1020, 1260};

    @Test public void beforeFirstReminderHasNoFill() {
        assertEquals(-1f, TimelineProgress.position(reminders, 359), 0f);
        assertEquals(-1f, TimelineProgress.position(new int[0], 1020), 0f);
    }

    @Test public void atFivePmFillsThroughFivePmReminder() {
        assertEquals(3f, TimelineProgress.position(reminders, 1020), 0f);
    }

    @Test public void progressesContinuouslyBetweenSections() {
        assertEquals(1.5f, TimelineProgress.position(reminders, 600), .001f);
        assertEquals(2.5f, TimelineProgress.position(reminders, 900), .001f);
    }

    @Test public void afterLastReminderIsFullyFilled() {
        assertEquals(4f, TimelineProgress.position(reminders, 1439), 0f);
    }

    @Test public void duplicateTimesFillTogetherWithoutDivisionByZero() {
        assertEquals(2f, TimelineProgress.position(new int[]{360, 360, 360, 720}, 360), 0f);
        assertEquals(0f, TimelineProgress.position(new int[]{360}, 360), 0f);
    }
}
