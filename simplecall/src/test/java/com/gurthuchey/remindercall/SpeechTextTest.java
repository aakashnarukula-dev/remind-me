package com.gurthuchey.remindercall;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SpeechTextTest {
    @Test public void naturalVoiceKeysExactlyMatchBackendTexts() {
        assertEquals("హలో Aakash! B Complex టాబ్లెట్ వేసుకున్నారా?",
                SpeechText.medicineQuestion("Aakash", "B Complex"));
        assertEquals("హలో నాన్నా! మందు టాబ్లెట్ వేసుకున్నారా?",
                SpeechText.medicineQuestion("నాన్నా", "టాబ్లెట్"));
        assertEquals("హలో నాన్నా! మందు టాబ్లెట్ వేసుకున్నారా?",
                SpeechText.medicineQuestion("నాన్నా", "టాబ్లెట్ టాబ్లెట్"));
        assertEquals("హలో నాన్నా! మందు టాబ్లెట్ వేసుకున్నారా?",
                SpeechText.medicineQuestion("నాన్నా", "tablet tablet"));
        assertEquals("హలో నాన్నా! మందు టాబ్లెట్ వేసుకున్నారా?",
                SpeechText.medicineQuestion("నాన్నా", "టాబ్లెట్\u200Cటాబ్లెట్"));
        assertEquals("సూపర్ Aakash! ఉంటాను, Bye!", SpeechText.medicineTaken("Aakash"));
        assertEquals("అయితే త్వరగా వెళ్లి టాబ్లెట్ వేసుకోండి. నేను లైన్‌లోనే ఉంటాను. "
                        + "వేసుకుని వచ్చాక, “వేసుకున్నా” బటన్ నొక్కండి. లేదా ఇంకా సమయం కావాలంటే, "
                        + "“తర్వాత గుర్తుచేయి” బటన్ నొక్కండి.",
                SpeechText.medicineNotTaken());
        assertEquals(SpeechText.medicineNotTaken(),
                VoiceClipCache.BUNDLED_MEDICINE_PROMPT_TEXT);
        assertEquals("ఎన్ని నిమిషాల తర్వాత మళ్లీ గుర్తు చేయాలి?",
                SpeechText.reminderDelayQuestion());
        assertEquals("సరే. ఐదు నిమిషాల తర్వాత మళ్లీ కాల్ చేసి గుర్తు చేస్తాను. Bye!",
                SpeechText.reminderDelayed(5));
        assertEquals(SpeechText.reminderDelayed(5),
                VoiceClipCache.BUNDLED_FIVE_MINUTE_RESPONSE_TEXT);
        assertEquals("హలో Aakash! భోజనం చేశారా? ఇంకా చేయకపోతే ఇప్పుడే చేయండి. "
                        + "ఇంకో ముప్పై నిమిషాల్లో మీరు B Complex టాబ్లెట్ వేసుకోవాలి. "
                        + "నేను మరో ముప్పై నిమిషాల్లో మీకు కాల్ చేసి గుర్తు చేస్తాను. Bye",
                SpeechText.mealQuestion("Aakash", "B Complex", false, 30));
    }

    @Test public void customReminderReplacesOnlySupportedPlaceholders() {
        assertEquals("హలో అమ్మా! డాక్టర్ అపాయింట్మెంట్ గుర్తుందా? [Unknown]",
                SpeechText.custom("హలో [Name]! [Reminder] గుర్తుందా? [Unknown]",
                        "అమ్మా", "డాక్టర్ అపాయింట్మెంట్"));
        assertEquals("", SpeechText.custom(null, "అమ్మా", "పని"));
    }

    @Test public void customReminderTitlePlaceholderTracksCurrentTitle() {
        assertEquals("Hello Aakash! Time for Collagen drink.",
                SpeechText.custom("Hello [Name]! Time for [Reminder title].",
                        "Aakash", "Collagen drink", "en"));
    }

    @Test public void customReminderCollapsesUnicodeWhitespaceLikeCloudVoice() {
        assertEquals("హలో అమ్మా! డాక్టర్ అపాయింట్మెంట్ గుర్తుందా?",
                SpeechText.custom("హలో\u00a0[Name]!\u2007[Reminder]\ufeffగుర్తుందా?",
                        "అమ్మా", "డాక్టర్\u202fఅపాయింట్మెంట్"));
    }

    @Test public void supportsAllSixReminderLanguagesWithoutChangingAuthoredText() {
        assertEquals("Hello Aakash! Have you taken your B Complex tablet?",
                SpeechText.medicineQuestion("Aakash", "B Complex", "en"));
        assertEquals("नमस्ते पापा! क्या आपने बीपी की गोली ले ली?",
                SpeechText.medicineQuestion("पापा", "बीपी", "hi"));
        assertEquals("வணக்கம் அம்மா! வைட்டமின் மாத்திரை எடுத்துக்கொண்டீர்களா?",
                SpeechText.medicineQuestion("அம்மா", "வைட்டமின்", "ta"));
        assertEquals("ನಮಸ್ಕಾರ ಅಪ್ಪ! ಬಿಪಿ ಮಾತ್ರೆ ತೆಗೆದುಕೊಂಡಿದ್ದೀರಾ?",
                SpeechText.medicineQuestion("ಅಪ್ಪ", "ಬಿಪಿ", "kn"));
        assertEquals("നമസ്കാരം അമ്മേ! വിറ്റാമിൻ ഗുളിക കഴിച്ചോ?",
                SpeechText.medicineQuestion("അമ്മേ", "വിറ്റാമിൻ", "ml"));
        assertEquals("Hello Aakash! తెలుగు reminder",
                SpeechText.custom("Hello [Name]! తెలుగు [Reminder]", "Aakash", "reminder", "en"));
    }

    @Test public void confirmationCallUsesCategoryAppropriateEnglish() {
        assertEquals("Hi Aakash! Have you taken Nervz-D + Pumpkin Seed Oil?",
                SpeechText.confirmationQuestion("Aakash", "Nervz-D + Pumpkin Seed Oil",
                        "supplement", "en"));
        assertEquals("Hi Aakash! Did you have Protein Oats Bowl?",
                SpeechText.confirmationQuestion("Aakash", "Protein Oats Bowl", "meal", "en"));
        assertEquals("Hi Aakash! Did you complete Workout?",
                SpeechText.confirmationQuestion("Aakash", "Workout", "exercise", "en"));
        assertEquals("Okay. Please do it now. I’ll call again in 5 minutes. Bye!",
                SpeechText.confirmationNotTaken("en"));
    }
}
