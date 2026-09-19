package com.gurthuchey.remindercall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RemoteStoreCategoryTest {
    @Test public void parsesCategoryQuestionAndFourAnswersFromFirebase() {
        Map<String, Object> schedule = new HashMap<>();
        schedule.put("id", "doctor");
        schedule.put("label", "Doctor appointment");
        schedule.put("category", "appointment");
        schedule.put("hour", 17L);
        schedule.put("minute", 30L);
        schedule.put("days", 127L);
        schedule.put("enabled", true);

        Map<String, Object> question = new HashMap<>();
        question.put("prompt", "హలో [Name]! [Reminder] గుర్తుందా?");
        question.put("answers", List.of(
                answer("సరే", "సరే. Bye!"),
                answer("లేదు", "ఇప్పుడే చూసుకోండి."),
                answer("రేపు", "రేపు గుర్తు చేస్తాను."),
                answer("వద్దు", "సరే."),
                answer("ignored", "ignored")));
        schedule.put("questions", List.of(question));

        RemoteStore.Schedule parsed = RemoteStore.Schedule.fromRemote(schedule);

        assertEquals("appointment", parsed.category);
        assertTrue(parsed.custom());
        assertEquals(1, parsed.questions.size());
        assertEquals(4, parsed.questions.get(0).answers.size());
        assertEquals("రేపు గుర్తు చేస్తాను.",
                parsed.questions.get(0).answers.get(2).response);
    }

    @Test public void unknownExplicitCategoryCannotBecomeMedicine() {
        Map<String, Object> schedule = baseSchedule("birthday");
        schedule.put("questions", List.of(question()));

        RemoteStore.Schedule parsed = RemoteStore.Schedule.fromRemote(schedule);

        assertEquals("custom", parsed.category);
        assertTrue(parsed.custom());
    }

    @Test public void missingLegacyCategoryRemainsMedicine() {
        Map<String, Object> schedule = baseSchedule(null);
        assertEquals("medicine", RemoteStore.Schedule.fromRemote(schedule).category);
    }

    @Test public void medicineWithConversationUsesScriptedCall() {
        Map<String, Object> schedule = baseSchedule("medicine");
        schedule.put("questions", List.of(Map.of(
                "prompt", ConversationDefaults.prompt("medicine"),
                "answers", List.of(answer("Okay", "")))));

        RemoteStore.Schedule parsed = RemoteStore.Schedule.fromRemote(schedule);

        assertTrue(parsed.scripted());
        assertEquals("Okay", parsed.questions.get(0).answers.get(0).label);
    }

    @Test public void confirmationSettingDefaultsOffAndParsesWhenEnabled() {
        Map<String, Object> legacy = baseSchedule(null);
        assertEquals(0, RemoteStore.Schedule.fromRemote(legacy).confirmationMinutes);

        Map<String, Object> enabled = baseSchedule(null);
        enabled.put("confirmationMinutes", 10L);
        assertEquals(10, RemoteStore.Schedule.fromRemote(enabled).confirmationMinutes);
    }

    @Test public void malformedCustomConversationIsRejectedBeforeScheduling() {
        Map<String, Object> schedule = baseSchedule("task");
        schedule.put("questions", List.of(Map.of("prompt", "", "answers", List.of())));

        assertThrows(IllegalArgumentException.class,
                () -> RemoteStore.Schedule.fromRemote(schedule));
    }

    @Test public void informationOnlyQuestionWithoutButtonsIsAccepted() {
        Map<String, Object> schedule = baseSchedule("task");
        schedule.put("questions", List.of(Map.of(
                "prompt", "Hi [Name]! Time for [Reminder title].",
                "answers", List.of())));

        RemoteStore.Schedule parsed = RemoteStore.Schedule.fromRemote(schedule);

        assertEquals(1, parsed.questions.size());
        assertTrue(parsed.questions.get(0).answers.isEmpty());
    }

    @Test public void customSpeechWhitespaceMatchesCloudNormalization() {
        Map<String, Object> schedule = baseSchedule("task");
        Map<String, Object> prompt = question();
        prompt.put("prompt", "  హలో [Name]!\n  [Reminder]   గుర్తుందా?  ");
        @SuppressWarnings("unchecked")
        Map<String, Object> answer = (Map<String, Object>)
                ((List<?>) prompt.get("answers")).get(0);
        answer.put("response", " సరే.\n\n Bye! ");
        schedule.put("questions", List.of(prompt));

        RemoteStore.Schedule parsed = RemoteStore.Schedule.fromRemote(schedule);

        assertEquals("హలో [Name]! [Reminder] గుర్తుందా?",
                parsed.questions.get(0).prompt);
        assertEquals("సరే. Bye!", parsed.questions.get(0).answers.get(0).response);
    }

    @Test public void unicodeWhitespaceMatchesCloudNormalization() {
        Map<String, Object> schedule = baseSchedule("task");
        schedule.put("label", "డాక్టర్\u00a0\u2007\u202fఅపాయింట్మెంట్");
        Map<String, Object> prompt = question();
        prompt.put("prompt", "హలో\u00a0[Name]!\u2007[Reminder]\ufeffగుర్తుందా?");
        schedule.put("questions", List.of(prompt));

        RemoteStore.Schedule parsed = RemoteStore.Schedule.fromRemote(schedule);

        assertEquals("డాక్టర్ అపాయింట్మెంట్", parsed.label);
        assertEquals("హలో [Name]! [Reminder] గుర్తుందా?",
                parsed.questions.get(0).prompt);
    }

    @Test public void duplicateReminderIdsAreRejected() {
        Map<String, Object> member = new HashMap<>();
        member.put("name", "Aakash");
        member.put("revision", 4L);
        Map<String, Object> first = baseSchedule(null);
        Map<String, Object> second = baseSchedule(null);
        member.put("schedules", List.of(first, second));

        assertThrows(IllegalArgumentException.class,
                () -> RemoteStore.Config.fromRemote("member", member));
    }

    private static Map<String, Object> baseSchedule(String category) {
        Map<String, Object> schedule = new HashMap<>();
        schedule.put("id", "future");
        schedule.put("label", "Future reminder");
        if (category != null) schedule.put("category", category);
        schedule.put("hour", 9L);
        schedule.put("minute", 0L);
        schedule.put("days", 127L);
        schedule.put("enabled", true);
        return schedule;
    }

    private static Map<String, Object> question() {
        Map<String, Object> question = new HashMap<>();
        question.put("prompt", "హలో [Name]! [Reminder] గుర్తుందా?");
        question.put("answers", List.of(answer("సరే", "సరే. Bye!")));
        return question;
    }

    private static Map<String, Object> answer(String label, String response) {
        Map<String, Object> answer = new HashMap<>();
        answer.put("label", label);
        answer.put("response", response);
        return answer;
    }
}
