package com.gurthuchey.remindercall;

import android.app.LocaleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

final class AppLanguage {
    private static final String LAUNCHER_COMPONENT_PACKAGE = "com.gurthuchey.remindercall";
    static final String[] CODES = {"en", "te", "hi", "ta", "kn", "ml"};
    static final String[] NAMES = {"English", "తెలుగు", "हिन्दी", "தமிழ்", "ಕನ್ನಡ", "മലയാളം"};
    private static final String PREFS = "app_language";
    private static final String KEY = "code";
    private static final String[] UI_KEYS = {
            "Language", "Log out", "Add reminder", "Edit reminder", "Save changes",
            "Delete", "Cancel", "Category", "Medicine", "Supplement", "Meal", "Drink",
            "Exercise", "Appointment", "Bill or payment", "Task", "Wake-up", "Custom",
            "Medicine name", "Reminder title", "Call time", "Repeat on", "Meal Reminder",
            "Call conversation", "Edit questions and answers", "Add question", "Edit question",
            "Save question", "Question spoken by Chitti", "Every day", "No days",
            "No reminders yet. Tap + to create one.", "No active reminders.",
            "Restore reminders", "Retry restore", "Use another number", "Sign in with mobile",
            "Use the number that Admin assigned to you.", "Please wait…", "Resend OTP", "Send OTP",
            "Your reminders. Your scheduled calls.", "Reminder call", "Reject", "Answer",
            "Swipe to reject", "Swipe to answer", "Taken", "Not taken yet", "Remind me later",
            "Turn speaker off", "Turn speaker on", "Connect to Admin", "Connect phone",
            "Choose time", "Choose a time later today.", "Skip for today"
    };

