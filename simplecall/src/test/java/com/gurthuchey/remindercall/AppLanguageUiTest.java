package com.gurthuchey.remindercall;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class AppLanguageUiTest {
    @Test public void skipTodaySupportsEveryAppLanguage() {
        assertEquals("Skip for today", AppLanguage.ui("en", "Skip for today"));
        assertEquals("ఈ రోజుకు వద్దు", AppLanguage.ui("te", "Skip for today"));
        assertEquals("आज के लिए छोड़ें", AppLanguage.ui("hi", "Skip for today"));
        assertEquals("இன்றைக்கு வேண்டாம்", AppLanguage.ui("ta", "Skip for today"));
        assertEquals("ಇಂದಿಗೆ ಬೇಡ", AppLanguage.ui("kn", "Skip for today"));
        assertEquals("ഇന്നേക്ക് വേണ്ട", AppLanguage.ui("ml", "Skip for today"));
    }
}
