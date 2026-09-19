package com.gurthuchey.remindercall;

final class ConversationDefaults {
    private ConversationDefaults() {}

    static String prompt(String category) {
        return "Hi [Name]! It's time for your [Reminder title] "
                + categoryPhrase(category) + ".";
    }

    static String categoryPhrase(String category) {
        if (category == null) return "reminder";
        switch (category) {
            case "medicine": return "medicine";
            case "supplement": return "supplement";
            case "meal": return "meal";
            case "drink": return "drink";
            case "exercise": return "exercise";
            case "appointment": return "appointment";
            case "payment": return "bill or payment";
            case "task": return "task";
            case "wake_up": return "wake-up reminder";
            default: return "reminder";
        }
    }
}
