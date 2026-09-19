package com.gurthuchey.remindercall;

final class SpeechText {
    private SpeechText() {}

    static String custom(String template, String member, String reminder) {
        return custom(template, member, reminder, "te");
    }

    static String custom(String template, String member, String reminder, String language) {
        if (template == null) return "";
        String reminderTitle = clean(reminder, reminderFallback(language));
        return clean(template, "").replace("[Name]", clean(member, addressFallback(language)))
                .replace("[Reminder title]", reminderTitle)
                .replace("[Reminder]", reminderTitle);
    }

    static String medicineQuestion(String member, String medicine) {
        return medicineQuestion(member, medicine, "te");
    }

    static String medicineQuestion(String member, String medicine, String language) {
        String name = clean(member, addressFallback(language));
        String item = medicineName(medicine, language);
        switch (AppLanguage.normalize(language)) {
            case "en": return "Hello " + name + "! Have you taken your " + item + " tablet?";
            case "hi": return "नमस्ते " + name + "! क्या आपने " + item + " की गोली ले ली?";
            case "ta": return "வணக்கம் " + name + "! " + item + " மாத்திரை எடுத்துக்கொண்டீர்களா?";
            case "kn": return "ನಮಸ್ಕಾರ " + name + "! " + item + " ಮಾತ್ರೆ ತೆಗೆದುಕೊಂಡಿದ್ದೀರಾ?";
            case "ml": return "നമസ്കാരം " + name + "! " + item + " ഗുളിക കഴിച്ചോ?";
            default: return "హలో " + name + "! " + item + " టాబ్లెట్ వేసుకున్నారా?";
        }
    }

    static String medicineTaken(String member) { return medicineTaken(member, "te"); }

    static String medicineTaken(String member, String language) {
        String name = clean(member, addressFallback(language));
        switch (AppLanguage.normalize(language)) {
            case "en": return "Great, " + name + "! Bye!";
            case "hi": return "बहुत बढ़िया, " + name + "! फिर मिलते हैं। बाय!";
            case "ta": return "அருமை, " + name + "! பிறகு பார்க்கலாம். பை!";
            case "kn": return "ತುಂಬಾ ಚೆನ್ನಾಗಿದೆ, " + name + "! ಮತ್ತೆ ಸಿಗೋಣ. ಬೈ!";
            case "ml": return "സൂപ്പർ, " + name + "! പിന്നെ കാണാം. ബൈ!";
            default: return "సూపర్ " + name + "! ఉంటాను, Bye!";
        }
    }

    static String confirmationQuestion(String member, String reminder, String category,
            String language) {
        String name = clean(member, addressFallback(language));
        String item = clean(reminder, reminderFallback(language));
        boolean take = "medicine".equals(category) || "supplement".equals(category);
        boolean have = "meal".equals(category) || "drink".equals(category);
        switch (AppLanguage.normalize(language)) {
            case "hi": return "नमस्ते " + name + "! क्या आपने " + item
                    + (take ? " ले लिया?" : have ? " ले लिया?" : " पूरा कर लिया?");
            case "ta": return "வணக்கம் " + name + "! " + item
                    + (take ? " எடுத்துக்கொண்டீர்களா?" : have ? " சாப்பிட்டீர்களா?" : " முடித்துவிட்டீர்களா?");
            case "kn": return "ನಮಸ್ಕಾರ " + name + "! " + item
                    + (take ? " ತೆಗೆದುಕೊಂಡಿದ್ದೀರಾ?" : have ? " ಸೇವಿಸಿದ್ದೀರಾ?" : " ಮುಗಿಸಿದ್ದೀರಾ?");
            case "ml": return "നമസ്കാരം " + name + "! " + item
                    + (take ? " കഴിച്ചോ?" : have ? " കഴിച്ചോ?" : " പൂർത്തിയാക്കിയോ?");
            case "te": return "హలో " + name + "! " + item
                    + (take ? " వేసుకున్నారా?" : have ? " తీసుకున్నారా?" : " పూర్తి చేశారా?");
            default: return "Hi " + name + "! " + (take ? "Have you taken "
                    : have ? "Did you have " : "Did you complete ") + item + "?";
        }
    }