    private static final String[][] UI_VALUES = {
            {"భాష", "లాగ్ అవుట్", "రిమైండర్ జోడించండి", "రిమైండర్ మార్చండి", "మార్పులు సేవ్ చేయండి",
                    "తొలగించండి", "రద్దు", "వర్గం", "మందు", "సప్లిమెంట్", "భోజనం", "పానీయం",
                    "వ్యాయామం", "అపాయింట్మెంట్", "బిల్ లేదా చెల్లింపు", "పని", "నిద్ర లేపడం", "కస్టమ్",
                    "మందు పేరు", "రిమైండర్ పేరు", "కాల్ సమయం", "మళ్లీ వచ్చే రోజులు", "భోజన రిమైండర్",
                    "కాల్ సంభాషణ", "ప్రశ్నలు, సమాధానాలు మార్చండి", "ప్రశ్న జోడించండి", "ప్రశ్న మార్చండి",
                    "ప్రశ్న సేవ్ చేయండి", "చిట్టి మాట్లాడే ప్రశ్న", "ప్రతి రోజు", "రోజులు లేవు",
                    "ఇంకా రిమైండర్లు లేవు. జోడించడానికి + నొక్కండి.", "యాక్టివ్ రిమైండర్లు లేవు.",
                    "రిమైండర్లు తిరిగి పొందండి", "మళ్లీ ప్రయత్నించండి", "మరో నంబర్ వాడండి", "మొబైల్‌తో సైన్ ఇన్",
                    "అడ్మిన్ మీకు కేటాయించిన నంబర్ వాడండి.", "దయచేసి వేచి ఉండండి…", "OTP మళ్లీ పంపండి", "OTP పంపండి",
                    "మీ రిమైండర్లు. మీ షెడ్యూల్ కాల్స్.", "రిమైండర్ కాల్", "తిరస్కరించు", "స్వీకరించు",
                    "తిరస్కరించడానికి స్వైప్ చేయండి", "స్వీకరించడానికి స్వైప్ చేయండి", "వేసుకున్నా", "ఇంకా వేసుకోలేదు", "తర్వాత గుర్తుచేయి",
                    "స్పీకర్ ఆఫ్ చేయండి", "స్పీకర్ ఆన్ చేయండి", "అడ్మిన్‌కు కనెక్ట్ చేయండి", "ఫోన్ కనెక్ట్ చేయండి",
                    "సమయం ఎంచుకోండి", "ఈ రోజు తర్వాతి సమయాన్ని ఎంచుకోండి.", "ఈ రోజుకు వద్దు"},
            {"भाषा", "लॉग आउट", "रिमाइंडर जोड़ें", "रिमाइंडर बदलें", "बदलाव सेव करें",
                    "हटाएँ", "रद्द करें", "श्रेणी", "दवा", "सप्लीमेंट", "भोजन", "पेय",
                    "व्यायाम", "अपॉइंटमेंट", "बिल या भुगतान", "काम", "जगाना", "कस्टम",
                    "दवा का नाम", "रिमाइंडर का नाम", "कॉल का समय", "दोहराने के दिन", "भोजन रिमाइंडर",
                    "कॉल बातचीत", "सवाल और जवाब बदलें", "सवाल जोड़ें", "सवाल बदलें", "सवाल सेव करें",
                    "चिट्टी का सवाल", "हर दिन", "कोई दिन नहीं", "अभी कोई रिमाइंडर नहीं है। जोड़ने के लिए + दबाएँ।",
                    "कोई सक्रिय रिमाइंडर नहीं है।", "रिमाइंडर वापस पाएँ", "फिर कोशिश करें", "दूसरा नंबर इस्तेमाल करें",
                    "मोबाइल से साइन इन", "एडमिन द्वारा दिया गया नंबर इस्तेमाल करें।", "कृपया प्रतीक्षा करें…",
                    "OTP फिर भेजें", "OTP भेजें", "आपके रिमाइंडर। आपकी तय कॉल।", "रिमाइंडर कॉल",
                    "अस्वीकार", "उत्तर दें", "अस्वीकार करने के लिए स्वाइप करें", "उत्तर देने के लिए स्वाइप करें",
                    "ले लिया", "अभी नहीं लिया", "बाद में याद दिलाओ", "स्पीकर बंद करें", "स्पीकर चालू करें",
                    "एडमिन से जोड़ें", "फोन जोड़ें", "समय चुनें", "आज का बाद का समय चुनें।", "आज के लिए छोड़ें"},
            {"மொழி", "வெளியேறு", "நினைவூட்டலைச் சேர்", "நினைவூட்டலைத் திருத்து", "மாற்றங்களைச் சேமி",
                    "நீக்கு", "ரத்து செய்", "வகை", "மருந்து", "ஊட்டச்சத்து மாத்திரை", "உணவு", "பானம்",
                    "உடற்பயிற்சி", "சந்திப்பு", "பில் அல்லது கட்டணம்", "பணி", "எழுப்புதல்", "தனிப்பயன்",
                    "மருந்தின் பெயர்", "நினைவூட்டல் பெயர்", "அழைப்பு நேரம்", "மீண்டும் வரும் நாட்கள்", "உணவு நினைவூட்டல்",
                    "அழைப்பு உரையாடல்", "கேள்வி, பதில்களைத் திருத்து", "கேள்வியைச் சேர்", "கேள்வியைத் திருத்து",
                    "கேள்வியைச் சேமி", "சிட்டி கேட்கும் கேள்வி", "தினமும்", "நாட்கள் இல்லை",
                    "இன்னும் நினைவூட்டல்கள் இல்லை. சேர்க்க + அழுத்தவும்.", "செயலில் உள்ள நினைவூட்டல்கள் இல்லை.",
                    "நினைவூட்டல்களை மீட்டெடு", "மீண்டும் முயற்சி", "வேறு எண்ணைப் பயன்படுத்து", "மொபைல் மூலம் உள்நுழை",
                    "நிர்வாகி ஒதுக்கிய எண்ணைப் பயன்படுத்தவும்.", "காத்திருக்கவும்…", "OTP மீண்டும் அனுப்பு", "OTP அனுப்பு",
                    "உங்கள் நினைவூட்டல்கள். உங்கள் திட்டமிட்ட அழைப்புகள்.", "நினைவூட்டல் அழைப்பு", "நிராகரி", "பதில் அளி",
                    "நிராகரிக்க ஸ்வைப் செய்", "பதில் அளிக்க ஸ்வைப் செய்", "எடுத்துவிட்டேன்", "இன்னும் எடுக்கவில்லை", "பிறகு நினைவூட்டு",
                    "ஸ்பீக்கரை அணை", "ஸ்பீக்கரை இயக்கு", "நிர்வாகியுடன் இணை", "தொலைபேசியை இணை",
                    "நேரத்தைத் தேர்வு செய்", "இன்றைய பிற்பட்ட நேரத்தைத் தேர்வு செய்யுங்கள்.", "இன்றைக்கு வேண்டாம்"},
            {"ಭಾಷೆ", "ಲಾಗ್ ಔಟ್", "ಜ್ಞಾಪನೆ ಸೇರಿಸಿ", "ಜ್ಞಾಪನೆ ಬದಲಿಸಿ", "ಬದಲಾವಣೆಗಳನ್ನು ಉಳಿಸಿ",
                    "ಅಳಿಸಿ", "ರದ್ದು", "ವರ್ಗ", "ಔಷಧಿ", "ಪೂರಕ ಮಾತ್ರೆ", "ಊಟ", "ಪಾನೀಯ",
                    "ವ್ಯಾಯಾಮ", "ಭೇಟಿ", "ಬಿಲ್ ಅಥವಾ ಪಾವತಿ", "ಕೆಲಸ", "ಎಚ್ಚರಿಸುವುದು", "ಕಸ್ಟಮ್",
                    "ಔಷಧಿಯ ಹೆಸರು", "ಜ್ಞಾಪನೆಯ ಹೆಸರು", "ಕರೆ ಸಮಯ", "ಮರುಕಳಿಸುವ ದಿನಗಳು", "ಊಟದ ಜ್ಞಾಪನೆ",
                    "ಕರೆ ಸಂಭಾಷಣೆ", "ಪ್ರಶ್ನೆ, ಉತ್ತರ ಬದಲಿಸಿ", "ಪ್ರಶ್ನೆ ಸೇರಿಸಿ", "ಪ್ರಶ್ನೆ ಬದಲಿಸಿ", "ಪ್ರಶ್ನೆ ಉಳಿಸಿ",
                    "ಚಿಟ್ಟಿ ಕೇಳುವ ಪ್ರಶ್ನೆ", "ಪ್ರತಿ ದಿನ", "ದಿನಗಳಿಲ್ಲ", "ಇನ್ನೂ ಜ್ಞಾಪನೆಗಳಿಲ್ಲ. ಸೇರಿಸಲು + ಒತ್ತಿರಿ.",
                    "ಸಕ್ರಿಯ ಜ್ಞಾಪನೆಗಳಿಲ್ಲ.", "ಜ್ಞಾಪನೆಗಳನ್ನು ಮರಳಿ ಪಡೆಯಿರಿ", "ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ", "ಬೇರೆ ಸಂಖ್ಯೆ ಬಳಸಿ",
                    "ಮೊಬೈಲ್ ಮೂಲಕ ಸೈನ್ ಇನ್", "ನಿರ್ವಾಹಕರು ನೀಡಿದ ಸಂಖ್ಯೆಯನ್ನು ಬಳಸಿ.", "ದಯವಿಟ್ಟು ಕಾಯಿರಿ…",
                    "OTP ಮತ್ತೆ ಕಳುಹಿಸಿ", "OTP ಕಳುಹಿಸಿ", "ನಿಮ್ಮ ಜ್ಞಾಪನೆಗಳು. ನಿಮ್ಮ ನಿಗದಿತ ಕರೆಗಳು.", "ಜ್ಞಾಪನೆ ಕರೆ",
                    "ತಿರಸ್ಕರಿಸಿ", "ಉತ್ತರಿಸಿ", "ತಿರಸ್ಕರಿಸಲು ಸ್ವೈಪ್ ಮಾಡಿ", "ಉತ್ತರಿಸಲು ಸ್ವೈಪ್ ಮಾಡಿ",
                    "ತೆಗೆದುಕೊಂಡೆ", "ಇನ್ನೂ ತೆಗೆದುಕೊಂಡಿಲ್ಲ", "ನಂತರ ನೆನಪಿಸು", "ಸ್ಪೀಕರ್ ಆಫ್ ಮಾಡಿ", "ಸ್ಪೀಕರ್ ಆನ್ ಮಾಡಿ",
                    "ನಿರ್ವಾಹಕರಿಗೆ ಸಂಪರ್ಕಿಸಿ", "ಫೋನ್ ಸಂಪರ್ಕಿಸಿ", "ಸಮಯ ಆಯ್ಕೆ ಮಾಡಿ", "ಇಂದಿನ ಮುಂದಿನ ಸಮಯವನ್ನು ಆಯ್ಕೆ ಮಾಡಿ.", "ಇಂದಿಗೆ ಬೇಡ"},
            {"ഭാഷ", "ലോഗ് ഔട്ട്", "ഓർമ്മപ്പെടുത്തൽ ചേർക്കുക", "ഓർമ്മപ്പെടുത്തൽ തിരുത്തുക", "മാറ്റങ്ങൾ സേവ് ചെയ്യുക",
                    "നീക്കുക", "റദ്ദാക്കുക", "വിഭാഗം", "മരുന്ന്", "സപ്ലിമെന്റ്", "ഭക്ഷണം", "പാനീയം",
                    "വ്യായാമം", "അപ്പോയിന്റ്മെന്റ്", "ബിൽ അല്ലെങ്കിൽ പണമടയ്ക്കൽ", "ജോലി", "ഉണർത്തൽ", "കസ്റ്റം",
                    "മരുന്നിന്റെ പേര്", "ഓർമ്മപ്പെടുത്തലിന്റെ പേര്", "കോൾ സമയം", "ആവർത്തിക്കുന്ന ദിവസങ്ങൾ", "ഭക്ഷണ ഓർമ്മപ്പെടുത്തൽ",
                    "കോൾ സംഭാഷണം", "ചോദ്യങ്ങളും ഉത്തരങ്ങളും തിരുത്തുക", "ചോദ്യം ചേർക്കുക", "ചോദ്യം തിരുത്തുക", "ചോദ്യം സേവ് ചെയ്യുക",
                    "ചിട്ടി ചോദിക്കുന്ന ചോദ്യം", "എല്ലാ ദിവസവും", "ദിവസങ്ങളില്ല", "ഇനിയും ഓർമ്മപ്പെടുത്തലുകളില്ല. ചേർക്കാൻ + അമർത്തുക.",
                    "സജീവ ഓർമ്മപ്പെടുത്തലുകളില്ല.", "ഓർമ്മപ്പെടുത്തലുകൾ വീണ്ടെടുക്കുക", "വീണ്ടും ശ്രമിക്കുക", "മറ്റൊരു നമ്പർ ഉപയോഗിക്കുക",
                    "മൊബൈൽ ഉപയോഗിച്ച് സൈൻ ഇൻ", "അഡ്മിൻ നൽകിയ നമ്പർ ഉപയോഗിക്കുക.", "ദയവായി കാത്തിരിക്കുക…",
                    "OTP വീണ്ടും അയയ്ക്കുക", "OTP അയയ്ക്കുക", "നിങ്ങളുടെ ഓർമ്മപ്പെടുത്തലുകൾ. നിശ്ചയിച്ച കോളുകൾ.", "ഓർമ്മപ്പെടുത്തൽ കോൾ",
                    "നിരസിക്കുക", "ഉത്തരം നൽകുക", "നിരസിക്കാൻ സ്വൈപ്പ് ചെയ്യുക", "ഉത്തരം നൽകാൻ സ്വൈപ്പ് ചെയ്യുക",
                    "കഴിച്ചു", "ഇനിയും കഴിച്ചില്ല", "പിന്നീട് ഓർമ്മിപ്പിക്കൂ", "സ്പീക്കർ ഓഫ് ചെയ്യുക", "സ്പീക്കർ ഓൺ ചെയ്യുക",
                    "അഡ്മിനുമായി ബന്ധിപ്പിക്കുക", "ഫോൺ ബന്ധിപ്പിക്കുക", "സമയം തിരഞ്ഞെടുക്കുക", "ഇന്നത്തെ ശേഷമുള്ള സമയം തിരഞ്ഞെടുക്കുക.", "ഇന്നേക്ക് വേണ്ട"}
    };

