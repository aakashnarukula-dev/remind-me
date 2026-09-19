"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const voice = require("./voice");

test("builds the exact Telugu call scripts", () => {
  assert.equal(voice.mealQuestion("నాన్నా", "కొలెస్ట్రాల్", true, 30),
    "హలో నాన్నా! టిఫిన్ చేశారా? ఇంకా చేయకపోతే ఇప్పుడే చేయండి. "
      + "ఇంకో ముప్పై నిమిషాల్లో మీరు కొలెస్ట్రాల్ టాబ్లెట్ వేసుకోవాలి. "
      + "నేను మరో ముప్పై నిమిషాల్లో మీకు కాల్ చేసి గుర్తు చేస్తాను. Bye");
  assert.equal(voice.medicineQuestion("నాన్నా", "కొలెస్ట్రాల్ టాబ్లెట్"),
    "హలో నాన్నా! కొలెస్ట్రాల్ టాబ్లెట్ వేసుకున్నారా?");
  assert.equal(voice.medicineQuestion("నాన్నా", "టాబ్లెట్"),
    "హలో నాన్నా! మందు టాబ్లెట్ వేసుకున్నారా?");
  assert.equal(voice.medicineQuestion("నాన్నా", "టాబ్లెట్ టాబ్లెట్"),
    "హలో నాన్నా! మందు టాబ్లెట్ వేసుకున్నారా?");
  assert.equal(voice.medicineQuestion("నాన్నా", "tablet tablet"),
    "హలో నాన్నా! మందు టాబ్లెట్ వేసుకున్నారా?");
  assert.equal(voice.medicineQuestion("నాన్నా", "టాబ్లెట్\u200Cటాబ్లెట్"),
    "హలో నాన్నా! మందు టాబ్లెట్ వేసుకున్నారా?");
  assert.equal(voice.medicineTaken("నాన్నా"), "సూపర్ నాన్నా! ఉంటాను, Bye!");
  assert.equal(voice.medicineNotTaken(),
    "అయితే త్వరగా వెళ్లి టాబ్లెట్ వేసుకోండి. నేను లైన్‌లోనే ఉంటాను. "
      + "వేసుకుని వచ్చాక, “వేసుకున్నా” బటన్ నొక్కండి. లేదా ఇంకా సమయం కావాలంటే, "
      + "“తర్వాత గుర్తుచేయి” బటన్ నొక్కండి.");
  assert.match(voice.remindLater(), /మళ్లీ కాల్/);
  assert.match(voice.reminderDelayQuestion(), /ఎన్ని నిమిషాల/);
  assert.match(voice.reminderDelayed(5), /ఐదు/);
  assert.match(voice.medicineFinished(), /ఫార్మసిస్ట్/);
  assert.match(voice.healthIssue(), /డాక్టర్/);
  assert.equal(voice.chittiIdentity(), "నా పేరు చిట్టి. మీకు మందు గుర్తు చేయడానికి కాల్ చేశాను.");
});

test("uses the matching English voice only for Bye", () => {
  const input = voice.synthesisInput("సూపర్ A&B! ఉంటాను, Bye!");
  assert.match(input.ssml, /A&amp;B/);
  assert.match(input.ssml, /en-US-Chirp3-HD-Leda/);
  assert.match(input.ssml, />bye<\/voice>/);
});

test("speaks the remind-later label as one quick natural phrase", () => {
  const input = voice.synthesisInput(voice.medicineNotTaken());
  assert.match(input.ssml,
    /<prosody rate="fast">తర్వాత గుర్తుచేయి<\/prosody>/);
});

test("keeps each reminder on its own language and voice", () => {
  const entries = voice.voiceEntries({
    language: "en",
    name: "Aakash",
    schedules: [
      {category: "task", language: "te", label: "పని", enabled: true,
        questions: [{prompt: "హలో [Name]! [Reminder] గుర్తుందా?",
          answers: [{label: "సరే", response: "సరే. Bye!"}]}]},
      {category: "task", language: "en", label: "Pay bill", enabled: true,
        questions: [{prompt: "Hello [Name]! Remember to [Reminder].",
          answers: [{label: "Okay", response: "Okay. Bye!"}]}]},
    ],
  });
  assert.deepEqual(entries.map((entry) => entry.language), ["te", "te", "en", "en"]);
  assert.notEqual(voice.clipId(entries[0].text, "te"), voice.clipId(entries[0].text, "en"));
});

