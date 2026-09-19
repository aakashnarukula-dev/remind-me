package com.gurthuchey.remindercall;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AppLanguageTimePeriodTest {
    @Test
    public void eveningStartsAtFourPmAndIncludesSevenPm() {
        assertEquals("Afternoon", AppLanguage.timePeriod("en", 15, 59));
        assertEquals("Evening", AppLanguage.timePeriod("en", 16, 0));
        assertEquals("Evening", AppLanguage.timePeriod("en", 19, 0));
    }

    @Test
    public void nightStartsAtSevenOhOnePm() {
        assertEquals("Night", AppLanguage.timePeriod("en", 19, 1));
        assertEquals("Night", AppLanguage.timePeriod("en", 23, 59));
    }
}