    private static final String[][] AGENDA_UI = {
            {"Today", "ఈ రోజు", "आज", "இன்று", "ಇಂದು", "ഇന്ന്"},
            {"Your reminders", "మీ రిమైండర్లు", "आपके रिमाइंडर", "உங்கள் நினைவூட்டல்கள்", "ನಿಮ್ಮ ಜ್ಞಾಪನೆಗಳು", "നിങ്ങളുടെ ഓർമ്മപ്പെടുത്തലുകൾ"},
            {"All reminders", "అన్ని రిమైండర్లు", "सभी रिमाइंडर", "எல்லா நினைவூட்டல்களும்", "ಎಲ್ಲಾ ಜ್ಞಾಪನೆಗಳು", "എല്ലാ ഓർമ്മപ്പെടുത്തലുകളും"},
            {"Today only", "ఈ రోజు మాత్రమే", "सिर्फ आज", "இன்று மட்டும்", "ಇಂದು ಮಾತ್ರ", "ഇന്ന് മാത്രം"},
            {"Next up", "తర్వాత", "अगला", "அடுத்து", "ಮುಂದಿನದು", "അടുത്തത്"},
            {"At your pace", "మీ సమయానికి", "अपने समय पर", "உங்கள் நேரத்தில்", "ನಿಮ್ಮ ಸಮಯದಲ್ಲಿ", "നിങ്ങളുടെ സമയത്ത്"},
            {"No more scheduled today", "ఈ రోజు ఇంకేమీ లేవు", "आज और कोई रिमाइंडर नहीं", "இன்று வேறு நினைவூட்டல் இல்லை", "ಇಂದು ಇನ್ನಾವುದೂ ಇಲ್ಲ", "ഇന്ന് ഇനി ഒന്നും നിശ്ചയിച്ചിട്ടില്ല"},
            {"Your agenda is always here below.", "మీ రిమైండర్లు కింద ఉన్నాయి.", "आपके रिमाइंडर नीचे हैं।", "உங்கள் நினைவூட்டல்கள் கீழே உள்ளன.", "ನಿಮ್ಮ ಜ್ಞಾಪನೆಗಳು ಕೆಳಗಿವೆ.", "നിങ്ങളുടെ ഓർമ്മപ്പെടുത്തലുകൾ താഴെയുണ്ട്."},
            {"%1$d of %2$d completed", "%2$d లో %1$d పూర్తయ్యాయి", "%2$d में से %1$d पूरे", "%2$d இல் %1$d முடிந்தது", "%2$d ರಲ್ಲಿ %1$d ಪೂರ್ಣಗೊಂಡಿವೆ", "%2$d ൽ %1$d പൂർത്തിയായി"},
            {"Read & respond", "చదివి స్పందించండి", "पढ़ें और जवाब दें", "படித்து பதிலளிக்கவும்", "ಓದಿ ಉತ್ತರಿಸಿ", "വായിച്ച് മറുപടി നൽകുക"},
            {"Answer & listen", "కాల్ తీసుకుని వినండి", "कॉल उठाएँ और सुनें", "அழைப்பை ஏற்று கேளுங்கள்", "ಕರೆ ಸ್ವೀಕರಿಸಿ ಆಲಿಸಿ", "കോൾ എടുത്ത് കേൾക്കുക"},
            {"Language and account", "భాష, ఖాతా", "भाषा और खाता", "மொழியும் கணக்கும்", "ಭಾಷೆ ಮತ್ತು ಖಾತೆ", "ഭാഷയും അക്കൗണ്ടും"},
            {"No reminders scheduled today.", "ఈ రోజు రిమైండర్లు లేవు.", "आज कोई रिमाइंडर नहीं है।", "இன்று நினைவூட்டல்கள் இல்லை.", "ಇಂದು ಜ್ಞಾಪನೆಗಳಿಲ್ಲ.", "ഇന്ന് ഓർമ്മപ്പെടുത്തലുകൾ ഇല്ല."},
            {"A calmer way to remember.", "ప్రశాంతంగా గుర్తుంచుకోండి.", "याद रखने का आसान तरीका।", "அமைதியாக நினைவில் கொள்ளுங்கள்.", "ನೆನಪಿಡಲು ಸುಲಭ ಮಾರ್ಗ.", "ഓർത്തിരിക്കാൻ എളുപ്പവഴി."},
            {"Keep this on today's list", "ఈ రోజు జాబితాలో ఉంచండి", "आज की सूची में रखें", "இன்றைய பட்டியலில் வைத்திரு", "ಇಂದಿನ ಪಟ್ಟಿಯಲ್ಲಿ ಇರಿಸಿ", "ഇന്നത്തെ പട്ടികയിൽ നിലനിർത്തുക"},
            {"Mark today's reminder done", "ఈ రోజు పూర్తయినట్లు గుర్తించండి", "आज का रिमाइंडर पूरा करें", "இன்றைய நினைவூட்டலை முடித்ததாகக் குறி", "ಇಂದಿನ ಜ್ಞಾಪನೆ ಪೂರ್ಣಗೊಳಿಸಿ", "ഇന്നത്തെ ഓർമ്മപ്പെടുത്തൽ പൂർത്തിയാക്കുക"},
            {"Resume on the next scheduled day", "తర్వాతి షెడ్యూల్ రోజున తిరిగి వస్తుంది", "अगले तय दिन फिर याद दिलाएँ", "அடுத்த திட்டமிட்ட நாளில் தொடரும்", "ಮುಂದಿನ ನಿಗದಿತ ದಿನ ಮುಂದುವರಿಸಿ", "അടുത്ത നിശ്ചിത ദിവസം തുടരുക"},
            {"Reminder", "రిమైండర్", "रिमाइंडर", "நினைவூட்டல்", "ಜ್ಞಾಪನೆ", "ഓർമ്മപ്പെടുത്തൽ"},
            {"Call", "కాల్", "कॉल", "அழைப்பு", "ಕರೆ", "കോൾ"},
            {"Today's status", "ఈ రోజు స్థితి", "आज की स्थिति", "இன்றைய நிலை", "ಇಂದಿನ ಸ್ಥಿತಿ", "ഇന്നത്തെ നില"},
            {"Applies to today only", "ఈ రోజుకు మాత్రమే", "सिर्फ आज के लिए", "இன்றைக்கு மட்டும்", "ಇಂದಿಗೆ ಮಾತ್ರ", "ഇന്നത്തേക്ക് മാത്രം"},
            {"Time & mode", "సమయం, విధానం", "समय और तरीका", "நேரமும் முறையும்", "ಸಮಯ ಮತ್ತು ವಿಧಾನ", "സമയവും രീതിയും"},
            {"Questions & answers", "ప్రశ్నలు, సమాధానాలు", "सवाल और जवाब", "கேள்விகளும் பதில்களும்", "ಪ್ರಶ್ನೆಗಳು ಮತ್ತು ಉತ್ತರಗಳು", "ചോദ്യങ്ങളും ഉത്തരങ്ങളും"}
    };

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

