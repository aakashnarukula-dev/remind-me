"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const interpretation = require("./interpretation");

test("validates short mono PCM and rejects malformed or oversized payloads", () => {
  const pcm = Buffer.alloc(3_200, 4);
  assert.deepEqual(interpretation.decodeAudioBase64(pcm.toString("base64")), pcm);
  assert.throws(() => interpretation.decodeAudioBase64("not base64"));
  assert.throws(() => interpretation.decodeAudioBase64(Buffer.alloc(801).toString("base64")));
  assert.throws(() => interpretation.decodeAudioBase64(
    Buffer.alloc(interpretation.MAX_AUDIO_BYTES + 2).toString("base64")));
});

test("creates a valid 16 kHz mono PCM WAV", () => {
  const pcm = Buffer.from([1, 2, 3, 4]);
  const wav = interpretation.pcmToWav(pcm);
  assert.equal(wav.toString("ascii", 0, 4), "RIFF");
  assert.equal(wav.toString("ascii", 8, 12), "WAVE");
  assert.equal(wav.readUInt16LE(22), 1);
  assert.equal(wav.readUInt32LE(24), 16_000);
  assert.equal(wav.readUInt16LE(34), 16);
  assert.equal(wav.readUInt32LE(40), 4);
  assert.deepEqual(wav.subarray(44), pcm);
});

test("audio gate rejects silence and accepts audible speech energy", () => {
  assert.equal(interpretation.hasSpeechEnergy(Buffer.alloc(32_000)), false);
  const speech = Buffer.alloc(32_000);
  for (let offset = 0; offset < speech.length; offset += 2) {
    speech.writeInt16LE((offset / 2) % 2 === 0 ? 2_000 : -2_000, offset);
  }
  assert.equal(interpretation.hasSpeechEnergy(speech), true);
  assert.deepEqual(interpretation.audioStats(speech), {peak: 2_000, rms: 2_000});
});

test("audio gate accepts amplified quiet speech", () => {
  const quiet = Buffer.alloc(32_000);
  for (let offset = 0; offset < quiet.length; offset += 2) {
    quiet.writeInt16LE((offset / 2) % 2 === 0 ? 300 : -300, offset);
  }
  assert.equal(interpretation.hasSpeechEnergy(quiet), true);
});

test("retries only transient cloud failures", async () => {
  assert.equal(interpretation.isTransientFailure({code: 14}), true);
  assert.equal(interpretation.isTransientFailure({response: {status: 429}}), true);
  assert.equal(interpretation.isTransientFailure({message: "The operation was aborted."}), true);
  assert.equal(interpretation.isTransientFailure({response: {status: 403}}), false);
  let calls = 0;
  const value = await interpretation.retryTransient(async () => {
    calls++;
    if (calls === 1) throw {code: 14};
    return "ok";
  });
  assert.equal(value, "ok");
  assert.equal(calls, 2);
});

test("parses Gemini JSON and clamps unsafe values", () => {
  const response = { data: { candidates: [{ content: { parts: [{ text: JSON.stringify({
    intent: "taken", heard: "వేసుకున్నాను", confidence: 2,
  }) }] } }] } };
  assert.deepEqual(interpretation.parseGeminiResponse(response), {
    intent: "TAKEN", heard: "వేసుకున్నాను", confidence: 1, delayMinutes: 0,
  });
  const reminder = {data: {candidates: [{content: {parts: [{text: JSON.stringify({
    intent: "REMIND_LATER", heard: "పది నిమిషాల్లో", confidence: 0.9, delayMinutes: 10,
  })}]}}]}};
  assert.equal(interpretation.parseGeminiResponse(reminder).delayMinutes, 10);
  reminder.data.candidates[0].content.parts[0].text = JSON.stringify({
    intent: "REMIND_LATER", heard: "later", confidence: 0.9, delayMinutes: 0,
  });
  assert.equal(interpretation.parseGeminiResponse(reminder).delayMinutes, 10);
  const identity = {data: {candidates: [{content: {parts: [{text: JSON.stringify({
    intent: "IDENTITY_QUESTION", heard: "మీ పేరు ఏంటి", confidence: 0.95, delayMinutes: 0,
  })}]}}]}};
  assert.equal(interpretation.parseGeminiResponse(identity).intent, "IDENTITY_QUESTION");
});

test("Gemini prompt receives speech hypotheses, has no alternate classifier, and protects completion", () => {
  const prompt = interpretation.classifierPrompt({
    phase: "medicine", waiting: true, duringPrompt: false,
    promptText: "హలో ఆకాష్! టాబ్లెట్ వేసుకున్నారా?",
    transcripts: [{text: "మీ పేరు ఏంటి", confidence: 0.79}],
  });
  assert.match(prompt, /no keyword matcher, reconciliation, or alternate fallback/i);
  assert.match(prompt, /examples are guidance, not an allowed-word list/i);
  assert.match(prompt, /మీ పేరు ఏంటి/);
  assert.match(prompt, /హలో ఆకాష్/);
  assert.match(prompt, /attached original audio/i);
  assert.match(prompt, /question.*vesukunnara.*NEVER TAKEN/i);
  assert.match(prompt, /exact spoken prompt.*no clearly separate reply words.*return UNCLEAR/is);
  assert.match(prompt, /ASR commonly drops the final vowel.*affirmative.*still TAKEN/i);
  assert.match(prompt, /one clearly meaningful positive or negative hypothesis is enough/i);
  assert.match(prompt, /When this is no.*do not reject a clear reply as speaker leakage/i);
  assert.match(prompt, /exact prompt text is unavailable/i);
  assert.match(prompt, /bare no.*complete negative answer/i);
  assert.match(prompt, /REMIND_LATER/);
  assert.match(prompt, /IDENTITY_QUESTION/);
});

test("Gemini prompt can interpret original audio without a separate transcription call", () => {
  const prompt = interpretation.classifierPrompt({
    phase: "medicine", waiting: false, duringPrompt: false,
    promptText: "హలో ఆకాష్! టాబ్లెట్ వేసుకున్నారా?", transcripts: [],
  });
  assert.match(prompt, /Listen directly to the attached original audio/i);
  assert.match(prompt, /Transcribe the person's short reply yourself/i);
  assert.match(prompt, /Recognition hypotheses, ordered best-first: \[\]/);
});

test("Gemini can classify an on-device transcript without expecting attached audio", () => {
  const prompt = interpretation.classifierPrompt({
    phase: "medicine", waiting: false, duringPrompt: false,
    promptText: "హలో ఆకాష్! టాబ్లెట్ వేసుకున్నారా?",
    transcripts: [{text: "ఈ రోజు వాతావరణం ఎలా ఉంది", confidence: 1}],
    hasAudio: false,
  });
  assert.match(prompt, /on-device Telugu recognition transcript/i);
  assert.doesNotMatch(prompt, /Listen to the attached original audio as well/i);
  assert.match(prompt, /ఈ రోజు వాతావరణం ఎలా ఉంది/);
});