    static String confirmationNotTaken(String language) {
        switch (AppLanguage.normalize(language)) {
            case "hi": return "ठीक है। अभी कर लीजिए। मैं पाँच मिनट बाद फिर कॉल करूँगी। बाय!";
            case "ta": return "சரி. இப்போது செய்துவிடுங்கள். ஐந்து நிமிடங்களில் மீண்டும் அழைக்கிறேன். பை!";
            case "kn": return "ಸರಿ. ಈಗಲೇ ಮಾಡಿ. ಐದು ನಿಮಿಷಗಳ ನಂತರ ಮತ್ತೆ ಕರೆ ಮಾಡುತ್ತೇನೆ. ಬೈ!";
            case "ml": return "ശരി. ഇപ്പോൾ ചെയ്യൂ. അഞ്ച് മിനിറ്റിന് ശേഷം വീണ്ടും വിളിക്കാം. ബൈ!";
            case "te": return "సరే. ఇప్పుడే పూర్తి చేయండి. ఐదు నిమిషాల తర్వాత మళ్లీ కాల్ చేస్తాను. Bye!";
            default: return "Okay. Please do it now. I’ll call again in 5 minutes. Bye!";
        }
    }

    static String medicineNotTaken() { return medicineNotTaken("te"); }

    static String medicineNotTaken(String language) {
        switch (AppLanguage.normalize(language)) {
            case "en": return "Please take the tablet now. I’ll stay on the line. After taking it, tap “Taken”. If you need more time, tap “Remind me later”.";
            case "hi": return "अभी जाकर गोली ले लीजिए। मैं लाइन पर रहूँगी। लेने के बाद “ले लिया” बटन दबाएँ। और समय चाहिए तो “बाद में याद दिलाओ” बटन दबाएँ।";
            case "ta": return "இப்போது மாத்திரையை எடுத்துக்கொள்ளுங்கள். நான் இணைப்பிலேயே இருப்பேன். எடுத்த பிறகு “எடுத்துவிட்டேன்” பொத்தானை அழுத்துங்கள். இன்னும் நேரம் வேண்டுமெனில் “பிறகு நினைவூட்டு” பொத்தானை அழுத்துங்கள்.";
            case "kn": return "ಈಗ ಮಾತ್ರೆ ತೆಗೆದುಕೊಳ್ಳಿ. ನಾನು ಲೈನ್‌ನಲ್ಲೇ ಇರುತ್ತೇನೆ. ತೆಗೆದುಕೊಂಡ ನಂತರ “ತೆಗೆದುಕೊಂಡೆ” ಬಟನ್ ಒತ್ತಿರಿ. ಇನ್ನಷ್ಟು ಸಮಯ ಬೇಕಾದರೆ “ನಂತರ ನೆನಪಿಸು” ಬಟನ್ ಒತ್ತಿರಿ.";
            case "ml": return "ഇപ്പോൾ ഗുളിക കഴിക്കൂ. ഞാൻ ലൈനിൽ തന്നെ ഉണ്ടാകും. കഴിച്ച ശേഷം “കഴിച്ചു” ബട്ടൺ അമർത്തുക. കൂടുതൽ സമയം വേണമെങ്കിൽ “പിന്നീട് ഓർമ്മിപ്പിക്കൂ” ബട്ടൺ അമർത്തുക.";
            default: return "అయితే త్వరగా వెళ్లి టాబ్లెట్ వేసుకోండి. నేను లైన్‌లోనే ఉంటాను. "
                    + "వేసుకుని వచ్చాక, “వేసుకున్నా” బటన్ నొక్కండి. లేదా ఇంకా సమయం కావాలంటే, "
                    + "“తర్వాత గుర్తుచేయి” బటన్ నొక్కండి.";
        }
    }

    static String reminderDelayQuestion() { return reminderDelayQuestion("te"); }

    static String reminderDelayQuestion(String language) {
        switch (AppLanguage.normalize(language)) {
            case "en": return "How many minutes later should I remind you again?";
            case "hi": return "मैं आपको कितने मिनट बाद फिर याद दिलाऊँ?";
            case "ta": return "எத்தனை நிமிடங்கள் கழித்து மீண்டும் நினைவூட்ட வேண்டும்?";
            case "kn": return "ಎಷ್ಟು ನಿಮಿಷಗಳ ನಂತರ ಮತ್ತೆ ನೆನಪಿಸಬೇಕು?";
            case "ml": return "എത്ര മിനിറ്റിന് ശേഷം വീണ്ടും ഓർമ്മിപ്പിക്കണം?";
            default: return "ఎన్ని నిమిషాల తర్వాత మళ్లీ గుర్తు చేయాలి?";
        }
    }

