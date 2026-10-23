# Voice typing for Lafz: research (2026-10-23)

Question from the owner: can a small open-source offline speech model give Lafz a mic key that works for **Urdu, Arabic and English**, on a phone, without hurting normal typing?
Method: web search and model cards only, **no model has been run yet**. Everything below is as reported by the sources linked at the end; where I could not confirm a claim it says so.

## Short answer
* **Arabic: yes, realistic.** A 27M-parameter Moonshine "tiny" Arabic model exists (reported FLEURS WER 20.8 %, Whisper tiny 66 %), MIT per a mirror (verify upstream). Nemotron 3.5 supports Arabic but is 600M.
* **English: yes.** Phonon-2 (164 MB download, best reported accuracy) or Moonshine tiny/base.
* **Urdu: the weak spot.** None of Phonon-2, Nemotron 3.5 or Moonshine's published flavours covers Urdu. Candidates: **Meta Omnilingual ASR** (300M CTC, Apache-2.0, Urdu `urd_Arab` is in its language list, runnable through sherpa-onnx's export), and **Whisper fine-tuned for Urdu** (small ~35 % WER reported on Common Voice; large-v3 fine-tunes ~26 % but 800M+ parameters). No Urdu error rate for Omnilingual-300M was found, so **Urdu quality is unproven**.
* **Cheapest working option today needs no model in our APK:** the voice key that already exists in the toolbar (`ToolbarKey.VOICE`, `RichInputMethodManager.switchToShortcutIme`) hands over to any installed voice IME, e.g. FUTO Voice Input (offline Whisper, open source). It needs nothing from us except documentation.

## Candidates
| Model | Size | Languages relevant to us | Runtime / Android | Licence | Notes |
|---|---|---|---|---|---|
| Phonon-2 (Fermion Research) | 164 MB download (Parakeet TDT 0.6B v3 compressed, ~2.1 bits/weight) | **English only** | CPU engine libs for Android arm64 (API 26+), MLX, Core ML; no ONNX/GGUF mentioned | CC-BY-4.0 (model), Apache-2.0 (code) | Reported avg WER 5.21 % on 7 English sets vs 6.58 % Whisper large-v3-turbo (developer's claim; independent tests mixed); one review reports 1.5-1.8 GB RAM at run time |
| Nemotron 3.5 ASR Streaming (NVIDIA) | 600M | 40 locales incl. **Arabic (transcription-ready)**, Hindi; **no Urdu** | GGUF q8_0 + NeMo-Speech.cpp (C++); no mobile deployment described | OpenMDW-1.1, commercial use OK | Streaming 80 ms-1.1 s chunks; far too big for a keyboard process |
| Moonshine tiny "flavours" | 27M | **Arabic**, plus Chinese, Japanese, Korean, Ukrainian, Vietnamese; English models exist | In sherpa-onnx's supported families | MIT on the mirror, "permissive" in the paper | Arabic FLEURS WER 20.76, Common Voice 17 WER 36.55; 5-15x faster than Whisper |
| Omnilingual ASR (Meta) | 300M to 7B; 300M "for low-power devices" | 1,600+ languages incl. **Urdu**, Arabic | sherpa-onnx lists the family (an `omniASR_CTC_1B` export exists); Apple ports exist | Apache-2.0 | No Urdu error rate found; int8 size and phone speed unmeasured |
| Whisper (tiny/base/small) and Urdu fine-tunes | 39M / 74M / 244M (GGML f16 75 / 142 / 465 MB) | Urdu, Arabic, English | whisper.cpp, sherpa-onnx, FUTO Voice Input | MIT | Urdu fine-tune small: ~35 % WER (Common Voice 16.1); zero-shot small/base weak; noise hurts |
| Vosk | ~50 MB per language | English, Arabic; Urdu not confirmed | Android library | Apache-2.0 | Low latency, lower accuracy |
| sherpa-onnx (runtime) | n/a | runs Whisper, Moonshine, Omnilingual, NeMo/Parakeet, SenseVoice... | Android APKs and build docs | licence not confirmed here | The most plausible single runtime to embed |

## How it would fit Lafz
* **Option A (now, zero cost):** document the existing voice key + FUTO Voice Input. No change to our size, memory or permissions.
* **Option B (embedded, opt-in):** `VoiceTypingService` in a **separate process** (a large model must not live in the keyboard's heap), sherpa-onnx, models side-loaded by the owner or bundled per language; off by default; model loaded only while the mic key is in use. New permission `RECORD_AUDIO` (the app asks for none today; still no INTERNET). Output goes through `commitText`; for Urdu/Arabic it already is native script, so no transliteration step is needed, and the committed words can feed the learning model.
* Streaming vs push-to-talk: Whisper-style models are offline (record, then transcribe, ~1-3 s for a sentence on a recent phone is plausible but unmeasured); Moonshine/Zipformer/Nemotron can stream.

## Unknowns that decide it (need measurement, not more reading)
1. Urdu character/word error of Omnilingual-300M and of the Urdu Whisper fine-tunes on real phone-speech (FLEURS `ur_pk`, Common Voice Urdu test).
2. int8 size, RAM and seconds-per-sentence on the owner's phone (arm64) for each candidate.
3. Licence check of the exact checkpoints before shipping (Moonshine upstream card, Phonon-2 CC-BY-4.0 attribution).

## Recommended next step
A throwaway spike on the desktop (RTX 3090 Ti): run Omnilingual-300M and the best Urdu Whisper fine-tune on FLEURS Urdu and Common Voice Urdu test clips, report CER/WER; then, only if Urdu is good enough (target: CER under ~10-15 %), a sherpa-onnx int8 build timed on the emulator and the phone. If Urdu fails the gate, ship Arabic + English voice (Moonshine) and leave Urdu to Option A.

## Sources
* Phonon-2: [Hugging Face model page](https://huggingface.co/FermionResearch/Phonon-2/tree/main), [review](https://www.stork.ai/blog/this-164-mb-ai-transcriber-has-a-catch), [vs Whisper](https://www.stork.ai/blog/phonon-2-is-tiny-are-its-whisper-claims-real)
* Nemotron 3.5 ASR: [model card](https://huggingface.co/nvidia/nemotron-3.5-asr-streaming-0.6b)
* Moonshine flavours: [paper](https://arxiv.org/html/2509.02523v1), [Arabic tiny GGUF card](https://huggingface.co/handy-computer/moonshine-tiny-ar-gguf)
* Omnilingual ASR: [paper abstract](https://arxiv.org/abs/2511.09690), [Soniqo guide (language list, Apple ports)](https://soniqo.audio/guides/omnilingual)
* Urdu Whisper: [fine-tune project](https://github.com/muhammadnaveedgurmani/urdu-asr), [whisper-small-urdu-v2](https://huggingface.co/hamza-amin/whisper-small-urdu-v2), [large-v3 Urdu](https://huggingface.co/kingabzpro/whisper-large-v3-urdu), [lightweight Whisper for Urdu](https://arxiv.org/pdf/2508.09865)
* Runtime and apps: [sherpa-onnx docs](https://k2-fsa.github.io/sherpa/onnx/index.html), [FUTO Voice Input review](https://korben.info/en/futo-voice-input-local-voice-dictation-mobile.html), [Whisper model sizes for FUTO](https://gist.github.com/danielrosehill/8de84be9e906b5a2d3b94e554e8961af), [Vosk](https://alphacephei.com/vosk/)
