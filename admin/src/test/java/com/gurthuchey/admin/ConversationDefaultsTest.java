package com.gurthuchey.admin;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ConversationDefaultsTest {
    @Test public void promptUsesSelectedCategoryAndDynamicTitle() {
        assertEquals("Hi [Name]! It's time for your [Reminder title] supplement.",
                ConversationDefaults.prompt("supplement"));
        assertEquals("Hi [Name]! It's time for your [Reminder title] bill or payment.",
                ConversationDefaults.prompt("payment"));
    }
}