    static String reminderDelayed(int minutes) { return reminderDelayed(minutes, "te"); }

    static String reminderDelayed(int minutes, String language) {
        String value = duration(minutes, language);
        switch (AppLanguage.normalize(language)) {
            case "en": return "Okay. I’ll call again in " + value + " minutes to remind you. Bye!";
            case "hi": return "ठीक है। मैं " + value + " मिनट बाद फिर कॉल करके याद दिलाऊँगी। बाय!";
            case "ta": return "சரி. " + value + " நிமிடங்கள் கழித்து மீண்டும் அழைத்து நினைவூட்டுகிறேன். பை!";
            case "kn": return "ಸರಿ. " + value + " ನಿಮಿಷಗಳ ನಂತರ ಮತ್ತೆ ಕರೆ ಮಾಡಿ ನೆನಪಿಸುತ್ತೇನೆ. ಬೈ!";
            case "ml": return "ശരി. " + value + " മിനിറ്റിന് ശേഷം വീണ്ടും വിളിച്ച് ഓർമ്മിപ്പിക്കാം. ബൈ!";
            default: return "సరే. " + value + " నిమిషాల తర్వాత మళ్లీ కాల్ చేసి గుర్తు చేస్తాను. Bye!";
        }
    }

    static String mealQuestion(String member, String medicine, boolean morning, int minutes) {
        return mealQuestion(member, medicine, morning, minutes, "te");
    }

    static String mealQuestion(String member, String medicine, boolean morning, int minutes,
            String language) {
        String name = clean(member, addressFallback(language));
        String item = medicineName(medicine, language);
        String value = duration(minutes, language);
        switch (AppLanguage.normalize(language)) {
            case "en": return "Hello " + name + "! Have you had " + (morning ? "breakfast" : "your meal")
                    + "? If not, please eat now. You need to take your " + item + " tablet in " + value
                    + " minutes. I’ll call you again in " + value + " minutes to remind you. Bye!";
            case "hi": return "नमस्ते " + name + "! क्या आपने " + (morning ? "नाश्ता" : "खाना")
                    + " खा लिया? अगर नहीं, तो अभी खा लीजिए। " + value + " मिनट बाद " + item
                    + " की गोली लेनी है। मैं " + value + " मिनट बाद फिर कॉल करके याद दिलाऊँगी। बाय!";
            case "ta": return "வணக்கம் " + name + "! " + (morning ? "காலை உணவு" : "சாப்பாடு")
                    + " சாப்பிட்டீர்களா? இல்லையெனில் இப்போது சாப்பிடுங்கள். இன்னும் " + value
                    + " நிமிடங்களில் " + item + " மாத்திரை எடுக்க வேண்டும். " + value
                    + " நிமிடங்களில் மீண்டும் அழைத்து நினைவூட்டுகிறேன். பை!";
            case "kn": return "ನಮಸ್ಕಾರ " + name + "! " + (morning ? "ತಿಂಡಿ" : "ಊಟ")
                    + " ಮಾಡಿದ್ದೀರಾ? ಇಲ್ಲದಿದ್ದರೆ ಈಗಲೇ ಮಾಡಿ. ಇನ್ನೂ " + value + " ನಿಮಿಷಗಳಲ್ಲಿ " + item
                    + " ಮಾತ್ರೆ ತೆಗೆದುಕೊಳ್ಳಬೇಕು. " + value + " ನಿಮಿಷಗಳಲ್ಲಿ ಮತ್ತೆ ಕರೆ ಮಾಡಿ ನೆನಪಿಸುತ್ತೇನೆ. ಬೈ!";
            case "ml": return "നമസ്കാരം " + name + "! " + (morning ? "പ്രഭാതഭക്ഷണം" : "ഭക്ഷണം")
                    + " കഴിച്ചോ? ഇല്ലെങ്കിൽ ഇപ്പോൾ കഴിക്കൂ. ഇനി " + value + " മിനിറ്റിൽ " + item
                    + " ഗുളിക കഴിക്കണം. " + value + " മിനിറ്റിന് ശേഷം വീണ്ടും വിളിച്ച് ഓർമ്മിപ്പിക്കാം. ബൈ!";
            default: return "హలో " + name + (morning
                    ? "! టిఫిన్ చేశారా? ఇంకా చేయకపోతే ఇప్పుడే చేయండి. "
                    : "! భోజనం చేశారా? ఇంకా చేయకపోతే ఇప్పుడే చేయండి. ")
                    + "ఇంకో " + value + " నిమిషాల్లో మీరు " + item
                    + " టాబ్లెట్ వేసుకోవాలి. నేను మరో " + value
                    + " నిమిషాల్లో మీకు కాల్ చేసి గుర్తు చేస్తాను. Bye";
        }
    }

