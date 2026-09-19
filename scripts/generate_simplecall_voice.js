"use strict";

const fs = require("fs");
const path = require("path");
const textToSpeech = require("../functions/node_modules/@google-cloud/text-to-speech");
const voice = require("../functions/voice");

async function main() {
  const client = new textToSpeech.TextToSpeechClient();
  const clips = [
    ["medicine_not_taken.mp3", voice.medicineNotTaken()],
    ["reminder_delayed_5.mp3", voice.reminderDelayed(5)],
  ];
  for (const [name, text] of clips) {
    const [response] = await client.synthesizeSpeech({
      input: voice.synthesisInput(text),
      voice: {languageCode: "te-IN", name: voice.VOICE_NAME},
      audioConfig: {audioEncoding: "MP3"},
    });
    const audio = Buffer.from(response.audioContent || []);
    if (!voice.validMp3Buffer(audio)) throw new Error(`Generated ${name} is not a valid MP3`);
    const output = path.join(__dirname, "..", "simplecall", "src", "main", "res", "raw", name);
    fs.mkdirSync(path.dirname(output), {recursive: true});
    fs.writeFileSync(output, audio);
    process.stdout.write(`${output}\n`);
  }
}

main().catch((error) => {
  process.stderr.write(`${error.message}\n`);
  process.exitCode = 1;
});
