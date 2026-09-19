#!/usr/bin/env node
"use strict";

const fs = require("node:fs");
const path = require("node:path");
const {createRequire} = require("node:module");
const requireFromFunctions = createRequire(path.join(__dirname, "../functions/package.json"));
const {initializeApp, cert} = requireFromFunctions("firebase-admin/app");
const {getFirestore, FieldValue} = requireFromFunctions("firebase-admin/firestore");
const {TextToSpeechClient} = requireFromFunctions("@google-cloud/text-to-speech");
const localInterpretation = process.env.LOCAL_INTERPRETATION === "1"
    || process.env.LOCAL_FAST_INTERPRETATION === "1"
  ? require("../functions/interpretation") : null;
const useFastInterpretation = process.env.LOCAL_FAST_INTERPRETATION === "1";
const useRemoteFastInterpretation = process.env.REMOTE_FAST_INTERPRETATION === "1";
const useRemoteTextInterpretation = process.env.REMOTE_TEXT_INTERPRETATION === "1";

const credentialsPath = process.env.FIREBASE_SERVICE_ACCOUNT;
if (!credentialsPath) throw new Error("Set FIREBASE_SERVICE_ACCOUNT to the service-account JSON path");
const credentials = JSON.parse(fs.readFileSync(credentialsPath, "utf8"));
initializeApp({credential: cert(credentials), projectId: credentials.project_id});
const database = getFirestore();
const tts = new TextToSpeechClient({credentials, projectId: credentials.project_id});

const allCases = [
  {label: "identity", text: "మీ పేరు ఏంటి", expected: "IDENTITY_QUESTION"},
  {label: "identity casual", text: "నువ్వు ఎవరు", expected: "IDENTITY_QUESTION"},
  {label: "prompt echo", text: "హలో ఆకాష్ టాబ్లెట్ వేసుకున్నారా", expected: "UNCLEAR", duringPrompt: true},
  {label: "prompt echo legacy", text: "హలో ఆకాష్ టాబ్లెట్ వేసుకున్నారా", expected: "UNCLEAR", duringPrompt: true, omitPrompt: true},
  {label: "unrelated", text: "ఈరోజు వాతావరణం ఎలా ఉంది", expected: "OTHER"},
  {label: "taken", text: "టాబ్లెట్ వేసుకున్నాను", expected: "TAKEN"},
  {label: "taken ha", text: "హా వేసుకున్నా", expected: "TAKEN"},
  {label: "taken casual", text: "హా మందు వేసేసుకున్నాను", expected: "TAKEN"},
  {label: "taken polite", text: "వేసుకున్నాను అండి", expected: "TAKEN"},
  {label: "taken short", text: "వేసుకున్నాను", expected: "TAKEN"},
  {label: "taken indirect", text: "అవునండి ఆ మందు నేను పొద్దున్నే తీసుకున్నాను", expected: "TAKEN"},
  {label: "not taken", text: "ఇంకా టాబ్లెట్ వేసుకోలేదు", expected: "NOT_TAKEN"},
  {label: "bare no", text: "లేదు", expected: "NOT_TAKEN"},
  {label: "no not taken", text: "లేదు వేసుకోలేదు", expected: "NOT_TAKEN"},
  {label: "not taken short", text: "వేసుకోలేదు", expected: "NOT_TAKEN"},
  {label: "not yet", text: "ఇంకా లేదు", expected: "NOT_TAKEN"},
  {label: "not taken indirect", text: "లేదు అండి ఆ మందు ఇంకా తీసుకోలేదు", expected: "NOT_TAKEN"},
  {label: "will take now", text: "సరే ఇప్పుడే వేసుకుంటాను", expected: "WAIT"},
  {label: "will take later natural", text: "సరే అండి ఇంకొంచెం సేపట్లో వేసుకుంటాను", expected: "WAIT"},
  {label: "wait on line", text: "ఒక్క నిమిషం వేచి ఉండండి", expected: "WAIT", waiting: true},
  {label: "callback", text: "పది నిమిషాల్లో మళ్లీ గుర్తు చేయండి", expected: "REMIND_LATER", delay: 10},
  {label: "medicine question", text: "ఏ టాబ్లెట్ వేసుకోవాలి", expected: "MEDICINE_QUESTION"},
  {label: "medicine finished", text: "టాబ్లెట్లు అయిపోయాయి", expected: "MEDICINE_FINISHED"},
  {label: "health issue", text: "నాకు ఒంట్లో బాగోలేదు", expected: "HEALTH_ISSUE"},
  {label: "meal done", text: "భోజనం చేశాను", expected: "TAKEN", phase: "meal"},
  {label: "meal not done", text: "ఇంకా భోజనం చేయలేదు", expected: "NOT_TAKEN", phase: "meal"},
];
const requestedLabels = new Set((process.env.CASE_FILTER || "")
  .split(",").map((value) => value.trim()).filter(Boolean));
