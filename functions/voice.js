"use strict";

const crypto = require("crypto");

const VOICE_NAME = "te-IN-Chirp3-HD-Leda";
const GOODBYE_VOICE_NAME = "en-US-Chirp3-HD-Leda";
const LANGUAGES = new Set(["en", "te", "hi", "ta", "kn", "ml"]);

function language(value) {
  return LANGUAGES.has(value) ? value : "te";
}

function languageCode(value) {
  return `${language(value)}-IN`;
}

function voiceName(value) {
  return `${languageCode(value)}-Chirp3-HD-Leda`;
}

function detectLanguage(text, fallback = "te") {
  const counts = {en: 0, te: 0, hi: 0, ta: 0, kn: 0, ml: 0};
  const source = typeof text === "string"
    ? text.replaceAll("[Name]", "").replaceAll("[Reminder title]", "")
      .replaceAll("[Reminder]", "").replaceAll("[category]", "")
      .replaceAll("[Category]", "") : "";
  for (const character of source) {
    const value = character.codePointAt(0);
    if (value >= 0x0c00 && value <= 0x0c7f) counts.te += 1;
    else if (value >= 0x0900 && value <= 0x097f) counts.hi += 1;
    else if (value >= 0x0b80 && value <= 0x0bff) counts.ta += 1;
    else if (value >= 0x0c80 && value <= 0x0cff) counts.kn += 1;
    else if (value >= 0x0d00 && value <= 0x0d7f) counts.ml += 1;
    else if (/[A-Za-z]/.test(character)) counts.en += 1;
  }
  const best = Object.entries(counts).sort((left, right) => right[1] - left[1])[0];
  return best && best[1] > 0 ? best[0] : language(fallback);
}

function clean(value, maximum, fallback) {
  if (typeof value !== "string") return fallback;
  const result = value.replace(/[\x00-\x1f\x7f]/g, " ").trim().replace(/\s+/g, " ");
  if (!result) return fallback;
  return result.length <= maximum ? result : result.substring(0, maximum).trim();
}

function addressName(value, lang = "te") {
  const fallbacks = {en: "there", te: "అండి", hi: "जी", ta: "ஐயா", kn: "ಅವರೇ", ml: "ചേട്ടാ"};
  return clean(value, 60, fallbacks[language(lang)]);
}

function medicineName(value, lang = "te") {
  const fallbacks = {en: "medicine", te: "మందు", hi: "दवा", ta: "மருந்து", kn: "ಔಷಧಿ", ml: "മരുന്ന്"};
  const result = clean(value, 80, fallbacks[language(lang)])
    .replace(/(?:[\s\u200B-\u200D\uFEFF]*tablet[\s\u200B-\u200D\uFEFF]*)+$/i, "")
    .replace(/(?:[\s\u200B-\u200D\uFEFF]*(?:టాబ్లెట్|गोली|மாத்திரை|ಮಾತ್ರೆ|ഗുളിക)[\s\u200B-\u200D\uFEFF]*)+$/, "").trim();
  return result || fallbacks[language(lang)];
}

function categoryName(value, lang = "te") {
  const key = typeof value === "string" ? value.trim().toLowerCase() : "custom";
  const names = {
    en: {medicine: "medicine", supplement: "supplement", meal: "meal", drink: "drink",
      exercise: "exercise", appointment: "appointment", payment: "bill or payment",
      task: "task", wake_up: "wake-up reminder", custom: "reminder"},
    te: {medicine: "మందు", supplement: "సప్లిమెంట్", meal: "భోజనం", drink: "పానీయం",
      exercise: "వ్యాయామం", appointment: "అపాయింట్‌మెంట్", payment: "బిల్లు లేదా చెల్లింపు",
      task: "పని", wake_up: "నిద్రలేవడం", custom: "రిమైండర్"},
    hi: {medicine: "दवा", supplement: "सप्लीमेंट", meal: "भोजन", drink: "पेय",
      exercise: "व्यायाम", appointment: "अपॉइंटमेंट", payment: "बिल या भुगतान",
      task: "काम", wake_up: "जागने का रिमाइंडर", custom: "रिमाइंडर"},
    ta: {medicine: "மருந்து", supplement: "ஊட்டச்சத்து மாத்திரை", meal: "உணவு", drink: "பானம்",
      exercise: "உடற்பயிற்சி", appointment: "சந்திப்பு", payment: "பில் அல்லது கட்டணம்",
      task: "பணி", wake_up: "எழுந்திருக்கும் நினைவூட்டல்", custom: "நினைவூட்டல்"},
    kn: {medicine: "ಔಷಧಿ", supplement: "ಪೂರಕ ಮಾತ್ರೆ", meal: "ಊಟ", drink: "ಪಾನೀಯ",
      exercise: "ವ್ಯಾಯಾಮ", appointment: "ಅಪಾಯಿಂಟ್‌ಮೆಂಟ್", payment: "ಬಿಲ್ ಅಥವಾ ಪಾವತಿ",
      task: "ಕೆಲಸ", wake_up: "ಎಚ್ಚರಗೊಳ್ಳುವ ಜ್ಞಾಪನೆ", custom: "ಜ್ಞಾಪನೆ"},
    ml: {medicine: "മരുന്ന്", supplement: "സപ്ലിമെന്റ്", meal: "ഭക്ഷണം", drink: "പാനീയം",
      exercise: "വ്യായാമം", appointment: "അപ്പോയിന്റ്മെന്റ്", payment: "ബിൽ അല്ലെങ്കിൽ പേയ്മെന്റ്",
      task: "ജോലി", wake_up: "ഉണരാനുള്ള ഓർമ്മപ്പെടുത്തൽ", custom: "ഓർമ്മപ്പെടുത്തൽ"},
  };
  const localized = names[language(lang)];
  return localized[key] || localized.custom;
}

