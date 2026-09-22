package com.gurthuchey.remindercall;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CallServiceFlowTest {
    @Test public void scriptedQuestionWithoutAuthoredButtonsIsInformationOnly() {
        RemoteStore.Schedule schedule = new RemoteStore.Schedule();
        RemoteStore.ScriptQuestion question = new RemoteStore.ScriptQuestion();
        question.prompt = "Time for breakfast";
        schedule.questions.add(question);

        assertTrue(CallService.informationOnlyQuestion(schedule, 0));
    }

    @Test public void authoredAnswerRequiresUserChoice() {
        RemoteStore.Schedule schedule = new RemoteStore.Schedule();
        RemoteStore.ScriptQuestion question = new RemoteStore.ScriptQuestion();
        question.prompt = "Did you take it?";
        RemoteStore.ScriptAnswer answer = new RemoteStore.ScriptAnswer();
        answer.label = "Okay";
        question.answers.add(answer);
        schedule.questions.add(question);

        assertFalse(CallService.informationOnlyQuestion(schedule, 0));
        assertFalse(CallService.informationOnlyQuestion(schedule, 1));
        assertFalse(CallService.informationOnlyQuestion(null, 0));
    }
}