    static String title(String code) {
        switch (normalize(code)) {
            case "en": return "Remind me";
            case "hi": return "याद दिलाओ";
            case "ta": return "நினைவூட்டு";
            case "kn": return "ನೆನಪಿಸು";
            case "ml": return "ഓർമ്മിപ്പിക്കൂ";
            default: return "గుర్తు చేయి";
        }
    }

    static String caller(String code) {
        switch (normalize(code)) {
            case "en": return "Chitti"; case "hi": return "चिट्टी"; case "ta": return "சிட்டி";
            case "kn": return "ಚಿಟ್ಟಿ"; case "ml": return "ചിട്ടി"; default: return "చిట్టి";
        }
    }

    static Locale locale(String code) { return new Locale(normalize(code), "IN"); }

    static String timePeriod(String code, int hour, int minute) {
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
        String normalized = normalize(code);
        int language = "en".equals(normalized) ? 0 : "te".equals(normalized) ? 1
                : "hi".equals(normalized) ? 2 : "ta".equals(normalized) ? 3
                : "kn".equals(normalized) ? 4 : 5;
        return names[language][period];
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

    static String ui(Context context, String english) { return ui(current(context), english); }

    static String ui(String code, String english) {
        String normalized = normalize(code);
        if (english == null || "en".equals(normalized)) return english;
        if ("Hang up".equals(english)) {
            switch (normalized) {
                case "te": return "కాల్ ముగించు"; case "hi": return "कॉल काटें";
                case "ta": return "அழைப்பை முடி"; case "kn": return "ಕರೆ ಮುಗಿಸಿ";
                default: return "കോൾ അവസാനിപ്പിക്കുക";
            }
        }
        if ("Okay".equals(english)) {
            switch (normalized) {
                case "te": return "సరే"; case "hi": return "ठीक है";
                case "ta": return "சரி"; case "kn": return "ಸರಿ"; default: return "ശരി";
            }
        }
        int language = "te".equals(normalized) ? 0 : "hi".equals(normalized) ? 1
                : "ta".equals(normalized) ? 2 : "kn".equals(normalized) ? 3 : 4;
        for (String[] text : AGENDA_UI) {
            if (text[0].equals(english)) return text[language + 1];
        }
        for (int index = 0; index < UI_KEYS.length; index++) {
            if (UI_KEYS[index].equals(english)) return UI_VALUES[language][index];
        }
        return english;
    }

    static String category(String code, String category) {
        String english;
        switch (category == null ? "custom" : category) {
            case "medicine": english = "Medicine"; break; case "supplement": english = "Supplement"; break;
            case "meal": english = "Meal"; break; case "drink": english = "Drink"; break;
            case "exercise": english = "Exercise"; break; case "appointment": english = "Appointment"; break;
            case "payment": english = "Bill or payment"; break; case "task": english = "Task"; break;
            case "wake_up": english = "Wake-up"; break; default: english = "Custom";
        }
        return ui(code, english);
    }
}