test("detects authored language for legacy reminders without a language field", () => {
  assert.equal(voice.detectLanguage("Hello Aakash"), "en");
  assert.equal(voice.detectLanguage("హలో ఆకాశ్"), "te");
  assert.equal(voice.detectLanguage("नमस्ते"), "hi");
  assert.equal(voice.detectLanguage("வணக்கம்"), "ta");
  assert.equal(voice.detectLanguage("ನಮಸ್ಕಾರ"), "kn");
  assert.equal(voice.detectLanguage("നമസ്കാരം"), "ml");
});

test("authored content overrides a stale app language", () => {
  const entries = voice.voiceEntries({
    name: "Aakash",
    schedules: [{
      category: "task",
      language: "te",
      label: "Pay electricity bill",
      enabled: true,
      questions: [{prompt: "Hello [Name], please [Reminder].",
        answers: [{label: "Okay", response: "Okay, bye!"}]}],
    }],
  });
  assert.equal(entries.every((entry) => entry.language === "en"), true);
});

test("deduplicates shared clips and includes meal branches only when enabled", () => {
  const medicineOnly = voice.voiceTexts({
    name: "నాన్నా",
    schedules: [{label: "బీపీ టాబ్లెట్", hour: 8, enabled: true, preMinutes: 0}],
  });
  assert.equal(medicineOnly.some((text) => text.includes("టిఫిన్ చేశారా")), false);
  const withMeals = voice.voiceTexts({
    name: "నాన్నా",
    schedules: [
      {label: "బీపీ టాబ్లెట్", hour: 8, enabled: true, preMinutes: 30},
      {label: "బీపీ టాబ్లెట్", hour: 8, enabled: true, preMinutes: 30},
    ],
  });
  assert.equal(new Set(withMeals).size, withMeals.length);
  assert.equal(withMeals.some((text) => text.includes("టిఫిన్ చేశారా")), true);
  assert.equal(withMeals.includes(voice.reminderDelayed(5)), true);
  assert.equal(withMeals.includes(voice.reminderDelayed(45)), false);
});

test("generates every custom reminder question and answer response", () => {
  const texts = voice.voiceTexts({
    name: "అమ్మా",
    schedules: [{
      label: "డాక్టర్ అపాయింట్మెంట్",
      category: "appointment",
      enabled: true,
      questions: [{
        prompt: "హలో [Name]! [Reminder] గుర్తుందా?",
        answers: [
          {label: "గుర్తుంది", response: "సరే [Name]. Bye!"},
          {label: "లేదు", response: "[Reminder] క్యాలెండర్‌లో చూడండి."},
        ],
      }],
    }],
  });
  assert.deepEqual(texts, [
    "హలో అమ్మా! డాక్టర్ అపాయింట్మెంట్ గుర్తుందా?",
    "సరే అమ్మా. Bye!",
    "డాక్టర్ అపాయింట్మెంట్ క్యాలెండర్‌లో చూడండి.",
  ]);
  assert.equal(texts.some((text) => text.includes("టాబ్లెట్")), false);
});

test("generates confirmation call clips only for enabled reminders", () => {
  const disabled = voice.voiceTexts({name: "Aakash", schedules: [{
    label: "Workout", category: "exercise", language: "en", enabled: true,
    confirmationMinutes: 0,
    questions: [{prompt: "Hi [Name]! Time for [Reminder title].",
      answers: [{label: "Okay", response: "Okay. Bye!"}]}],
  }]});
  assert.equal(disabled.includes("Hi Aakash! Did you complete Workout?"), false);

  const enabled = voice.voiceTexts({name: "Aakash", schedules: [{
    label: "Workout", category: "exercise", language: "en", enabled: true,
    confirmationMinutes: 10,
    questions: [{prompt: "Hi [Name]! Time for [Reminder title].",
      answers: [{label: "Okay", response: "Okay. Bye!"}]}],
  }]});
  assert.equal(enabled.includes("Hi Aakash! Did you complete Workout?"), true);
  assert.equal(enabled.includes("Okay. Please do it now. I’ll call again in 5 minutes. Bye!"), true);
  assert.equal(enabled.includes(voice.reminderDelayed(60, "en")), true);
});

