package com.gurthuchey.remindercall;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ConversationDefaultsTest {
    @Test public void promptUsesSelectedCategoryAndDynamicTitle() {
        assertEquals("Hi [Name]! It's time for your [Reminder title] medicine.",
                ConversationDefaults.prompt("medicine"));
        assertEquals("Hi [Name]! It's time for your [Reminder title] wake-up reminder.",
                ConversationDefaults.prompt("wake_up"));
    }
}