const cases = requestedLabels.size === 0
  ? allCases : allCases.filter((item) => requestedLabels.has(item.label));

function rawPcm(linear16) {
  if (linear16.toString("ascii", 0, 4) !== "RIFF") return linear16;
  let offset = 12;
  while (offset + 8 <= linear16.length) {
    const id = linear16.toString("ascii", offset, offset + 4);
    const length = linear16.readUInt32LE(offset + 4);
    if (id === "data") return linear16.subarray(offset + 8, offset + 8 + length);
    offset += 8 + length + (length % 2);
  }
  throw new Error("No PCM data chunk in synthesized WAV");
}

async function synthesize(text) {
  const [response] = await tts.synthesizeSpeech({
    input: {text},
    voice: {languageCode: "te-IN", name: "te-IN-Chirp3-HD-Leda"},
    audioConfig: {audioEncoding: "LINEAR16", sampleRateHertz: 16000},
  });
  return rawPcm(Buffer.from(response.audioContent));
}

async function assignedDevice() {
  for (const family of await database.collection("families").listDocuments()) {
    const devices = await family.collection("devices").where("status", "==", "assigned").limit(1).get();
    if (!devices.empty) return {family, device: devices.docs[0]};
  }
  throw new Error("No assigned parent phone found");
}

async function waitForResult(reference, timeoutMs = 30000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const snapshot = await reference.get();
    if (snapshot.exists) return snapshot.data();
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`Timed out waiting for ${reference.path}`);
}

async function main() {
  const target = localInterpretation ? null : await assignedDevice();
  const family = target && target.family;
  const device = target && target.device;
  const memberId = device && device.get("memberId");
  const failures = [];
  for (const item of cases) {
    const pcm = useRemoteTextInterpretation ? null : await synthesize(item.text);
    const interpretationStartedAt = Date.now();
    let value;
    if (localInterpretation) {
      const interpreter = useFastInterpretation
        ? localInterpretation.interpretFast : localInterpretation.interpret;
      value = await interpreter(pcm, {
        phase: item.phase || "medicine",
        waiting: item.waiting === true,
        duringPrompt: item.duringPrompt === true,
        promptText: item.phase === "meal"
          ? "హలో ఆకాష్! భోజనం చేశారా?"
          : "హలో ఆకాష్! టాబ్లెట్ వేసుకున్నారా?",
      });
    } else {
      const id = `codex-strict-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
      const request = device.ref.collection("speechRequests").doc(id);
      const result = device.ref.collection("speechResults").doc(id);
    const requestData = {
      memberId,
      phase: item.phase || "medicine",
      waiting: item.waiting === true,
      duringPrompt: item.duringPrompt === true,
      createdAt: FieldValue.serverTimestamp(),
    };
    if (useRemoteTextInterpretation) {
      requestData.transcript = item.text;
      requestData.textOnly = true;
      requestData.duringPrompt = false;
    } else {
      requestData.audioBase64 = pcm.toString("base64");
      if (useRemoteFastInterpretation) requestData.fast = true;
    }
    if (!item.omitPrompt) {
      requestData.promptText = item.phase === "meal"
        ? "హలో ఆకాష్! భోజనం చేశారా?"
        : "హలో ఆకాష్! టాబ్లెట్ వేసుకున్నారా?";
    }
    await request.set(requestData);
    try {
      value = await waitForResult(result);
    } finally {
      await Promise.allSettled([request.delete(), result.delete()]);
    }
    }
    const passed = value.intent === item.expected
      && (item.delay == null || value.delayMinutes === item.delay);
    process.stdout.write(`${passed ? "PASS" : "FAIL"} ${item.label}: `
      + `${value.intent} (${value.source}, ${value.confidence})`
      + ` ${Date.now() - interpretationStartedAt}ms`
      + `${value.delayMinutes ? ` delay=${value.delayMinutes}` : ""}`
      + ` heard=${JSON.stringify(value.heard || "")}\n`);
    if (!passed) failures.push({item, value});
  }
  if (failures.length) {
    process.stderr.write(`${failures.length} live intent case(s) failed\n`);
    process.exitCode = 1;
  }
  process.stdout.write(localInterpretation
    ? "Tested local function code; no records created\n"
    : `Tested ${family.id}/${device.id}; temporary records deleted\n`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
