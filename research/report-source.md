# Research source: offline Telugu speech recognition

Research date: 4 September 2026  
Project: Gurthu Chey parent app  
Question: Does the bundled Dolphin Telugu model with sherpa-onnx native libraries work more accurately and reliably than the app's previous speech-recognition paths?

## Scope and method

The comparison covers the exact implementations present in this repository:

1. Bundled Dolphin small INT8 CTC model executed by sherpa-onnx 1.13.7.
2. Android `SpeechRecognizer`, preferring the dedicated on-device service and falling back to the system recognizer with Telugu (`te-IN`) and `EXTRA_PREFER_OFFLINE`.
3. Google Cloud Speech-to-Text plus the earlier cloud semantic interpretation path.
4. Buttons as the deterministic, fully offline confirmation path.

Decision criteria are Telugu intent accuracy, dangerous false-positive rate, quiet-speech/noise robustness, offline guarantee, latency, device consistency, APK footprint, and evidence quality. Primary sources were preferred. Searches found no independent, device-level Telugu comparison of Dolphin's sherpa-onnx INT8 CTC export against Android/Gboard or Google Cloud Speech-to-Text.

## Repository facts

- `parent/src/main/java/com/gurthuchey/app/OfflineSpeechRecognizer.java` loads `dolphin-small-int8/model.int8.onnx`, records mono 16 kHz audio using `VOICE_RECOGNITION`, applies available Android echo cancellation/noise suppression/automatic gain control, uses local energy-based endpointing, and decodes using sherpa-onnx greedy search on CPU.
- `parent/build.gradle` bundles sherpa-onnx 1.13.7 and both `armeabi-v7a` and `arm64-v8a` native libraries.
- The Dolphin ONNX asset is approximately 238 MiB on disk. The current parent APK is approximately 295 MiB.
- `OnDeviceTeluguRecognizer.java` represents the prior Android API route. It requests Telugu, partial results, five alternatives, short endpointing, and offline preference, while recreating sessions after terminal callbacks.
- `LocalReplyInterpreter.java` converts transcripts into a deliberately limited set of call states. It prioritizes negative/ambiguous safety behavior instead of treating an unclear answer as medicine taken.
- The current active call path uses the bundled recognizer and local interpreter; the older Android and cloud classes remain in the source tree but are not the primary active recognition path.

## Primary evidence

### Dolphin

