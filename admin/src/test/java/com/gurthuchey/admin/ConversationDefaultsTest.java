package com.gurthuchey.admin;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ConversationDefaultsTest {
    @Test public void promptUsesDynamicCategoryAndTitle() {
        assertEquals("Hi [Name]. It's time for your [Reminder title] [category].",
                ConversationDefaults.prompt("supplement"));
        assertEquals("Hi [Name]. It's time for your [Reminder title] [category].",
                ConversationDefaults.prompt("payment"));
    }
}