function customText(template, memberName, reminderLabel, lang = "te", category = "custom") {
  if (typeof template !== "string") return "";
  const categoryValue = categoryName(category, lang);
  return clean(template, 300, "")
    .replace(/\[Name\]/g, addressName(memberName, lang))
    .replace(/\[Reminder title\]/gi, clean(reminderLabel, 80, medicineName("", lang)))
    .replace(/\[Reminder\]/g, clean(reminderLabel, 80, medicineName("", lang)))
    .replace(/\[category\]/g, categoryValue)
    .replace(/\[Category\]/g, categoryValue.charAt(0).toUpperCase() + categoryValue.slice(1));
}

function duration(minutes, lang = "te") {
  if (language(lang) !== "te") return String(Math.max(1, Number(minutes) || 1));
  if (Number(minutes) === 2) return "రెండు";
  if (Number(minutes) === 5) return "ఐదు";
  if (Number(minutes) === 15) return "పదిహేను";
  if (Number(minutes) === 30) return "ముప్పై";
  if (Number(minutes) === 45) return "నలభై ఐదు";
  if (Number(minutes) === 60) return "అరవై";
  return String(Math.max(1, Number(minutes) || 1));
}

function mealQuestion(memberName, label, morning, minutes, lang = "te") {
  const code = language(lang);
  const name = addressName(memberName, code);
  const item = medicineName(label, code);
  const time = duration(minutes, code);
  if (code === "en") return `Hello ${name}! Have you had ${morning ? "breakfast" : "your meal"}? If not, please eat now. You need to take your ${item} tablet in ${time} minutes. I’ll call you again in ${time} minutes to remind you. Bye!`;
  if (code === "hi") return `नमस्ते ${name}! क्या आपने ${morning ? "नाश्ता" : "खाना"} खा लिया? अगर नहीं, तो अभी खा लीजिए। ${time} मिनट बाद ${item} की गोली लेनी है। मैं ${time} मिनट बाद फिर कॉल करके याद दिलाऊँगी। बाय!`;
  if (code === "ta") return `வணக்கம் ${name}! ${morning ? "காலை உணவு" : "சாப்பாடு"} சாப்பிட்டீர்களா? இல்லையெனில் இப்போது சாப்பிடுங்கள். இன்னும் ${time} நிமிடங்களில் ${item} மாத்திரை எடுக்க வேண்டும். ${time} நிமிடங்களில் மீண்டும் அழைத்து நினைவூட்டுகிறேன். பை!`;
  if (code === "kn") return `ನಮಸ್ಕಾರ ${name}! ${morning ? "ತಿಂಡಿ" : "ಊಟ"} ಮಾಡಿದ್ದೀರಾ? ಇಲ್ಲದಿದ್ದರೆ ಈಗಲೇ ಮಾಡಿ. ಇನ್ನೂ ${time} ನಿಮಿಷಗಳಲ್ಲಿ ${item} ಮಾತ್ರೆ ತೆಗೆದುಕೊಳ್ಳಬೇಕು. ${time} ನಿಮಿಷಗಳಲ್ಲಿ ಮತ್ತೆ ಕರೆ ಮಾಡಿ ನೆನಪಿಸುತ್ತೇನೆ. ಬೈ!`;
  if (code === "ml") return `നമസ്കാരം ${name}! ${morning ? "പ്രഭാതഭക്ഷണം" : "ഭക്ഷണം"} കഴിച്ചോ? ഇല്ലെങ്കിൽ ഇപ്പോൾ കഴിക്കൂ. ഇനി ${time} മിനിറ്റിൽ ${item} ഗുളിക കഴിക്കണം. ${time} മിനിറ്റിന് ശേഷം വീണ്ടും വിളിച്ച് ഓർമ്മിപ്പിക്കാം. ബൈ!`;
  const meal = morning ? "టిఫిన్" : "భోజనం";
  return `హలో ${name}! ${meal} చేశారా? ఇంకా చేయకపోతే ఇప్పుడే చేయండి. `
    + `ఇంకో ${time} నిమిషాల్లో మీరు ${item} టాబ్లెట్ వేసుకోవాలి. `
    + `నేను మరో ${time} నిమిషాల్లో మీకు కాల్ చేసి గుర్తు చేస్తాను. Bye`;
}