    static String mealNotCompleted(String medicine) { return mealNotCompleted(medicine, "te"); }
    static String mealNotCompleted(String medicine, String language) {
        return medicineNotTaken(language);
    }

    static String mealAcknowledged() { return mealAcknowledged("te"); }
    static String mealAcknowledged(String language) {
        switch (AppLanguage.normalize(language)) {
            case "en": return "Okay. Bye!"; case "hi": return "ठीक है। बाय!";
            case "ta": return "சரி. பை!"; case "kn": return "ಸರಿ. ಬೈ!";
            case "ml": return "ശരി. ബൈ!"; default: return "సరే. Bye!";
        }
    }

    static String answerTaken(String language) { return AppLanguage.ui(language, "Taken"); }
    static String answerNotTaken(String language) { return AppLanguage.ui(language, "Not taken yet"); }
    static String answerLater(String language) { return AppLanguage.ui(language, "Remind me later"); }
    static String confirmationDone(String language) {
        switch (AppLanguage.normalize(language)) {
            case "te": return "పూర్తయింది"; case "hi": return "हो गया";
            case "ta": return "முடிந்தது"; case "kn": return "ಮುಗಿದಿದೆ";
            case "ml": return "ചെയ്തു"; default: return "Done";
        }
    }

    static String confirmationNotDone(String language) {
        switch (AppLanguage.normalize(language)) {
            case "te": return "ఇంకా లేదు"; case "hi": return "अभी नहीं";
            case "ta": return "இன்னும் இல்லை"; case "kn": return "ಇನ್ನೂ ಇಲ್ಲ";
            case "ml": return "ഇതുവരെ ഇല്ല"; default: return "Not yet";
        }
    }

    private static String duration(int minutes, String language) {
        if ("en".equals(AppLanguage.normalize(language))) return String.valueOf(Math.max(1, minutes));
        if ("te".equals(AppLanguage.normalize(language))) {
            if (minutes == 5) return "ఐదు"; if (minutes == 15) return "పదిహేను";
            if (minutes == 30) return "ముప్పై"; if (minutes == 60) return "అరవై";
        }
        return String.valueOf(Math.max(1, minutes));
    }

    private static String medicineName(String value, String language) {
        String result = clean(value, reminderFallback(language))
                .replaceFirst("(?i)(?:[\\s\\u200B-\\u200D\\uFEFF]*(?:tablet|టాబ్లెట్|गोली|மாத்திரை|ಮಾತ್ರೆ|ഗുളിക)[\\s\\u200B-\\u200D\\uFEFF]*)+$", "")
                .trim();
        return result.isEmpty() ? reminderFallback(language) : result;
    }

    private static String addressFallback(String language) {
        switch (AppLanguage.normalize(language)) {
            case "en": return "there"; case "hi": return "जी"; case "ta": return "ஐயா";
            case "kn": return "ಅವರೇ"; case "ml": return "ചേട്ടാ"; default: return "అండి";
        }
    }

    private static String reminderFallback(String language) {
        switch (AppLanguage.normalize(language)) {
            case "en": return "medicine"; case "hi": return "दवा"; case "ta": return "மருந்து";
            case "kn": return "ಔಷಧಿ"; case "ml": return "മരുന്ന്"; default: return "మందు";
        }
    }

    private static String clean(String value, String fallback) {
        if (value == null) return fallback;
        String result = value.replaceAll("[\\x00-\\x1f\\x7f]", " ")
                .replaceAll("[\\s\\p{Z}\\uFEFF]+", " ").trim();
        return result.isEmpty() ? fallback : result;
    }
}