- Dolphin is a 40-language Eastern-language ASR family. The paper lists Telugu as `te-IN` and reports 372 million parameters for the small model. It was trained on 212,137 labelled hours in total, of which 137,712 hours are proprietary Dataocean material. Source: [Dolphin paper](https://arxiv.org/html/2503.20212).
- Published Telugu WER for Dolphin small is 46.2% on the Dataocean set, 62.1% on Common Voice 17, and 37.8% on FLEURS. It substantially beats same-size Whisper in those author-run tests, but the absolute error rates are not close to perfect. Source: [Dolphin paper, detailed per-language results](https://arxiv.org/html/2503.20212).
- Critical mismatch: those reported results use only Dolphin's attention decoder, 30-second padding, beam size 5, and checkpoint averaging. The paper says its CTC layers were not used for evaluation. Gurthu Chey instead uses the quantized INT8 CTC-only export with greedy search. The paper therefore does not validate the exact APK model. Source: [Dolphin paper, experimental setup](https://arxiv.org/html/2503.20212).
- Dolphin's authors themselves list low-latency, real-time optimization and compression as future deployment work. Source: [Dolphin paper, future work](https://arxiv.org/html/2503.20212).

### sherpa-onnx native runtime

- The official sherpa-onnx package used by the app is `sherpa-onnx-dolphin-small-ctc-multi-lang-int8-2025-04-02`. The package contains a 239 MB INT8 model and 493 KB token file and uses greedy search in the published example. Source: [sherpa-onnx Dolphin model documentation](https://k2-fsa.github.io/sherpa/onnx/Dolphin/pretrained.html).
- sherpa-onnx demonstrates a real-time factor of 0.212 for one 5.611-second Chinese sample, but the host is not identified as the target Android phone, and the sample is not Telugu. It is runtime evidence, not Telugu-accuracy evidence. Source: [sherpa-onnx Dolphin model documentation](https://k2-fsa.github.io/sherpa/onnx/Dolphin/pretrained.html).
- sherpa-onnx supplies the Android/JNI/ONNX runtime. It does not make Dolphin's transcript more accurate by itself. Model, decoder, microphone pipeline, endpointing, and input conditions determine end-to-end accuracy.

### Android native speech recognition

- Android provides a dedicated `createOnDeviceSpeechRecognizer()` only when `isOnDeviceRecognitionAvailable()` is true; otherwise it fails. Language support can also be unavailable until a model is downloaded. Source: [Android `SpeechRecognizer`](https://developer.android.com/reference/android/speech/SpeechRecognizer).
- The generic recognizer may stream audio to remote servers and is explicitly not intended for continuous recognition. Clients must wait for `onResults` or `onError` before starting another recognition session. These lifecycle constraints explain some previous busy/restart and microphone-toggling behavior. Source: [Android `SpeechRecognizer`](https://developer.android.com/reference/android/speech/SpeechRecognizer).
- `EXTRA_PREFER_OFFLINE` is only a request; Android documents that it may have no effect depending on the recognizer implementation. Source: [Android `RecognizerIntent`](https://developer.android.com/reference/kotlin/android/speech/RecognizerIntent).
- A successful Telugu result from the Gboard keyboard microphone does not prove that an app receives the same recognizer implementation, configuration, model, or session behavior through `SpeechRecognizer`. Gboard's microphone is a product feature, whereas the app API delegates to an installed recognition service. Source: [Gboard voice typing help](https://support.google.com/gboard/answer/2781851?hl=en-IN) and the Android API documentation above.

### Google Cloud Speech-to-Text

- Google Cloud Speech-to-Text supports Telugu (`te-IN`); Chirp 3 currently lists Telugu as Preview. Source: [Cloud Speech-to-Text supported languages](https://docs.cloud.google.com/speech-to-text/docs/speech-to-text-supported-languages) and [Chirp 3 documentation](https://docs.cloud.google.com/speech-to-text/docs/models/chirp-3).
- Cloud streaming still has network, endpointing, and server latency. Google's Chirp documentation describes the accuracy/latency trade-off in speech endpointing. No official apples-to-apples Telugu WER was found for the exact short phrases, parents, phones, and noise conditions in this app.
- The project history found the cloud path semantically strong but network-dependent and subject to multi-second response delays. This is project evidence, not a public benchmark.

### Other Telugu-specific offline model checked

- AI4Bharat publishes a Telugu IndicConformer CTC/RNNT model with a 120-million-parameter encoder. Its official model card does not publish Telugu WER, Android resource measurements, or a supported small mobile integration. It is a credible future test candidate, not currently proven better for this app. Source: [AI4Bharat Telugu IndicConformer model card](https://huggingface.co/ai4bharat/indicconformer_stt_te_hybrid_ctc_rnnt_large) and [official repository](https://github.com/AI4Bharat/IndicConformerASR).

## Findings

1. **No recognizer in this comparison can honestly be called perfect.** The published Dolphin small Telugu WER is high, and it is for a stronger/different decoding path than the APK uses.
2. **Dolphin plus sherpa-onnx is clearly better for guaranteed offline availability and consistent ownership of the microphone lifecycle.** It avoids OEM recognition-service availability, hidden network use, and service restart rules.
3. **There is no evidence that the exact bundled Dolphin INT8 CTC export is more accurate than Android/Gboard or Google Cloud for this app.** No direct benchmark exists, and the published Dolphin results cannot be transferred to the CTC export.
4. **sherpa-onnx should be judged as an execution library, not as a recognition model.** Its native libraries can work reliably while the model still mis-transcribes Telugu.
5. **The 295 MiB cost is mostly the model and native binaries, not a guarantee of higher accuracy.** A large multilingual recognizer is inefficient for a small fixed set of medicine intents.
6. **For medicine confirmation, intent safety matters more than verbatim WER.** The most dangerous error is classifying a negative or unclear answer as "taken." The target must be zero false-positive `TAKEN` decisions in the validation set.

## Decision

Do not replace the buttons or claim that Dolphin is better on accuracy based on public evidence.

Recommended production policy:

- Keep the call flow, reminders, prerecorded prompts, and all buttons fully offline.
- Treat buttons as the authoritative path and make them always visible.
- Dolphin may remain as an optional voice convenience only if it passes a controlled family-device benchmark.
- For unclear or conflicting voice results, repeat the question or let the user tap. Never infer `TAKEN` from uncertainty.
- If compact APK size is more important than guaranteed offline voice, remove Dolphin and use Android on-device recognition only opportunistically, with buttons as the guaranteed fallback.
- If guaranteed offline voice is mandatory, first benchmark Dolphin against a Telugu-specific model such as AI4Bharat IndicConformer or a small command/intent model trained on the actual family phrases. Do not ship a new model based only on model-card claims.

## Required acceptance test before choosing a winner

Use the actual parents and their actual phones. Test at least 30 utterances per intent per speaker in quiet conditions and 30 per intent with fan/TV/background speech. Include natural variants, soft speech, mixed Telugu-English, pauses, and follow-up answers.

For every recognizer record:

- intent accuracy by class (`TAKEN`, `NOT_TAKEN`, meal yes/no, wait, remind later, unclear);
- false-positive `TAKEN` count;
- missed-speech/empty-transcript rate;
- median and 95th-percentile response latency;
- RAM, warm-up time, battery use, and crashes;
- installed size and whether behavior remains identical with Wi-Fi and mobile data disabled.

Acceptance gates:

- false-positive `TAKEN`: 0;
- core-intent accuracy: at least 98% on the family test set;
- response starts within 500 ms after endpointing on the target phones;
- no microphone loss, recognizer-busy loop, crash, or network dependency over 100 consecutive calls.

Until such a benchmark passes, the evidence supports this conclusion: **Dolphin/sherpa-onnx is the more controllable offline stack, but it is not proven to be the more accurate Telugu stack.**
