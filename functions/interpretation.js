"use strict";

const speech = require("@google-cloud/speech");
const { GoogleAuth } = require("google-auth-library");

const MAX_AUDIO_BYTES = 16_000 * 2 * 12;
const SAMPLE_RATE = 16_000;
const MODEL = "gemini-2.5-flash";
const FAST_MODEL = "gemini-2.5-flash";
const VALID_INTENTS = new Set([
  "TAKEN",
  "NOT_TAKEN",
  "WAIT",
  "REMIND_LATER",
  "MEDICINE_FINISHED",
  "MEDICINE_QUESTION",
  "IDENTITY_QUESTION",
  "HEALTH_ISSUE",
  "OTHER",
  "UNCLEAR",
]);

const speechClient = new speech.SpeechClient();
const auth = new GoogleAuth({ scopes: ["https://www.googleapis.com/auth/cloud-platform"] });

function decodeAudioBase64(encoded) {
  if (typeof encoded !== "string" || encoded.length < 100 || encoded.length > 700_000
      || !/^[A-Za-z0-9+/]+={0,2}$/.test(encoded) || encoded.length % 4 !== 0) {
    throw new Error("Invalid audio");
  }
  const audio = Buffer.from(encoded, "base64");
  if (audio.length < 800 || audio.length > MAX_AUDIO_BYTES || audio.length % 2 !== 0) {
    throw new Error("Invalid audio length");
  }
  return audio;
}

function pcmToWav(pcm, sampleRate = SAMPLE_RATE) {
  const header = Buffer.alloc(44);
  header.write("RIFF", 0);
  header.writeUInt32LE(36 + pcm.length, 4);
  header.write("WAVE", 8);
  header.write("fmt ", 12);
  header.writeUInt32LE(16, 16);
  header.writeUInt16LE(1, 20);
  header.writeUInt16LE(1, 22);
  header.writeUInt32LE(sampleRate, 24);
  header.writeUInt32LE(sampleRate * 2, 28);
  header.writeUInt16LE(2, 32);
  header.writeUInt16LE(16, 34);
  header.write("data", 36);
  header.writeUInt32LE(pcm.length, 40);
  return Buffer.concat([header, pcm]);
}

function audioStats(pcm) {
  let peak = 0;
  let sumSquares = 0;
  const count = Math.floor(pcm.length / 2);
  for (let offset = 0; offset + 1 < pcm.length; offset += 2) {
    const sample = pcm.readInt16LE(offset);
    const absolute = Math.abs(sample);
    if (absolute > peak) peak = absolute;
    sumSquares += sample * sample;
  }
  return { peak, rms: count ? Math.sqrt(sumSquares / count) : 0 };
}

function hasSpeechEnergy(pcm) {
  const stats = audioStats(pcm);
  return stats.peak >= 250 && stats.rms >= 35;
}

function boundedDelay(value, fallback = 10) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed < 1) return fallback;
  return Math.max(1, Math.min(120, Math.round(parsed)));
}

function isTransientFailure(error) {
  const code = Number(error && error.code);
  const status = Number(error && error.response && error.response.status);
  const name = String(error && error.name || "").toLowerCase();
  const message = String(error && error.message || "").toLowerCase();
  return [4, 8, 13, 14].includes(code)
    || [408, 429, 500, 502, 503, 504].includes(status)
    || name.includes("abort") || message.includes("aborted");
}

async function retryTransient(operation) {
  try {
    return await operation();
  } catch (error) {
    if (!isTransientFailure(error)) throw error;
    await new Promise((resolve) => setTimeout(resolve, 350));
    return operation();
  }
}

