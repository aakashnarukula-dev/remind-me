# Dolphin Telugu vs previous recognizers

**Research date:** 4 September 2026  
**Project:** Gurthu Chey

## Bottom line

**No—the evidence does not show that the bundled Dolphin Telugu model and speech-recognition native libraries work perfectly, or that they are more accurate than every recognizer previously used by Gurthu Chey.**

They *are* better in one important way: the APK owns the complete recognition path, so voice processing is genuinely offline and does not depend on an OEM speech service, downloaded language pack, Firebase function, or internet connection. But offline control and transcription accuracy are different questions.

The strongest defensible conclusion is:

- **Offline reliability/control:** Dolphin + sherpa-onnx wins.
- **Proven Telugu accuracy:** no winner; the necessary direct benchmark does not exist.
- **Public evidence for this exact mobile model:** weak.
- **Safety-critical medicine confirmation:** keep buttons authoritative; voice must fail closed.

## What is actually in the APK

Gurthu Chey bundles `sherpa-onnx-dolphin-small-ctc-multi-lang-int8-2025-04-02` and sherpa-onnx 1.13.7. The app records 16 kHz mono audio, applies available Android audio processing plus adaptive gain, finds the end of each utterance locally, then runs INT8 Dolphin CTC with greedy search on the phone.

The official package contains a **239 MB model**, before native libraries and the rest of the app. That matches the repository: the model asset is about 238 MiB and the built parent APK is about 295 MiB. [sherpa-onnx model documentation](https://k2-fsa.github.io/sherpa/onnx/Dolphin/pretrained.html)

Important distinction: **sherpa-onnx is the native inference runtime; Dolphin is the recognition model.** Native libraries can make execution stable and fully local, but they cannot turn a weak or mismatched transcript into an accurate one.

## Published Telugu results

The Dolphin paper reports the following Word Error Rate (lower is better) for **Dolphin small**:

| Telugu test set | Dolphin small WER | Whisper small WER | Whisper large-v3 WER |
|---|---:|---:|---:|
| Dataocean internal | 46.2% | 108.7% | 94.6% |
| Common Voice 17 | 62.1% | 163.7% | 80.2% |
| FLEURS | 37.8% | 111.3% | 39.2% |

So Dolphin is clearly stronger than Whisper for Telugu in these author-run tests. However, **37.8–62.1% WER is not close to perfect recognition**. [Dolphin paper and per-language tables](https://arxiv.org/html/2503.20212)

There is an even more important caveat: the paper says these results came from the **attention decoder with beam size 5**, while CTC output was not used. Gurthu Chey uses a **quantized INT8, CTC-only, greedy-search export**. No Telugu WER is published for that exact export. Therefore, the paper cannot be used to claim that the APK's recognizer has the reported accuracy. [Dolphin experimental setup](https://arxiv.org/html/2503.20212)

The authors also list low-latency real-time deployment and model compression as future work, which is further evidence that the research model was not originally validated as this exact mobile product. [Dolphin future work](https://arxiv.org/html/2503.20212)

## Comparison with the previous approaches

| Approach | Offline guarantee | Telugu accuracy evidence | Speed/consistency | Size | Verdict for Gurthu Chey |
|---|---|---|---|---:|---|
| Dolphin INT8 CTC + sherpa-onnx | Yes | No published WER for the exact export; stronger decoder has high Telugu WER | Local and controllable; device benchmark still required | ~295 MiB APK | Good offline engineering path, not proven best recognition |
| Android on-device `SpeechRecognizer` | Only when an on-device service and Telugu model are available | No public device-specific Telugu WER | OEM/service dependent; session lifecycle caused earlier app problems | Small app | Useful opportunistically, not a guaranteed universal offline path |
| Android system recognizer with `EXTRA_PREFER_OFFLINE` | No guarantee | No public device-specific Telugu WER | May use network; recognizer controls endpointing/session behavior | Small app | Cannot meet the strict offline guarantee by itself |
| Google Cloud Speech-to-Text + semantic cloud path | No | Google does not publish an equivalent benchmark for these exact phrases/devices | Strong project results, but internet and multi-second delay | Small app | Best candidate when online quality matters; fails offline requirement |
| Visible response buttons | Yes | Deterministic input | Immediate and device-independent | Negligible | Safest authoritative confirmation path |

Android documents that its generic recognition API may stream audio to servers and is not intended for continuous recognition. The dedicated on-device API works only if an on-device service is available. Android also says `EXTRA_PREFER_OFFLINE` may have no effect depending on the recognizer implementation. [Android `SpeechRecognizer`](https://developer.android.com/reference/android/speech/SpeechRecognizer), [Android `RecognizerIntent`](https://developer.android.com/reference/kotlin/android/speech/RecognizerIntent)

This also explains why Gboard's Telugu microphone can appear excellent while the app's Android recognizer behaves differently: Gboard's keyboard mic is not a documented accuracy or routing guarantee for a third-party app using `SpeechRecognizer`. [Gboard voice typing](https://support.google.com/gboard/answer/2781851?hl=en-IN)

Google Cloud supports Telugu, including Chirp 3 in Preview, but no official apples-to-apples Telugu accuracy result exists for the parents' voices, phones, phrases, and home noise. It also cannot satisfy offline operation. [Cloud Speech-to-Text languages](https://docs.cloud.google.com/speech-to-text/docs/speech-to-text-supported-languages), [Chirp 3](https://docs.cloud.google.com/speech-to-text/docs/models/chirp-3)

## Recommendation

For the app as currently defined:

1. Keep reminders, prerecorded Telugu audio, schedules, retries, and all response buttons fully offline.
2. Keep buttons visible for every question and make them the authoritative path.
3. Let Dolphin voice recognition remain an optional convenience only after it passes a family-device benchmark.
4. Never convert an unclear or contradictory transcript to “medicine taken.” Ask again or wait for a button.
5. If a small APK matters more than guaranteed offline voice, remove Dolphin and use Android recognition only when available, while buttons preserve complete offline operation.
6. If guaranteed offline voice is mandatory, test Dolphin against a Telugu-specific model. AI4Bharat's Telugu IndicConformer is a plausible candidate, but its official model card currently provides neither WER nor a validated lightweight Android deployment, so it is not yet a proven replacement. [AI4Bharat Telugu model](https://huggingface.co/ai4bharat/indicconformer_stt_te_hybrid_ctc_rnnt_large)

## The test that will settle it

Public benchmarks cannot answer the exact product question. A controlled test should use both parents, their phones, and the final phrases:

- at least 30 utterances per intent per speaker in quiet conditions;
- another 30 per intent with a fan, TV, and household speech;
- normal and soft volume, natural Telugu variants, Telugu-English mixing, pauses, and follow-up answers;
- the same recordings tested with Dolphin, Android recognition, and Google Cloud;
- measure intent accuracy, missed speech, false-positive “taken,” median/95th-percentile latency, RAM, crashes, and battery.

The medicine-safety acceptance gate should be **zero false-positive “taken” decisions**. A reasonable product gate is at least 98% core-intent accuracy on the actual family test set, response within 500 ms after endpointing, and no microphone/session failure over 100 consecutive calls.

## Final decision

**Keep Dolphin only for guaranteed offline voice experimentation—not because it has been proven more accurate.** The current evidence supports sherpa-onnx as a solid offline runtime, but does not validate the exact Dolphin INT8 CTC model as superior Telugu recognition. Until a parent/device benchmark proves otherwise, buttons are the only truly deterministic and safe offline confirmation method.
