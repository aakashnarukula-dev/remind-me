"use strict";

const fs = require("node:fs/promises");
const path = require("node:path");
const textToSpeech = require("@google-cloud/text-to-speech");
const voice = require("./voice");

const output = path.resolve(__dirname, "../simplecall/src/main/res/raw");
const languages = ["en", "te", "hi", "ta", "kn", "ml"];
const delays = [5, 15, 30, 60];

async function generate() {
  const client = new textToSpeech.TextToSpeechClient();
  await fs.mkdir(output, {recursive: true});
  for (const language of languages) {
    const entries = [
      ["question", voice.reminderDelayQuestion(language)],
      ...delays.map((minutes) => [String(minutes), voice.reminderDelayed(minutes, language)]),
    ];
    for (const [suffix, text] of entries) {
      const [response] = await client.synthesizeSpeech({
        input: voice.synthesisInput(text, language),
        voice: {
          languageCode: voice.languageCode(language),
          name: voice.voiceName(language),
        },
        audioConfig: {audioEncoding: "MP3"},
      });
      const audio = Buffer.from(response.audioContent || []);
      if (!voice.validMp3Buffer(audio)) throw new Error(`Invalid ${language} ${suffix} clip`);
      await fs.writeFile(path.join(output, `remind_later_${language}_${suffix}.mp3`), audio);
    }
  }
}

generate().catch((error) => {
  process.stderr.write(`${error.stack || error.message}\n`);
  process.exitCode = 1;
});