async function transcribe(pcm) {
  const [response] = await speechClient.recognize({
    audio: { content: pcm },
    config: {
      encoding: "LINEAR16",
      sampleRateHertz: SAMPLE_RATE,
      languageCode: "te-IN",
      alternativeLanguageCodes: ["en-IN"],
      model: "latest_short",
      maxAlternatives: 5,
      enableAutomaticPunctuation: false,
      speechContexts: [{
        boost: 18,
        phrases: [
          "వేసుకున్నాను", "వేసుకున్నా", "టాబ్లెట్ వేసుకున్నాను", "మందు వేసుకున్నాను",
          "హా వేసుకున్నా", "వేసుకున్నాను అండి", "ఇంకా వేసుకోలేదు", "వేసుకోలేదు",
          "లేదు", "లేదు వేసుకోలేదు", "తీసుకోలేదు", "ఇంకా లేదు",
          "వేసుకుంటాను", "వేసుకుని వస్తాను", "ఒక్క నిమిషం", "సరే", "అవును", "కాదు",
          "భోజనం చేశాను", "ఇంకా భోజనం చేయలేదు", "తిన్నాను",
          "పది నిమిషాల్లో గుర్తు చేయండి", "మళ్లీ కాల్ చేయండి", "అరగంట తర్వాత కాల్ చేయండి",
          "ఏ టాబ్లెట్ వేసుకోవాలి", "మందు పేరు ఏమిటి", "మందులు అయిపోయాయి",
          "టాబ్లెట్లు అయిపోయాయి", "ఒంట్లో బాగోలేదు", "ఆరోగ్య సమస్య ఉంది",
          "మీ పేరు ఏంటి", "మీ పేరు ఏమిటి", "నీ పేరు ఏంటి", "నువ్వు ఎవరు",
        ],
      }],
    },
  });
  const alternatives = [];
  for (const result of response.results || []) {
    for (const alternative of result.alternatives || []) {
      const text = typeof alternative.transcript === "string"
        ? alternative.transcript.trim() : "";
      if (text && !alternatives.some((item) => item.text === text)) {
        alternatives.push({
          text: text.slice(0, 300),
          confidence: Math.max(0, Math.min(1, Number(alternative.confidence) || 0)),
        });
      }
    }
  }
  return alternatives.slice(0, 8);
}

function classifierPrompt({ phase, waiting, duringPrompt, promptText, transcripts,
  hasAudio = true }) {
  const hypotheses = Array.isArray(transcripts) ? transcripts : [];
  const situation = phase === "meal"
    ? "Chitti asked whether the person has eaten. TAKEN means the person clearly says the meal is already completed; NOT_TAKEN means they clearly say they have not eaten."
    : "Chitti asked whether the person has taken medicine. TAKEN means the person clearly says the dose is already swallowed; NOT_TAKEN means they clearly say it is not yet taken.";
  return [
    "Classify the person's reply from the Telugu/English speech-recognition hypotheses below. It may be Telugu, Telugu-English code-switching, transliterated Telugu, or Indian English.",
    "Interpret the complete meaning in the context of Chitti's question. The examples are guidance, not an allowed-word list: understand natural paraphrases, polite fillers, indirect replies, and word-order variations.",
    hypotheses.length
      ? hasAudio
        ? "Listen to the attached original audio as well as reading every recognition hypothesis. They are two representations of the same utterance for your single semantic decision; resolve clipped word endings from the audio."
        : "Use the on-device Telugu recognition transcript below for one contextual semantic decision. Preserve its wording in heard."
      : "Listen directly to the attached original audio. Transcribe the person's short reply yourself, then make one contextual semantic decision from that same audio.",
    situation,
    "This is one required pipeline: speech recognition followed by your semantic decision. There is no keyword matcher, reconciliation, or alternate fallback.",
    "TAKEN requires an explicit completed reply such as 'vesukunna' or 'already took it'. A question such as 'vesukunnara?' is NEVER TAKEN. Telugu ASR commonly drops the final vowel and writes a completed 'వేసుకున్నా' as 'వేసుకున్న'; when it is preceded by an affirmative such as 'హా/అవును' and is not a question, it is still TAKEN.",
    "WAIT means they say they will do it now/later, ask Chitti to wait, or acknowledge an instruction without saying it is completed.",
    "REMIND_LATER means they ask for another reminder/call after some minutes. Put that number in delayMinutes, bounded from 1 to 120; use 10 when no number is clear.",
    "MEDICINE_FINISHED means their medicine/tablet supply has run out, not that today's dose was completed.",
    "MEDICINE_QUESTION means they ask which medicine/tablet to take or ask its name.",
    "IDENTITY_QUESTION means they ask Chitti's name or who is speaking, including 'mee peru enti?' or 'nuvvu evaru?'.",
    "HEALTH_ISSUE means they report pain, illness, dizziness, fever, breathing difficulty, or another health problem.",
    "OTHER means clearly spoken but unrelated words or questions.",
    "UNCLEAR means unintelligible text, silence/noise, or only Chitti's reminder question leaking from the speaker.",
    "MANDATORY: understandable unrelated speech such as a question about today's weather is OTHER and must be transcribed in heard. Never choose UNCLEAR merely because the reply does not answer the medicine or meal question.",
    "A complete reminder question that starts with a greeting/name and asks 'tablet vesukunnara?' or 'bhojanam chesara?' is Chitti speaker leakage and is UNCLEAR even if the exact prompt text is unavailable.",
    "Apply these examples by meaning, including close Telugu wording and recognition variants:",
    "MANDATORY: medicine + 'వేసుకున్నాను అండి', 'హా వేసుకున్నా', ASR variant 'హ వేసుకున్న', 'మందు వేసేసుకున్నాను', or 'tablet already తీసుకున్నాను' => TAKEN unless the person is asking a question or contradicting it.",
    "MANDATORY: medicine + 'లేదు', 'కాదు', 'వేసుకోలేదు', 'లేదు వేసుకోలేదు', 'ఇంకా లేదు', 'ఇంకా టాబ్లెట్ వేసుకోలేదు', or 'లేదు ఇంకా తీసుకోలేదు' => NOT_TAKEN. A bare no is a complete negative answer to Chitti's yes/no question; do not require the word medicine.",
    "medicine + 'వేసుకుంటాను', 'ఇప్పుడే తీసుకుంటాను', or 'ఒక్క నిమిషం wait' => WAIT, because it is not completed yet.",
    "meal + 'భోజనం చేశాను' or 'తిన్నాను' => TAKEN; meal + 'ఇంకా భోజనం చేయలేదు' => NOT_TAKEN.",
    "'మీ పేరు ఏంటి' or 'నువ్వు ఎవరు' => IDENTITY_QUESTION, never TAKEN.",
    "Use all hypotheses: one clearly meaningful positive or negative hypothesis is enough when the alternatives are merely clipped spelling variants of the same reply. Prefer UNCLEAR only when the reply itself is genuinely unintelligible, contradictory, or indistinguishable from Chitti's question.",
    `The call is already waiting for completion: ${waiting ? "yes" : "no"}.`,
    `Chitti's exact spoken prompt was: ${JSON.stringify(promptText || "")}.`,
    `The reply might overlap the end of Chitti's prompt: ${duringPrompt ? "yes" : "no"}. When this is yes, compare the hypotheses with Chitti's exact prompt. Ignore any copied, truncated, or slightly mistranscribed prompt words. Classify only clearly separate words spoken by the person. If there are no clearly separate reply words, return UNCLEAR. Speech recognition may drop the final question sound and render Chitti's 'vesukunnara' as 'vesukunna'; that alone is still UNCLEAR, never TAKEN. A separately spoken short reply such as 'tablet vesukunna' remains TAKEN. A question is never a completed dose. When this is no, Chitti has finished speaking: do not reject a clear reply as speaker leakage merely because it repeats words from the question.`,
    `Recognition hypotheses, ordered best-first: ${JSON.stringify(hypotheses)}`,
    "Return only the requested JSON. heard must be a short transcription of the person's reply, or empty when unclear.",
  ].join("\n");
}

