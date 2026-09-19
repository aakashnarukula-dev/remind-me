package com.gurthuchey.admin;

final class ConversationDefaults {
    private ConversationDefaults() {}

    static String prompt(String category) {
        return "Hi [Name]. It's time for your [Reminder title] [category].";
    }
}
