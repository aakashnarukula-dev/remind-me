package com.gurthuchey.admin;

import android.app.LocaleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

final class AppLanguage {
    private static final String LAUNCHER_COMPONENT_PACKAGE = "com.gurthuchey.admin";
    static final String[] CODES = {"en", "te", "hi", "ta", "kn", "ml"};
    static final String[] NAMES = {"English", "తెలుగు", "हिन्दी", "தமிழ்", "ಕನ್ನಡ", "മലയാളം"};
    private static final String PREFS = "app_language";
    private static final String KEY = "code";
    private AppLanguage() {}

    static String current(Context context) {
        return normalize(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, "en"));
    }

    static void set(Context context, String value) {
        String code = normalize(value);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, code).apply();
        if (Build.VERSION.SDK_INT >= 33) {
            LocaleManager manager = context.getSystemService(LocaleManager.class);
            if (manager != null) {
                manager.setApplicationLocales(LocaleList.forLanguageTags(code + "-IN"));
            }
        }
        syncLauncherLabel(context, code);
    }

    static void syncLauncherLabel(Context context) {
        syncLauncherLabel(context, current(context));
    }

    private static void syncLauncherLabel(Context context, String code) {
        String selected = launcherSuffix(normalize(code));
        PackageManager manager = context.getPackageManager();
        setLauncherState(context, manager, selected, true);
        for (String suffix : new String[]{"English", "Telugu", "Hindi", "Tamil", "Kannada", "Malayalam"}) {
            if (!suffix.equals(selected)) setLauncherState(context, manager, suffix, false);
        }
    }

    private static void setLauncherState(Context context, PackageManager manager,
            String suffix, boolean enabled) {
        ComponentName component = new ComponentName(context.getPackageName(),
                LAUNCHER_COMPONENT_PACKAGE + ".Launcher" + suffix);
        int state = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        if (manager.getComponentEnabledSetting(component) != state) {
            manager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP);
        }
    }

    private static String launcherSuffix(String code) {
        switch (normalize(code)) {
            case "te": return "Telugu";
            case "hi": return "Hindi";
            case "ta": return "Tamil";
            case "kn": return "Kannada";
            case "ml": return "Malayalam";
            default: return "English";
        }
    }

    static String normalize(String value) {
        if (value != null) for (String code : CODES) if (code.equals(value)) return code;
        return "en";
    }

    static String detect(String text, String fallback) {
        int[] counts = new int[6];
        String source = text == null ? "" : text.replace("[Name]", "")
                .replace("[Reminder title]", "").replace("[Reminder]", "")
                .replace("[category]", "").replace("[Category]", "");
        if (!source.isEmpty()) {
            for (int offset = 0; offset < source.length();) {
                int value = source.codePointAt(offset);
                offset += Character.charCount(value);
                if (value >= 0x0C00 && value <= 0x0C7F) counts[1]++;
                else if (value >= 0x0900 && value <= 0x097F) counts[2]++;
                else if (value >= 0x0B80 && value <= 0x0BFF) counts[3]++;
                else if (value >= 0x0C80 && value <= 0x0CFF) counts[4]++;
                else if (value >= 0x0D00 && value <= 0x0D7F) counts[5]++;
                else if ((value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z')) counts[0]++;
            }
        }
        int best = -1;
        for (int index = 0; index < counts.length; index++) {
            if (counts[index] > 0 && (best < 0 || counts[index] > counts[best])) best = index;
        }
        return best < 0 ? normalize(fallback) : CODES[best];
    }

    static String title(Context context) { return title(current(context)); }

    static String title(String code) {
        switch (normalize(code)) {
            case "te": return "గుర్తు చేయి";
            case "hi": return "याद दिलाओ";
            case "ta": return "நினைவூட்டு";
            case "kn": return "ನೆನಪಿಸು";
            case "ml": return "ഓർമ്മിപ്പിക്കൂ";
            default: return "Remind me";
        }
    }

    static String count(Context context, int count, String singular, String plural) {
        String noun = count == 1 ? singular : plural;
        return count + " " + ui(context, noun);
    }

    static String timePeriod(Context context, int hour, int minute) {
        String code = current(context);
        int normalizedHour = Math.max(0, Math.min(23, hour));
        int normalizedMinute = Math.max(0, Math.min(59, minute));
        int minutesAfterMidnight = normalizedHour * 60 + normalizedMinute;
        int period = minutesAfterMidnight >= 5 * 60 && minutesAfterMidnight < 12 * 60 ? 0
                : minutesAfterMidnight >= 12 * 60 && minutesAfterMidnight < 16 * 60 ? 1
                : minutesAfterMidnight >= 16 * 60 && minutesAfterMidnight <= 19 * 60 ? 2 : 3;
        String[][] names = {
                {"Morning", "Afternoon", "Evening", "Night"},
                {"ఉదయం", "మధ్యాహ్నం", "సాయంత్రం", "రాత్రి"},
                {"सुबह", "दोपहर", "शाम", "रात"},
                {"காலை", "மதியம்", "மாலை", "இரவு"},
                {"ಬೆಳಗ್ಗೆ", "ಮಧ್ಯಾಹ್ನ", "ಸಂಜೆ", "ರಾತ್ರಿ"},
                {"രാവിലെ", "ഉച്ചയ്ക്ക്", "വൈകുന്നേരം", "രാത്രി"}
        };
        int language = "en".equals(code) ? 0 : "te".equals(code) ? 1 : "hi".equals(code) ? 2
                : "ta".equals(code) ? 3 : "kn".equals(code) ? 4 : 5;
        return names[language][period];
    }

    static String[] shortDays(Context context) {
        return shortDays(current(context));
    }

    static String[] shortDays(String code) {
        switch (normalize(code)) {
            case "te": return new String[]{"సోమ", "మంగళ", "బుధ", "గురు", "శుక్ర", "శని", "ఆది"};
            case "hi": return new String[]{"सोम", "मंगल", "बुध", "गुरु", "शुक्र", "शनि", "रवि"};
            case "ta": return new String[]{"திங்கள்", "செவ்", "புதன்", "வியா", "வெள்ளி", "சனி", "ஞாயி"};
            case "kn": return new String[]{"ಸೋಮ", "ಮಂಗಳ", "ಬುಧ", "ಗುರು", "ಶುಕ್ರ", "ಶನಿ", "ಭಾನು"};
            case "ml": return new String[]{"തിങ്കൾ", "ചൊവ്വ", "ബുധൻ", "വ്യാഴം", "വെള്ളി", "ശനി", "ഞായർ"};
            default: return new String[]{"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        }
    }

    static String category(Context context, String category) {
        if (category == null) return ui(context, "Custom");
        switch (category) {
            case "medicine": return ui(context, "Medicine");
            case "supplement": return ui(context, "Supplement");
            case "meal": return ui(context, "Meal");
            case "drink": return ui(context, "Drink");
            case "exercise": return ui(context, "Exercise");
            case "appointment": return ui(context, "Appointment");
            case "payment": return ui(context, "Bill or payment");
            case "task": return ui(context, "Task");
            case "wake_up": return ui(context, "Wake-up");
            default: return ui(context, "Custom");
        }
    }

    static String ui(Context context, String english) {
        return ui(current(context), english);
    }

    static String ui(String code, String english) {
        if (english == null || "en".equals(normalize(code))) return english;
        switch (normalize(code)) {
            case "te": return telugu(english);
            case "hi": return hindi(english);
            case "ta": return tamil(english);
            case "kn": return kannada(english);
            case "ml": return malayalam(english);
            default: return english;
        }
    }

    private static String telugu(String s) {
        switch (s) {
            case "Admin": return "అడ్మిన్";
            case "Members": return "సభ్యులు";
            case "member": return "సభ్యుడు";
            case "members": return "సభ్యులు";
            case "Add Member": return "సభ్యుడిని జోడించండి";
            case "Add reminder": return "రిమైండర్ జోడించండి";
            case "Edit reminder": return "రిమైండర్ మార్చండి";
            case "Save changes": return "మార్పులు సేవ్ చేయండి";
            case "Delete": return "తొలగించండి";
            case "Remove": return "తీసివేయండి";
            case "Cancel": return "రద్దు";
            case "Log out": return "లాగ్ అవుట్";
            case "Language": return "భాష";
            case "Category": return "వర్గం";
            case "Medicine": return "మందు";
            case "Supplement": return "సప్లిమెంట్";
            case "Meal": return "భోజనం";
            case "Drink": return "పానీయం";
            case "Exercise": return "వ్యాయామం";
            case "Appointment": return "అపాయింట్మెంట్";
            case "Bill or payment": return "బిల్ లేదా చెల్లింపు";
            case "Task": return "పని";
            case "Wake-up": return "నిద్ర లేపడం";
            case "Custom": return "కస్టమ్";
            case "Medicine name": return "మందు పేరు";
            case "Reminder title": return "రిమైండర్ పేరు";
            case "Call time": return "కాల్ సమయం";
            case "Repeat on": return "మళ్లీ వచ్చే రోజులు";
            case "Meal Reminder": return "భోజన రిమైండర్";
            case "Call conversation": return "కాల్ సంభాషణ";
            case "Edit questions and answers": return "ప్రశ్నలు, సమాధానాలు మార్చండి";
            case "Add question": return "ప్రశ్న జోడించండి";
            case "Edit question": return "ప్రశ్న మార్చండి";
            case "Save question": return "ప్రశ్న సేవ్ చేయండి";
            case "Question spoken by Chitti": return "చిట్టి మాట్లాడే ప్రశ్న";
            case "No reminders yet.": return "ఇంకా రిమైండర్లు లేవు.";
            case "Every day": return "ప్రతి రోజు";
            case "No days": return "రోజులు లేవు";
            case "Add phone": return "ఫోన్ జోడించండి";
            case "Change phone": return "ఫోన్ మార్చండి";
            case "Add medicine": return "మందు జోడించండి";
            case "Private admin access": return "ప్రైవేట్ అడ్మిన్ యాక్సెస్";
            case "Please wait…": return "దయచేసి వేచి ఉండండి…";
            case "Send OTP": return "OTP పంపండి";
            case "Resend OTP": return "OTP మళ్లీ పంపండి";
            default: return s;
        }
    }

    private static String hindi(String s) {
        switch (s) {
            case "Admin": return "एडमिन"; case "Members": return "सदस्य";
            case "member": return "सदस्य"; case "members": return "सदस्य";
            case "Add Member": return "सदस्य जोड़ें"; case "Add reminder": return "रिमाइंडर जोड़ें";
            case "Edit reminder": return "रिमाइंडर बदलें"; case "Save changes": return "बदलाव सेव करें";
            case "Delete": return "हटाएँ"; case "Remove": return "हटाएँ"; case "Cancel": return "रद्द करें";
            case "Log out": return "लॉग आउट"; case "Language": return "भाषा"; case "Category": return "श्रेणी";
            case "Medicine": return "दवा"; case "Supplement": return "सप्लीमेंट"; case "Meal": return "भोजन";
            case "Drink": return "पेय"; case "Exercise": return "व्यायाम"; case "Appointment": return "अपॉइंटमेंट";
            case "Bill or payment": return "बिल या भुगतान"; case "Task": return "काम"; case "Wake-up": return "जगाना";
            case "Custom": return "कस्टम"; case "Medicine name": return "दवा का नाम";
            case "Reminder title": return "रिमाइंडर का नाम"; case "Call time": return "कॉल का समय";
            case "Repeat on": return "दोहराने के दिन"; case "Meal Reminder": return "भोजन रिमाइंडर";
            case "Call conversation": return "कॉल बातचीत"; case "Edit questions and answers": return "सवाल और जवाब बदलें";
            case "Add question": return "सवाल जोड़ें"; case "Edit question": return "सवाल बदलें";
            case "Save question": return "सवाल सेव करें"; case "Question spoken by Chitti": return "चिट्टी का सवाल";
            case "No reminders yet.": return "अभी कोई रिमाइंडर नहीं है।"; case "Every day": return "हर दिन";
            case "No days": return "कोई दिन नहीं"; case "Add phone": return "फोन जोड़ें";
            case "Change phone": return "फोन बदलें"; case "Add medicine": return "दवा जोड़ें";
            case "Private admin access": return "निजी एडमिन एक्सेस"; case "Please wait…": return "कृपया प्रतीक्षा करें…";
            case "Send OTP": return "OTP भेजें"; case "Resend OTP": return "OTP फिर भेजें";
            default: return s;
        }
    }

    private static String tamil(String s) {
        switch (s) {
            case "Admin": return "நிர்வாகி"; case "Members": return "உறுப்பினர்கள்";
            case "member": return "உறுப்பினர்"; case "members": return "உறுப்பினர்கள்";
            case "Add Member": return "உறுப்பினரைச் சேர்"; case "Add reminder": return "நினைவூட்டலைச் சேர்";
            case "Edit reminder": return "நினைவூட்டலைத் திருத்து"; case "Save changes": return "மாற்றங்களைச் சேமி";
            case "Delete": return "நீக்கு"; case "Remove": return "அகற்று"; case "Cancel": return "ரத்து செய்";
            case "Log out": return "வெளியேறு"; case "Language": return "மொழி"; case "Category": return "வகை";
            case "Medicine": return "மருந்து"; case "Supplement": return "ஊட்டச்சத்து மாத்திரை"; case "Meal": return "உணவு";
            case "Drink": return "பானம்"; case "Exercise": return "உடற்பயிற்சி"; case "Appointment": return "சந்திப்பு";
            case "Bill or payment": return "பில் அல்லது கட்டணம்"; case "Task": return "பணி"; case "Wake-up": return "எழுப்புதல்";
            case "Custom": return "தனிப்பயன்"; case "Medicine name": return "மருந்தின் பெயர்";
            case "Reminder title": return "நினைவூட்டல் பெயர்"; case "Call time": return "அழைப்பு நேரம்";
            case "Repeat on": return "மீண்டும் வரும் நாட்கள்"; case "Meal Reminder": return "உணவு நினைவூட்டல்";
            case "Call conversation": return "அழைப்பு உரையாடல்"; case "Edit questions and answers": return "கேள்வி, பதில்களைத் திருத்து";
            case "Add question": return "கேள்வியைச் சேர்"; case "Edit question": return "கேள்வியைத் திருத்து";
            case "Save question": return "கேள்வியைச் சேமி"; case "Question spoken by Chitti": return "சிட்டி கேட்கும் கேள்வி";
            case "No reminders yet.": return "இன்னும் நினைவூட்டல்கள் இல்லை."; case "Every day": return "தினமும்";
            case "No days": return "நாட்கள் இல்லை"; case "Add phone": return "தொலைபேசியைச் சேர்";
            case "Change phone": return "தொலைபேசியை மாற்று"; case "Add medicine": return "மருந்தைச் சேர்";
            case "Private admin access": return "தனிப்பட்ட நிர்வாக அணுகல்"; case "Please wait…": return "காத்திருக்கவும்…";
            case "Send OTP": return "OTP அனுப்பு"; case "Resend OTP": return "OTP மீண்டும் அனுப்பு";
            default: return s;
        }
    }

    private static String kannada(String s) {
        switch (s) {
            case "Admin": return "ನಿರ್ವಾಹಕ"; case "Members": return "ಸದಸ್ಯರು";
            case "member": return "ಸದಸ್ಯ"; case "members": return "ಸದಸ್ಯರು";
            case "Add Member": return "ಸದಸ್ಯರನ್ನು ಸೇರಿಸಿ"; case "Add reminder": return "ಜ್ಞಾಪನೆ ಸೇರಿಸಿ";
            case "Edit reminder": return "ಜ್ಞಾಪನೆ ಬದಲಿಸಿ"; case "Save changes": return "ಬದಲಾವಣೆಗಳನ್ನು ಉಳಿಸಿ";
            case "Delete": return "ಅಳಿಸಿ"; case "Remove": return "ತೆಗೆದುಹಾಕಿ"; case "Cancel": return "ರದ್ದು";
            case "Log out": return "ಲಾಗ್ ಔಟ್"; case "Language": return "ಭಾಷೆ"; case "Category": return "ವರ್ಗ";
            case "Medicine": return "ಔಷಧಿ"; case "Supplement": return "ಪೂರಕ ಮಾತ್ರೆ"; case "Meal": return "ಊಟ";
            case "Drink": return "ಪಾನೀಯ"; case "Exercise": return "ವ್ಯಾಯಾಮ"; case "Appointment": return "ಭೇಟಿ";
            case "Bill or payment": return "ಬಿಲ್ ಅಥವಾ ಪಾವತಿ"; case "Task": return "ಕೆಲಸ"; case "Wake-up": return "ಎಚ್ಚರಿಸುವುದು";
            case "Custom": return "ಕಸ್ಟಮ್"; case "Medicine name": return "ಔಷಧಿಯ ಹೆಸರು";
            case "Reminder title": return "ಜ್ಞಾಪನೆಯ ಹೆಸರು"; case "Call time": return "ಕರೆ ಸಮಯ";
            case "Repeat on": return "ಮರುಕಳಿಸುವ ದಿನಗಳು"; case "Meal Reminder": return "ಊಟದ ಜ್ಞಾಪನೆ";
            case "Call conversation": return "ಕರೆ ಸಂಭಾಷಣೆ"; case "Edit questions and answers": return "ಪ್ರಶ್ನೆ, ಉತ್ತರ ಬದಲಿಸಿ";
            case "Add question": return "ಪ್ರಶ್ನೆ ಸೇರಿಸಿ"; case "Edit question": return "ಪ್ರಶ್ನೆ ಬದಲಿಸಿ";
            case "Save question": return "ಪ್ರಶ್ನೆ ಉಳಿಸಿ"; case "Question spoken by Chitti": return "ಚಿಟ್ಟಿ ಕೇಳುವ ಪ್ರಶ್ನೆ";
            case "No reminders yet.": return "ಇನ್ನೂ ಜ್ಞಾಪನೆಗಳಿಲ್ಲ."; case "Every day": return "ಪ್ರತಿ ದಿನ";
            case "No days": return "ದಿನಗಳಿಲ್ಲ"; case "Add phone": return "ಫೋನ್ ಸೇರಿಸಿ";
            case "Change phone": return "ಫೋನ್ ಬದಲಿಸಿ"; case "Add medicine": return "ಔಷಧಿ ಸೇರಿಸಿ";
            case "Private admin access": return "ಖಾಸಗಿ ನಿರ್ವಾಹಕ ಪ್ರವೇಶ"; case "Please wait…": return "ದಯವಿಟ್ಟು ಕಾಯಿರಿ…";
            case "Send OTP": return "OTP ಕಳುಹಿಸಿ"; case "Resend OTP": return "OTP ಮತ್ತೆ ಕಳುಹಿಸಿ";
            default: return s;
        }
    }

    private static String malayalam(String s) {
        switch (s) {
            case "Admin": return "അഡ്മിൻ"; case "Members": return "അംഗങ്ങൾ";
            case "member": return "അംഗം"; case "members": return "അംഗങ്ങൾ";
            case "Add Member": return "അംഗത്തെ ചേർക്കുക"; case "Add reminder": return "ഓർമ്മപ്പെടുത്തൽ ചേർക്കുക";
            case "Edit reminder": return "ഓർമ്മപ്പെടുത്തൽ തിരുത്തുക"; case "Save changes": return "മാറ്റങ്ങൾ സേവ് ചെയ്യുക";
            case "Delete": return "നീക്കുക"; case "Remove": return "ഒഴിവാക്കുക"; case "Cancel": return "റദ്ദാക്കുക";
            case "Log out": return "ലോഗ് ഔട്ട്"; case "Language": return "ഭാഷ"; case "Category": return "വിഭാഗം";
            case "Medicine": return "മരുന്ന്"; case "Supplement": return "സപ്ലിമെന്റ്"; case "Meal": return "ഭക്ഷണം";
            case "Drink": return "പാനീയം"; case "Exercise": return "വ്യായാമം"; case "Appointment": return "അപ്പോയിന്റ്മെന്റ്";
            case "Bill or payment": return "ബിൽ അല്ലെങ്കിൽ പണമടയ്ക്കൽ"; case "Task": return "ജോലി"; case "Wake-up": return "ഉണർത്തൽ";
            case "Custom": return "കസ്റ്റം"; case "Medicine name": return "മരുന്നിന്റെ പേര്";
            case "Reminder title": return "ഓർമ്മപ്പെടുത്തലിന്റെ പേര്"; case "Call time": return "കോൾ സമയം";
            case "Repeat on": return "ആവർത്തിക്കുന്ന ദിവസങ്ങൾ"; case "Meal Reminder": return "ഭക്ഷണ ഓർമ്മപ്പെടുത്തൽ";
            case "Call conversation": return "കോൾ സംഭാഷണം"; case "Edit questions and answers": return "ചോദ്യങ്ങളും ഉത്തരങ്ങളും തിരുത്തുക";
            case "Add question": return "ചോദ്യം ചേർക്കുക"; case "Edit question": return "ചോദ്യം തിരുത്തുക";
            case "Save question": return "ചോദ്യം സേവ് ചെയ്യുക"; case "Question spoken by Chitti": return "ചിട്ടി ചോദിക്കുന്ന ചോദ്യം";
            case "No reminders yet.": return "ഇനിയും ഓർമ്മപ്പെടുത്തലുകളില്ല."; case "Every day": return "എല്ലാ ദിവസവും";
            case "No days": return "ദിവസങ്ങളില്ല"; case "Add phone": return "ഫോൺ ചേർക്കുക";
            case "Change phone": return "ഫോൺ മാറ്റുക"; case "Add medicine": return "മരുന്ന് ചേർക്കുക";
            case "Private admin access": return "സ്വകാര്യ അഡ്മിൻ ആക്സസ്"; case "Please wait…": return "ദയവായി കാത്തിരിക്കുക…";
            case "Send OTP": return "OTP അയയ്ക്കുക"; case "Resend OTP": return "OTP വീണ്ടും അയയ്ക്കുക";
            default: return s;
        }
    }
}