function parseGeminiResponse(response) {
  const parts = response && response.data && response.data.candidates
    && response.data.candidates[0] && response.data.candidates[0].content
    ? response.data.candidates[0].content.parts : [];
  const text = Array.isArray(parts)
    ? parts.map((part) => typeof part.text === "string" ? part.text : "").join("") : "";
  const parsed = JSON.parse(text);
  const intent = typeof parsed.intent === "string" ? parsed.intent.toUpperCase() : "UNCLEAR";
  const validIntent = VALID_INTENTS.has(intent) ? intent : "UNCLEAR";
  return {
    intent: validIntent,
    heard: typeof parsed.heard === "string" ? parsed.heard.trim().slice(0, 300) : "",
    confidence: Math.max(0, Math.min(1, Number(parsed.confidence) || 0)),
    delayMinutes: validIntent === "REMIND_LATER" ? boundedDelay(parsed.delayMinutes) : 0,
  };
}

async function classifyWithGemini({ phase, waiting, duringPrompt, promptText, transcripts, pcm,
  model = MODEL, hasAudio = true }) {
  const projectId = process.env.GCLOUD_PROJECT || process.env.GOOGLE_CLOUD_PROJECT;
  if (!projectId) throw new Error("Missing Google Cloud project ID");
  const client = await auth.getClient();
  const url = `https://aiplatform.googleapis.com/v1/projects/${projectId}`
    + `/locations/global/publishers/google/models/${model}:generateContent`;
  const parts = [{
    text: classifierPrompt({phase, waiting, duringPrompt, promptText, transcripts, hasAudio}),
  }];
  if (hasAudio) {
    parts.push({inlineData: {mimeType: "audio/wav", data: pcmToWav(pcm).toString("base64")}});
  }
  const response = await client.request({
    url,
    method: "POST",
    data: {
      contents: [{
        role: "user",
        parts,
      }],
      generationConfig: {
        temperature: 0,
        seed: 17,
        maxOutputTokens: 512,
        thinkingConfig: { thinkingBudget: 0 },
        responseMimeType: "application/json",
        responseSchema: {
          type: "OBJECT",
          properties: {
            intent: { type: "STRING", enum: [...VALID_INTENTS] },
            heard: { type: "STRING" },
            confidence: { type: "NUMBER", minimum: 0, maximum: 1 },
            delayMinutes: { type: "INTEGER", minimum: 0, maximum: 120 },
          },
          required: ["intent", "heard", "confidence", "delayMinutes"],
        },
      },
    },
    timeout: 12_000,
  });
  return parseGeminiResponse(response);
}