function mealCompleted(label) {
  return `అయితే ఇంకో ముప్పై నిమిషాల్లో మీరు ${medicineName(label)} వేసుకోవాలి. `
    + "నేను ఇంకో ముప్పై నిమిషాల్లో మీకు కాల్ చేసి గుర్తు చేస్తాను. అప్పుడు వేసుకోండి. Bye!";
}

function mealNotCompleted(label) {
  return `అయితే త్వరగా వెళ్లి భోజనం చేయండి. ఇంకో ముప్పై నిమిషాల్లో మీరు ${medicineName(label)} వేసుకోవాలి.`;
}

function mealAcknowledged() {
  return "సరే. నేను ఇంకో ముప్పై నిమిషాల్లో మీకు కాల్ చేసి గుర్తు చేస్తాను. అప్పుడు వేసుకోండి. Bye!";
}

function medicineQuestion(memberName, label, lang = "te") {
  const code = language(lang); const name = addressName(memberName, code); const item = medicineName(label, code);
  if (code === "en") return `Hello ${name}! Have you taken your ${item} tablet?`;
  if (code === "hi") return `नमस्ते ${name}! क्या आपने ${item} की गोली ले ली?`;
  if (code === "ta") return `வணக்கம் ${name}! ${item} மாத்திரை எடுத்துக்கொண்டீர்களா?`;
  if (code === "kn") return `ನಮಸ್ಕಾರ ${name}! ${item} ಮಾತ್ರೆ ತೆಗೆದುಕೊಂಡಿದ್ದೀರಾ?`;
  if (code === "ml") return `നമസ്കാരം ${name}! ${item} ഗുളിക കഴിച്ചോ?`;
  return `హలో ${name}! ${item} టాబ్లెట్ వేసుకున్నారా?`;
}

function medicineTaken(memberName, lang = "te") {
  const code = language(lang); const name = addressName(memberName, code);
  if (code === "en") return `Great, ${name}! Bye!`;
  if (code === "hi") return `बहुत बढ़िया, ${name}! फिर मिलते हैं। बाय!`;
  if (code === "ta") return `அருமை, ${name}! பிறகு பார்க்கலாம். பை!`;
  if (code === "kn") return `ತುಂಬಾ ಚೆನ್ನಾಗಿದೆ, ${name}! ಮತ್ತೆ ಸಿಗೋಣ. ಬೈ!`;
  if (code === "ml") return `സൂപ്പർ, ${name}! പിന്നെ കാണാം. ബൈ!`;
  return `సూపర్ ${name}! ఉంటాను, Bye!`;
}

