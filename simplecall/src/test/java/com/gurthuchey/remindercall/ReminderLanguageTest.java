package com.gurthuchey.remindercall;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ReminderLanguageTest {
    @Test public void detectsEverySupportedCallLanguageFromReminderText() {
        assertEquals("en", AppLanguage.detect("Take vitamin tablet. Okay. Bye.", "te"));
        assertEquals("te", AppLanguage.detect("మందు వేసుకున్నారా? సరే.", "en"));
        assertEquals("hi", AppLanguage.detect("क्या आपने दवा ली? ठीक है।", "en"));
        assertEquals("ta", AppLanguage.detect("மருந்து எடுத்தீர்களா? சரி.", "en"));
        assertEquals("kn", AppLanguage.detect("ಔಷಧಿ ತೆಗೆದುಕೊಂಡಿರಾ? ಸರಿ.", "en"));
        assertEquals("ml", AppLanguage.detect("മരുന്ന് കഴിച്ചോ? ശരി.", "en"));
    }

    @Test public void scheduleLanguageComesFromNameQuestionAndAnswersNotSavedUiLanguage() {
        RemoteStore.Schedule schedule = new RemoteStore.Schedule();
        schedule.label = "డాక్టర్ అపాయింట్మెంట్";
        schedule.language = "hi";

        RemoteStore.ScriptQuestion question = new RemoteStore.ScriptQuestion();
        question.prompt = "హలో [Name]! [Reminder] గురించి గుర్తు చేస్తున్నాను.";
        RemoteStore.ScriptAnswer answer = new RemoteStore.ScriptAnswer();
        answer.label = "సరే";
        answer.response = "సరే. బై!";
        question.answers.add(answer);
        schedule.questions.add(question);

        assertEquals("te", schedule.detectedLanguage());
    }

    @Test public void medicineLanguageComesFromMedicineName() {
        RemoteStore.Schedule schedule = new RemoteStore.Schedule();
        schedule.label = "Vitamin B Complex";
        schedule.language = "te";
        assertEquals("en", schedule.detectedLanguage());

        schedule.label = "విటమిన్ బి కాంప్లెక్స్";
        assertEquals("te", schedule.detectedLanguage());
    }
}