/** One low-latency multimodal request: Gemini hears, transcribes, and classifies. */
async function interpretFast(pcm, context) {
  if (!hasSpeechEnergy(pcm)) {
    return { intent: "UNCLEAR", heard: "", confidence: 0, delayMinutes: 0,
      source: "audio-gate" };
  }
  try {
    const result = await retryTransient(() => classifyWithGemini({
      transcripts: [], pcm, model: FAST_MODEL, ...context,
    }));
    return { ...result, source: "audio-gemini-fast" };
  } catch (error) {
    const apiError = error && error.response && error.response.data
      ? error.response.data.error : null;
    console.warn("Fast Gemini audio interpretation failed", {
      code: error.code || "unknown",
      status: apiError && apiError.status ? apiError.status : "unknown",
      message: apiError && apiError.message
        ? String(apiError.message).slice(0, 500) : String(error.message || "unknown").slice(0, 500),
    });
    return { intent: "UNCLEAR", heard: "", confidence: 0, delayMinutes: 0,
      source: "gemini-unavailable" };
  }
}

async function interpret(pcm, context) {
  if (!hasSpeechEnergy(pcm)) {
    return { intent: "UNCLEAR", heard: "", confidence: 0, delayMinutes: 0, source: "audio-gate" };
  }
  let transcripts;
  try {
    transcripts = await retryTransient(() => transcribe(pcm));
  } catch (error) {
    console.warn("Required speech transcription failed", { code: error.code || "unknown" });
    return { intent: "UNCLEAR", heard: "", confidence: 0, delayMinutes: 0,
      source: "speech-unavailable" };
  }
  if (transcripts.length === 0) {
    return { intent: "UNCLEAR", heard: "", confidence: 0, delayMinutes: 0,
      source: "speech-empty" };
  }
  try {
    const result = await retryTransient(
      () => classifyWithGemini({ transcripts, pcm, ...context }));
    // The model's numeric confidence is not calibrated and often mirrors the
    // acoustic score of a clipped ASR spelling. Its controlled UNCLEAR intent is
    // the authoritative ambiguity decision; do not override a semantic result.
    return { ...result, source: "speech-gemini" };
  } catch (error) {
    const apiError = error && error.response && error.response.data
      ? error.response.data.error : null;
    console.warn("Required Gemini interpretation failed", {
      code: error.code || "unknown",
      status: apiError && apiError.status ? apiError.status : "unknown",
      message: apiError && apiError.message
        ? String(apiError.message).slice(0, 500) : String(error.message || "unknown").slice(0, 500),
    });
    return { intent: "UNCLEAR", heard: "", confidence: 0, delayMinutes: 0,
      source: "gemini-unavailable" };
  }
}

/** Classifies a transcript produced by Android's strictly on-device recognizer. */
async function interpretText(transcript, context) {
  if (typeof transcript !== "string" || !transcript.trim() || transcript.length > 300) {
    return {intent: "UNCLEAR", heard: "", confidence: 0, delayMinutes: 0,
      source: "device-text-invalid"};
  }
  const clean = transcript.trim();
  try {
    const result = await retryTransient(() => classifyWithGemini({
      transcripts: [{text: clean, confidence: 1}],
      pcm: null,
      hasAudio: false,
      model: FAST_MODEL,
      ...context,
    }));
    return {...result, source: "device-text-gemini"};
  } catch (error) {
    const apiError = error && error.response && error.response.data
      ? error.response.data.error : null;
    console.warn("Gemini text interpretation failed", {
      code: error.code || "unknown",
      status: apiError && apiError.status ? apiError.status : "unknown",
      message: apiError && apiError.message
        ? String(apiError.message).slice(0, 500) : String(error.message || "unknown").slice(0, 500),
    });
    return {intent: "UNCLEAR", heard: "", confidence: 0, delayMinutes: 0,
      source: "gemini-unavailable"};
  }
}

module.exports = {
  MAX_AUDIO_BYTES,
  SAMPLE_RATE,
  decodeAudioBase64,
  pcmToWav,
  audioStats,
  hasSpeechEnergy,
  isTransientFailure,
  retryTransient,
  transcribe,
  classifierPrompt,
  parseGeminiResponse,
  interpretFast,
  interpret,
  interpretText,
};