function confirmationQuestion(memberName, label, category, lang = "te") {
  const code = language(lang); const name = addressName(memberName, code);
  const item = clean(label, 80, medicineName("", code));
  const take = category === "medicine" || category === "supplement";
  const have = category === "meal" || category === "drink";
  if (code === "hi") return `नमस्ते ${name}! क्या आपने ${item}${take || have ? " ले लिया?" : " पूरा कर लिया?"}`;
  if (code === "ta") return `வணக்கம் ${name}! ${item}${take ? " எடுத்துக்கொண்டீர்களா?" : have ? " சாப்பிட்டீர்களா?" : " முடித்துவிட்டீர்களா?"}`;
  if (code === "kn") return `ನಮಸ್ಕಾರ ${name}! ${item}${take ? " ತೆಗೆದುಕೊಂಡಿದ್ದೀರಾ?" : have ? " ಸೇವಿಸಿದ್ದೀರಾ?" : " ಮುಗಿಸಿದ್ದೀರಾ?"}`;
  if (code === "ml") return `നമസ്കാരം ${name}! ${item}${take || have ? " കഴിച്ചോ?" : " പൂർത്തിയാക്കിയോ?"}`;
  if (code === "te") return `హలో ${name}! ${item}${take ? " వేసుకున్నారా?" : have ? " తీసుకున్నారా?" : " పూర్తి చేశారా?"}`;
  return `Hi ${name}! ${take ? "Have you taken " : have ? "Did you have " : "Did you complete "}${item}?`;
}

function confirmationNotTaken(lang = "te") {
  const code = language(lang);
  if (code === "hi") return "ठीक है। अभी कर लीजिए। मैं पाँच मिनट बाद फिर कॉल करूँगी। बाय!";
  if (code === "ta") return "சரி. இப்போது செய்துவிடுங்கள். ஐந்து நிமிடங்களில் மீண்டும் அழைக்கிறேன். பை!";
  if (code === "kn") return "ಸರಿ. ಈಗಲೇ ಮಾಡಿ. ಐದು ನಿಮಿಷಗಳ ನಂತರ ಮತ್ತೆ ಕರೆ ಮಾಡುತ್ತೇನೆ. ಬೈ!";
  if (code === "ml") return "ശരി. ഇപ്പോൾ ചെയ്യൂ. അഞ്ച് മിനിറ്റിന് ശേഷം വീണ്ടും വിളിക്കാം. ബൈ!";
  if (code === "te") return "సరే. ఇప్పుడే పూర్తి చేయండి. ఐదు నిమిషాల తర్వాత మళ్లీ కాల్ చేస్తాను. Bye!";
  return "Okay. Please do it now. I’ll call again in 5 minutes. Bye!";
}