test("reminder title placeholder follows later title edits", () => {
  assert.equal(
    voice.customText("Hello [Name]! Time for [Reminder title].",
      "Aakash", "Collagen drink", "en"),
    "Hello Aakash! Time for Collagen drink.");
});

test("unknown future categories stay generic instead of becoming medicine", () => {
  const texts = voice.voiceTexts({
    name: "అమ్మా",
    schedules: [{
      label: "పుట్టినరోజు",
      category: "birthday",
      enabled: true,
      questions: [{prompt: "[Reminder] గుర్తుందా?", answers: []}],
    }],
  });
  assert.deepEqual(texts, ["పుట్టినరోజు గుర్తుందా?"]);
  assert.equal(texts.some((text) => text.includes("టాబ్లెట్")), false);
});

test("legacy generic kind stays generic instead of becoming medicine", () => {
  const texts = voice.voiceTexts({
    name: "అమ్మా",
    schedules: [{
      kind: "appointment",
      label: "డాక్టర్ అపాయింట్మెంట్",
      enabled: true,
      questions: [{
        prompt: "హలో [Name]! [Reminder] గుర్తుందా?",
        answers: [{ label: "సరే", response: "సరే. Bye!" }],
      }],
    }],
  });
  assert.deepEqual(texts, [
    "హలో అమ్మా! డాక్టర్ అపాయింట్మెంట్ గుర్తుందా?",
    "సరే. Bye!",
  ]);
});

test("limits custom scripts and ignores empty voice lines", () => {
  const questions = [];
  for (let question = 0; question < 12; question += 1) {
    const answers = [];
    for (let answer = 0; answer < 6; answer += 1) {
      answers.push({label: `A${answer}`, response: answer === 0 ? "" : `R${question}-${answer}`});
    }
    questions.push({prompt: `Q${question}`, answers});
  }
  const texts = voice.voiceTexts({
    name: "N",
    schedules: [{label: "R", category: "custom", enabled: true, questions}],
  });
  assert.equal(texts.filter((text) => text.startsWith("Q")).length, 10);
  assert.equal(texts.filter((text) => text.startsWith("R")).length, 30);
});

test("limits one member to the same fifty reminders accepted by Android", () => {
  const schedules = Array.from({ length: 55 }, (_, index) => ({
    category: "task",
    label: `Task ${index}`,
    enabled: true,
    questions: [{
      prompt: `[Reminder] ${index}`,
      answers: [{ label: "సరే", response: "" }],
    }],
  }));
  const texts = voice.voiceTexts({ name: "అమ్మా", schedules });
  assert.equal(texts.length, 50);
  assert.equal(texts.at(-1), "Task 49 49");
});

test("content hashes are stable and voice-specific", () => {
  assert.match(voice.clipId("హలో"), /^[a-f0-9]{64}$/);
  assert.equal(voice.clipId("హలో"), voice.clipId("హలో"));
  assert.notEqual(voice.clipId("హలో"), voice.clipId("హాయ్"));
});

test("validates cached audio contents instead of trusting base64 length", () => {
  const mp3 = Buffer.alloc(300, 0);
  mp3[0] = 0x49;
  mp3[1] = 0x44;
  mp3[2] = 0x33;
  const value = {
    text: "హలో",
    voice: voice.VOICE_NAME,
    mimeType: "audio/mpeg",
    audioBase64: mp3.toString("base64"),
  };
  assert.equal(voice.validClipDocument(value, "హలో"), true);
  assert.equal(voice.validClipDocument({...value, text: "wrong"}, "హలో"), false);
  assert.equal(voice.validClipDocument({...value, mimeType: "text/plain"}, "హలో"), false);
  assert.equal(voice.validClipDocument({...value, audioBase64: "!".repeat(400)}, "హలో"), false);

  const notMp3 = Buffer.alloc(300, 0x41).toString("base64");
  assert.equal(voice.validClipDocument({...value, audioBase64: notMp3}, "హలో"), false);
});

test("accepts an MP3 frame header and rejects truncated or oversized audio", () => {
  const framed = Buffer.alloc(256, 0);
  framed[0] = 0xff;
  framed[1] = 0xfb;
  assert.equal(voice.validMp3Buffer(framed), true);
  assert.equal(voice.validMp3Buffer(Buffer.alloc(255)), false);
  assert.equal(voice.validMp3Buffer(Buffer.alloc(700001)), false);
});
