package com.gurthuchey.admin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ModelsRemoteTest {
    @Test public void onePersonKeepsEveryMedicineTime() {
        Map<String, Object> member = new HashMap<>();
        member.put("name", "Nana");
        List<Map<String, Object>> schedules = new ArrayList<>();
        schedules.add(schedule("Morning BP", 8, 0, true));
        schedules.add(schedule("Afternoon sugar", 13, 30, true));
        schedules.add(schedule("Night cholesterol", 21, 15, false));
        member.put("schedules", schedules);

        Models.Member parsed = Models.Member.fromRemote("nana", member);

        assertEquals("Nana", parsed.name);
        assertEquals("generic", parsed.relation);
        assertEquals(3, parsed.schedules.size());
        assertEquals("1:30 PM", parsed.schedules.get(1).timeText());
        assertFalse(parsed.schedules.get(2).enabled);
    }

    @Test public void malformedRemoteTimesAreClampedSafely() {
        Map<String, Object> item = schedule("Medicine", 99, -8, true);
        item.put("preMinutes", 500L);
        item.put("confirmationMinutes", 500L);
        item.put("days", 255L);

        Models.Schedule parsed = Models.Schedule.fromRemote(item);

        assertEquals(23, parsed.hour);
        assertEquals(0, parsed.minute);
        assertEquals(180, parsed.preMinutes);
        assertEquals(180, parsed.confirmationMinutes);
        assertEquals(127, parsed.days);
        assertTrue(parsed.enabled);
    }

    @Test public void legacyReminderDefaultsConfirmationOff() {
        Models.Schedule parsed = Models.Schedule.fromRemote(
                schedule("Legacy medicine", 8, 0, true));
        assertEquals(0, parsed.confirmationMinutes);
    }

    @Test public void oversizedRemoteTextAndSchedulesAreBounded() {
        Map<String, Object> member = new HashMap<>();
        member.put("name", "  " + "N".repeat(90) + "\n");
        List<Map<String, Object>> schedules = new ArrayList<>();
        for (int i = 0; i < 60; i++) schedules.add(schedule("M".repeat(100), 8, 0, true));
        member.put("schedules", schedules);

        Models.Member parsed = Models.Member.fromRemote("x".repeat(100), member);

        assertEquals(80, parsed.id.length());
        assertEquals(60, parsed.name.length());
        assertEquals(50, parsed.schedules.size());
        assertEquals(80, parsed.schedules.get(0).label.length());
    }

    @Test public void categoryConversationRoundTripsFromFirebaseData() {
        Map<String, Object> item = schedule("Doctor visit", 17, 45, true);
        item.put("category", "appointment");
        Map<String, Object> answer = new HashMap<>();
        answer.put("label", "సరే");
        answer.put("response", "సరే. Bye!");
        Map<String, Object> question = new HashMap<>();
        question.put("prompt", "హలో [Name]! [Reminder] గుర్తుందా?");
        question.put("answers", List.of(answer));
        item.put("questions", List.of(question));

        Models.Schedule parsed = Models.Schedule.fromRemote(item);

        assertEquals("appointment", parsed.category);
        assertTrue(parsed.custom());
        assertEquals(1, parsed.questions.size());
        assertEquals("సరే", parsed.questions.get(0).answers.get(0).label);
        assertEquals("సరే. Bye!", parsed.questions.get(0).answers.get(0).response);
    }

    @Test public void nonMedicineCategoryNeverFallsBackToMedicineFlow() {
        Map<String, Object> item = schedule("Drink water", 10, 0, true);
        item.put("category", "task");

        Models.Schedule parsed = Models.Schedule.fromRemote(item);

        assertTrue(parsed.custom());
        assertTrue(parsed.questions.isEmpty());
    }

    @Test public void informationOnlyQuestionWithoutButtonsRoundTrips() {
        Map<String, Object> item = schedule("Workout", 18, 0, true);
        item.put("category", "exercise");
        item.put("questions", List.of(Map.of(
                "prompt", "Hi [Name]! Time for [Reminder title].",
                "answers", List.of())));

        Models.Schedule parsed = Models.Schedule.fromRemote(item);

        assertEquals(1, parsed.questions.size());
        assertTrue(parsed.questions.get(0).answers.isEmpty());
    }

    @Test public void unknownExplicitCategoryIsCustomButMissingLegacyCategoryIsMedicine() {
        Map<String, Object> future = schedule("Birthday", 9, 0, true);
        future.put("category", "birthday");

        assertEquals("custom", Models.Schedule.fromRemote(future).category);
        assertEquals("medicine", Models.Schedule.fromRemote(
                schedule("Legacy tablet", 9, 0, true)).category);
    }

    private static Map<String, Object> schedule(String label, int hour, int minute, boolean enabled) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", label.replace(' ', '-'));
        item.put("label", label);
        item.put("hour", (long) hour);
        item.put("minute", (long) minute);
        item.put("days", 127L);
        item.put("preMinutes", 30L);
        item.put("medicineKey", "generic");
        item.put("enabled", enabled);
        return item;
    }
}