function escapeSsml(value) {
  return value.replace(/&/g, "&amp;").replace(/</g, "&lt;")
    .replace(/>/g, "&gt;").replace(/\"/g, "&quot;").replace(/'/g, "&apos;");
}

function synthesisInput(text, lang = "te") {
  if (language(lang) !== "te") return {text};
  const quickReminderLabel = "తర్వాత గుర్తుచేయి";
  if (!text.includes("Bye") && !text.includes(quickReminderLabel)) return { text };
  const goodbye = `<voice name="${GOODBYE_VOICE_NAME}">bye</voice>`;
  const spoken = text.split("Bye").map(escapeSsml).join(goodbye)
    .replace(quickReminderLabel,
      `<prosody rate="fast">${quickReminderLabel}</prosody>`);
  return { ssml: `<speak>${spoken}</speak>` };
}

function medicineNotTaken(lang = "te") {
  const code = language(lang);
  if (code === "en") return "Please take the tablet now. I’ll stay on the line. After taking it, tap “Taken”. If you need more time, tap “Remind me later”.";
  if (code === "hi") return "अभी जाकर गोली ले लीजिए। मैं लाइन पर रहूँगी। लेने के बाद “ले लिया” बटन दबाएँ। और समय चाहिए तो “बाद में याद दिलाओ” बटन दबाएँ।";
  if (code === "ta") return "இப்போது மாத்திரையை எடுத்துக்கொள்ளுங்கள். நான் இணைப்பிலேயே இருப்பேன். எடுத்த பிறகு “எடுத்துவிட்டேன்” பொத்தானை அழுத்துங்கள். இன்னும் நேரம் வேண்டுமெனில் “பிறகு நினைவூட்டு” பொத்தானை அழுத்துங்கள்.";
  if (code === "kn") return "ಈಗ ಮಾತ್ರೆ ತೆಗೆದುಕೊಳ್ಳಿ. ನಾನು ಲೈನ್‌ನಲ್ಲೇ ಇರುತ್ತೇನೆ. ತೆಗೆದುಕೊಂಡ ನಂತರ “ತೆಗೆದುಕೊಂಡೆ” ಬಟನ್ ಒತ್ತಿರಿ. ಇನ್ನಷ್ಟು ಸಮಯ ಬೇಕಾದರೆ “ನಂತರ ನೆನಪಿಸು” ಬಟನ್ ಒತ್ತಿರಿ.";
  if (code === "ml") return "ഇപ്പോൾ ഗുളിക കഴിക്കൂ. ഞാൻ ലൈനിൽ തന്നെ ഉണ്ടാകും. കഴിച്ച ശേഷം “കഴിച്ചു” ബട്ടൺ അമർത്തുക. കൂടുതൽ സമയം വേണമെങ്കിൽ “പിന്നീട് ഓർമ്മിപ്പിക്കൂ” ബട്ടൺ അമർത്തുക.";
  return "అయితే త్వరగా వెళ్లి టాబ్లెట్ వేసుకోండి. నేను లైన్‌లోనే ఉంటాను. "
    + "వేసుకుని వచ్చాక, “వేసుకున్నా” బటన్ నొక్కండి. లేదా ఇంకా సమయం కావాలంటే, "
    + "“తర్వాత గుర్తుచేయి” బటన్ నొక్కండి.";
}

function remindLater() {
  return "సరే. మీరు చెప్పిన సమయం తర్వాత మళ్లీ కాల్ చేసి గుర్తు చేస్తాను. Bye!";
}

function reminderDelayQuestion(lang = "te") {
  const values = {en: "How many minutes later should I remind you again?", hi: "मैं आपको कितने मिनट बाद फिर याद दिलाऊँ?", ta: "எத்தனை நிமிடங்கள் கழித்து மீண்டும் நினைவூட்ட வேண்டும்?", kn: "ಎಷ್ಟು ನಿಮಿಷಗಳ ನಂತರ ಮತ್ತೆ ನೆನಪಿಸಬೇಕು?", ml: "എത്ര മിനിറ്റിന് ശേഷം വീണ്ടും ഓർമ്മിപ്പിക്കണം?"};
  if (values[language(lang)]) return values[language(lang)];
  return "ఎన్ని నిమిషాల తర్వాత మళ్లీ గుర్తు చేయాలి?";
}

function reminderDelayed(minutes, lang = "te") {
  const code = language(lang); const value = duration(minutes, code);
  if (code === "en") return `Okay. I’ll call again in ${value} minutes to remind you. Bye!`;
  if (code === "hi") return `ठीक है। मैं ${value} मिनट बाद फिर कॉल करके याद दिलाऊँगी। बाय!`;
  if (code === "ta") return `சரி. ${value} நிமிடங்கள் கழித்து மீண்டும் அழைத்து நினைவூட்டுகிறேன். பை!`;
  if (code === "kn") return `ಸರಿ. ${value} ನಿಮಿಷಗಳ ನಂತರ ಮತ್ತೆ ಕರೆ ಮಾಡಿ ನೆನಪಿಸುತ್ತೇನೆ. ಬೈ!`;
  if (code === "ml") return `ശരി. ${value} മിനിറ്റിന് ശേഷം വീണ്ടും വിളിച്ച് ഓർമ്മിപ്പിക്കാം. ബൈ!`;
  return `సరే. ${value} నిమిషాల తర్వాత మళ్లీ కాల్ చేసి గుర్తు చేస్తాను. Bye!`;
}

function medicineFinished() {
  return "సరే. మందులు అయిపోయాయని ఇంట్లో వాళ్లకు వెంటనే చెప్పండి. "
    + "డాక్టర్ లేదా ఫార్మసిస్ట్ సలహా లేకుండా వేరే మందు వేసుకోకండి. Bye!";
}

function healthIssue() {
  return "సరే. మీకు ఆరోగ్య సమస్య ఉంటే వెంటనే ఇంట్లో వాళ్లకు చెప్పండి. "
    + "అవసరమైతే డాక్టర్‌ను సంప్రదించండి. Bye!";
}

function chittiIdentity() {
  return "నా పేరు చిట్టి. మీకు మందు గుర్తు చేయడానికి కాల్ చేశాను.";
}

function voiceEntries(member) {
  const schedules = Array.isArray(member && member.schedules)
    ? member.schedules.filter((item) => item && item.enabled !== false).slice(0, 50)
    : [];
  if (schedules.length === 0) return [];
  const name = member && member.name;
  const entries = new Map();
  const add = (text, lang) => {
    if (!text) return;
    const code = language(lang);
    entries.set(`${code}\n${text}`, {text, language: code});
  };
  for (const schedule of schedules) {
    const authoredQuestions = Array.isArray(schedule.questions)
      ? schedule.questions.map((question) => [question && question.prompt,
        ...(Array.isArray(question && question.answers)
          ? question.answers.flatMap((answer) => [answer && answer.label, answer && answer.response]) : [])])
        .flat().filter(Boolean).join(" ") : "";
    const authored = [schedule.label, authoredQuestions].filter(Boolean).join(" ");
    // The reminder's authored content owns its call language. The app UI language
    // is a separate local preference and must never force new voice generation.
    const lang = language(detectLanguage(authored, schedule.language || "en"));
    const rawCategory = typeof schedule.category === "string"
      ? schedule.category.trim().toLowerCase()
      : typeof schedule.kind === "string"
        ? schedule.kind.trim().toLowerCase() : "medicine";
    const category = rawCategory || "medicine";
    const questions = Array.isArray(schedule.questions) ? schedule.questions.slice(0, 10) : [];
    if (category === "medicine" && questions.length === 0) {
      add(medicineQuestion(name, schedule.label, lang), lang);
      if (Number(schedule.preMinutes) > 0) {
        add(mealQuestion(name, schedule.label,
          Number(schedule.hour) < 12, Number(schedule.preMinutes), lang), lang);
      }
      add(medicineTaken(name, lang), lang);
      add(medicineNotTaken(lang), lang);
      add(reminderDelayQuestion(lang), lang);
      add(reminderDelayed(5, lang), lang);
      add(reminderDelayed(15, lang), lang);
      add(reminderDelayed(30, lang), lang);
      add(reminderDelayed(60, lang), lang);
    } else {
      if (category === "medicine" && Number(schedule.preMinutes) > 0) {
        add(mealQuestion(name, schedule.label,
          Number(schedule.hour) < 12, Number(schedule.preMinutes), lang), lang);
      }
      for (const question of questions) {
        const prompt = customText(question && question.prompt, name, schedule.label, lang, category);
        add(prompt, lang);
        const answers = Array.isArray(question && question.answers)
          ? question.answers.slice(0, 4) : [];
        for (const answer of answers) {
          const response = customText(answer && answer.response, name, schedule.label, lang, category);
          add(response, lang);
        }
      }
    }
    add(reminderDelayQuestion(lang), lang);
    for (const minutes of [5, 15, 30, 60]) add(reminderDelayed(minutes, lang), lang);
    if (Number(schedule.confirmationMinutes) > 0) {
      add(confirmationQuestion(name, schedule.label, category, lang), lang);
      add(medicineTaken(name, lang), lang);
      add(confirmationNotTaken(lang), lang);
      add(reminderDelayQuestion(lang), lang);
      for (const minutes of [5, 15, 30, 60]) add(reminderDelayed(minutes, lang), lang);
    }
  }
  return [...entries.values()];
}

function voiceTexts(member) {
  return voiceEntries(member).map((entry) => entry.text);
}

function clipId(text, lang = "te") {
  return crypto.createHash("sha256").update(`${voiceName(lang)}\n${text}`, "utf8").digest("hex");
}

function validMp3Buffer(audio) {
  if (!Buffer.isBuffer(audio) || audio.length < 256 || audio.length > 700000) return false;
  return (audio[0] === 0x49 && audio[1] === 0x44 && audio[2] === 0x33)
    || (audio[0] === 0xff && (audio[1] & 0xe0) === 0xe0);
}

function validClipDocument(value, expectedText, lang = "te") {
  if (!value || value.voice !== voiceName(lang) || value.text !== expectedText
      || value.mimeType !== "audio/mpeg" || typeof value.audioBase64 !== "string") return false;
  const encoded = value.audioBase64;
  // Buffer.from(base64) silently accepts malformed input, so validate its alphabet
  // and padding first. These limits mirror the Android client's on-disk checks.
  if (encoded.length < 300 || encoded.length > 950000 || encoded.length % 4 !== 0
      || !/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(encoded)) return false;
  return validMp3Buffer(Buffer.from(encoded, "base64"));
}

module.exports = {
  VOICE_NAME,
  GOODBYE_VOICE_NAME,
  language,
  languageCode,
  voiceName,
  detectLanguage,
  addressName,
  medicineName,
  categoryName,
  customText,
  mealQuestion,
  mealCompleted,
  mealNotCompleted,
  mealAcknowledged,
  medicineQuestion,
  medicineTaken,
  confirmationQuestion,
  confirmationNotTaken,
  medicineNotTaken,
  remindLater,
  reminderDelayQuestion,
  reminderDelayed,
  medicineFinished,
  healthIssue,
  chittiIdentity,
  synthesisInput,
  voiceEntries,
  voiceTexts,
  clipId,
  validMp3Buffer,
  validClipDocument,
};
