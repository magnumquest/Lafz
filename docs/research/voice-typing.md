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


## Measured results (2026-10-23, desktop; Google FLEURS read speech, clean audio)
Urdu = `ur_pk` test, 299 clips; Arabic = `ar_eg` test, first 150 clips. CER/WER after normalising punctuation, diacritics and letter variants. RTF = processing time / audio time.
Scripts: `tools/voice-spike/`. **These are desktop numbers on clean read speech, not a phone and not chat-style speech: expect worse in noise.**

| Language | Model | Params / file | CER | WER | Speed (desktop) | Licence |
|---|---|---|---|---|---|---|
| Urdu | **Whisper-small, Urdu fine-tune** (hamza-amin/whisper-small-urdu-v2, Common Voice 16.1) | 244M / ~465 MB fp16 | **9.9 %** | **26.3 %** | RTF 0.012 (GPU) | Apache-2.0 |
| Urdu | Whisper-small, stock | 244M / ~465 MB | 16.3 % | 40.5 % | RTF 0.013 (GPU) | Apache-2.0 |
| Urdu | Omnilingual-300M CTC + Devanagari->Roman + Lafz decoder | 1.3 GB fp32 | 21.5 % | 41.1 % | RTF 0.11 (CPU, 4 threads) | Apache-2.0 |
| Urdu | same, int8 | 328 MB | 29.5 % | 47.6 % | RTF 0.22 (CPU) | Apache-2.0 |
| Arabic | **Omnilingual-300M CTC, int8** | 328 MB | **4.9 %** | **16.9 %** | RTF 0.22 (CPU, 4 threads) | Apache-2.0 |
| Arabic | Omnilingual-300M CTC, fp32 | 1.3 GB | 4.8 % | 16.4 % | RTF 0.12 (CPU, 4 threads) | Apache-2.0 |
| Arabic | Whisper-small, stock | 244M / ~465 MB | 8.3 % | 26.0 % | RTF 0.010 (GPU) | Apache-2.0 |
| Arabic | Moonshine-tiny-ar | 27M / 108 MB | 13.5 % | 24.0 % | RTF 0.065 (GPU, one clip at a time) | **Moonshine AI Community License** (research / limited commercial; not MIT) |

### Sweep (2026-10-23/24): more Urdu fine-tunes and English
Same method; English = LibriSpeech-style FLEURS `en_us` test, first 200 clips. Whisper runs fp16 on GPU (sizes are the fp32 safetensors). Script: `tools/voice-spike/sweep.py`.

| Language | Model | Size | CER | WER |
|---|---|---|---|---|
| Urdu | khawajaaliarshad/whisper-small-urdu | 967 MB | **9.6 %** | **25.6 %** |
| Urdu | hamza-amin/whisper-small-urdu-v2 (above) | ~967 MB | 9.9 % | 26.3 % |
| Urdu | kingabzpro/whisper-base-urdu-full | 290 MB | 15.2 % | 39.3 % |
| Urdu | humairmunirawn/whisper-urdu-base | 290 MB | 21.7 % | 50.2 % |
| Urdu | sharjeel103/whisper-tiny-urdu | 151 MB | 19.7 % | 47.1 % |
| Urdu | kingabzpro/whisper-tiny-urdu | 151 MB | 19.0 % | 47.5 % |
| Urdu | UmarRamzan/w2v2-bert-urdu (CTC) | 2.4 GB | 98.2 % | 99.9 % (unusable as published) |
| English | Whisper small.en | 967 MB | **3.0 %** | **6.1 %** |
| English | Whisper base.en | 290 MB | 4.0 % | 8.1 % |
| English | Whisper tiny.en | 151 MB | 5.6 % | 11.5 % |
| English | Moonshine base | 246 MB | 7.8 % | 12.2 % |
| English | Moonshine tiny | 108 MB | 7.0 % | 14.0 % |
| English | wav2vec2-large-960h-lv60-self (CTC) | 1.26 GB | 5.4 % | 11.8 % |
| English | wav2vec2-base-960h (CTC) | 378 MB | 8.0 % | 18.6 % |
| Urdu | shaeel12 Moonshine base Urdu | 246 MB | 63.5 % | 87.1 % (unusable) |
| Urdu | shaeel12 Moonshine tiny Urdu | 108 MB | 86.8 % | 123 % (unusable) |

* Urdu accuracy scales with size: tiny/base fine-tunes are too weak (39-47 % WER); only the small class (~26 % WER) is usable, and that needs a quantised file (q5/int8 roughly 250-500 MB) to be phone-friendly.
* English: Whisper base.en is the sweet spot (8.1 % WER at 290 MB fp32); small.en is best but heavy. Moonshine is smaller but not more accurate than Whisper at equal size.
* English wav2vec2 CTC models are worse than Whisper at equal size, so no reason to prefer them. Moonshine Urdu fine-tunes are unusable.
* Skipped on purpose (too large to ship, no value as a ceiling): whisper-large-v3-turbo-urdu (3 GB), XLS-R 300M Urdu and xlsr-53 English (language-model files broken).
* **Conclusion:** Whisper is the engine family for Urdu (small fine-tune, quantised) and English (base.en). Arabic stays on Omnilingual CTC int8.

### whisper.cpp (GGML) quantised: accuracy holds (2026-10-24)
whisper.cpp built on the desktop, models converted with `convert-h5-to-ggml.py` (needs `tools/voice-spike/convert_run.py` here because of the CUDA-12 flash_attn import) and quantised with `whisper-quantize`. Evaluated with `tools/voice-spike/eval_ggml.py` on the same FLEURS sets, CPU only, 8 threads, whisper-cli defaults (beam search).

| Language | Model | File | CER | WER | Desktop CPU RTF |
|---|---|---|---|---|---|
| Urdu | whisper-small-urdu, **q5_1** | **190 MB** (f16 488 MB) | 9.5 % | **25.2 %** | 0.35 |
| English | base.en, **q5_1** | **60 MB** (f16 148 MB) | 3.7 % | **7.3 %** | 0.07 |

* q5_1 loses nothing measurable against the unquantised models (25.6 % / 8.1 %), so q8_0 was not tested (rule: only if q5 were clearly worse).
* Decision: engine = whisper.cpp; ship sizes are 190 MB (Urdu) and 60 MB (English), side-loaded like the Arabic model. Phone speed: see "Phone results" below (Urdu x0.79, usable but the slowest).

### Whisper in the app (whisper.cpp, emulator speed test, 2026-10-24)
`:whisper` module (vendored whisper.cpp 1.9.5, ggml CPU backends built per instruction-set level and picked at run time), model type `whisper_cpp` in `model.json`. x86_64 emulator, 4 threads, 10 s sample clip, not a phone:

| Model | Load | 10 s clip | Peak memory over baseline | Error on the clip |
|---|---|---|---|---|
| English base.en q5_1 (56 MB) | 0.1 s | 1.6 s (x0.16) | 118 MB | 0 % |
| Urdu small q5_1 (181 MB), beam 5 | 0.2 s | 7.5 s (x0.75) | 435 MB | 7 % |
| Urdu small q5_1, greedy (`"beam": 1`) | 0.2 s | 5.4 s (x0.54) | 318 MB | 7 % (same text) |

Memory is fully released after unloading. Greedy is the likely default for dictation; the choice needs a real-phone run and a WER check with beam 1.

### Phone results (Nothing A059, Android 16, arm64, 8 cores, 11.4 GB RAM, 2026-10-24)
Full test, 8 FLEURS clips per language (~80 s of audio each), file `lafz_voice_results_20261024_122718.txt`. whisper.cpp picked the best CPU variant (NEON, FP16, DOTPROD, MATMUL_INT8).

| Model | Setting | Speed (x real time) | WER / CER | Peak memory over baseline |
|---|---|---|---|---|
| Arabic Omnilingual-300M int8 | 4 threads | x0.18 | 20.0 % / 6.6 % | +771 MB |
| Arabic | 8 threads | x0.24 | 20.0 % / 6.6 % | +673 MB |
| English base.en q5_1 | beam 5, 4 threads | x0.22 | 9.5 % / 2.2 % | +129 MB |
| English | greedy, 4 threads | x0.19 | 9.5 % / 2.2 % | +127 MB |
| English | greedy, 8 threads | x0.47 | 9.5 % / 2.2 % | +138 MB |
| Urdu small q5_1 | beam 5, 4 threads | x0.98 | 24.6 % / 7.5 % | +430 MB |
| Urdu | greedy, 4 threads | x0.79 | 25.0 % / 7.6 % | +315 MB |
| Urdu | greedy, 8 threads | x5.12 | 23.7 % / 7.3 % | +331 MB |

* **4 threads beat 8 for every model** (Urdu 8 threads: x5.1, probably work landing on the slow cores). Memory returns to baseline after unloading.
* **Greedy is as accurate as beam 5** (Urdu 25.0 vs 24.6 %, English identical) and 20-25 % faster.
* **Defaults chosen:** greedy (`"beam": 1`, `VoiceModels.DEFAULT_BEAM`) and 4 threads (`VoiceModels.defaultThreads`). The full test now compares greedy against beam 5.
* **Urdu is the limit:** x0.79 means a 10 s dictation takes ~8 s after the user stops talking. Arabic and English are comfortable. Ideas, not tried: 2-3 threads, transcribing in chunks while the user speaks.

### Is there something better for this phone? (web check, 2026-10-24, model-card numbers are self-reported)
* Urdu fine-tunes on Hugging Face: [BuzzASR/urdu](https://huggingface.co/BuzzASR/urdu) (Whisper large-v3, ~2B params) FLEURS 21.8 % WER / 7.5 % CER; [kingabzpro/whisper-large-v3-turbo-urdu](https://huggingface.co/kingabzpro/whisper-large-v3-turbo-urdu) 26.2 % on Common Voice 17; [ihanif/whisper-medium-urdu](https://huggingface.co/ihanif/whisper-medium-urdu) 27.0 % on Common Voice 11; [hamza-amin/whisper-small-urdu-v2](https://huggingface.co/hamza-amin/whisper-small-urdu-v2) 35.4 % on Common Voice 16 (our measurement on FLEURS: 26.3 %).
  Our small fine-tune (25 % WER on FLEURS) is within ~3 points of the best large model, which is 4-10x bigger (turbo ~800M, large-v3 ~1.5-2B) and would not run in real time on this phone, since the small model already needs x0.79. No Urdu model that is both smaller than Whisper-small and better was found.
* [mahwizzzz/aurix-v1](https://huggingface.co/mahwizzzz/aurix-v1) (turbo size) outputs IPA phonemes and has 40 % WER on FLEURS: not usable.
* Moonshine flavours ([paper](https://arxiv.org/abs/2509.02523)) cover Arabic, Chinese, Japanese, Korean, Ukrainian, Vietnamese: no Urdu. No Arabic or English model found that beats the current ones at this size; English small.en (3.0 / 6.1 % on desktop) would be the upgrade if more accuracy than base.en is wanted (about 3x the compute).
* Conclusion: current choices are the best available for this device. Revisit when a distilled or turbo-size Urdu fine-tune, or an Urdu Moonshine, appears.

What this says:
* **Urdu: use the Urdu Whisper fine-tune.** It writes Urdu script directly, so **no Roman conversion is needed and any keyboard layout (Urdu Pakistan, Roman Urdu, English) can host the mic key**; the text is simply inserted.
  The Omnilingual CTC model writes Urdu speech in Devanagari (Hindi script), which is why I tried the Roman/Lafz route: it works (21.5 % CER) but is clearly worse, and int8 hurts it further.
* **Arabic: Omnilingual-300M CTC int8** is the best (4.9 % CER), fast (non-autoregressive) and loses almost nothing from int8. Moonshine-tiny is attractive for its size but its licence is restrictive.
* English was not measured. Phonon-2 (English only) is the strongest candidate on paper; Whisper-small / Moonshine-tiny-en are the fallbacks.

## Running it on a phone: memory, delivery, speed (estimates, not measured on a phone)
* **Memory:** a 330-465 MB model loaded only while dictating is fine on a 6 GB+ phone, but working memory adds to the file size (budget ~0.8-1.2 GB peak for Whisper-small). It must run in its own process, not the keyboard's, and be released shortly after use.
* **Delivery:** too big for the APK, and Lafz has no INTERNET permission. Side-load the model files into app storage (like "Load gesture typing library") or use one explicit opt-in download.
* **Speed is the open question:** Whisper always processes a 30 s window, so a short sentence still costs a few seconds on a phone CPU; Omnilingual CTC is non-autoregressive and cheaper per second of audio. Needs a timing run on the owner's arm64 phone (USB debugging, or a small test APK).
* New permission needed: `RECORD_AUDIO`. Everything behind a Settings switch, off by default; model loaded only when the mic key is pressed.

## Verdict
Feasible for **Arabic** (clearly good) and **Urdu** (good with the Whisper fine-tune, ~26 % WER on clean speech). Next: a phone timing run, then a `VoiceTypingService` prototype behind a switch.

## Original plan (before measuring)
A throwaway spike on the desktop (RTX 3090 Ti): run Omnilingual-300M and the best Urdu Whisper fine-tune on FLEURS Urdu and Common Voice Urdu test clips, report CER/WER; then, only if Urdu is good enough (target: CER under ~10-15 %), a sherpa-onnx int8 build timed on the emulator and the phone. If Urdu fails the gate, ship Arabic + English voice (Moonshine) and leave Urdu to Option A.

## Sources
* Phonon-2: [Hugging Face model page](https://huggingface.co/FermionResearch/Phonon-2/tree/main), [review](https://www.stork.ai/blog/this-164-mb-ai-transcriber-has-a-catch), [vs Whisper](https://www.stork.ai/blog/phonon-2-is-tiny-are-its-whisper-claims-real)
* Nemotron 3.5 ASR: [model card](https://huggingface.co/nvidia/nemotron-3.5-asr-streaming-0.6b)
* Moonshine flavours: [paper](https://arxiv.org/html/2509.02523v1), [Arabic tiny GGUF card](https://huggingface.co/handy-computer/moonshine-tiny-ar-gguf)
* Omnilingual ASR: [paper abstract](https://arxiv.org/abs/2511.09690), [Soniqo guide (language list, Apple ports)](https://soniqo.audio/guides/omnilingual)
* Urdu Whisper: [fine-tune project](https://github.com/muhammadnaveedgurmani/urdu-asr), [whisper-small-urdu-v2](https://huggingface.co/hamza-amin/whisper-small-urdu-v2), [large-v3 Urdu](https://huggingface.co/kingabzpro/whisper-large-v3-urdu), [lightweight Whisper for Urdu](https://arxiv.org/pdf/2508.09865)
* Runtime and apps: [sherpa-onnx docs](https://k2-fsa.github.io/sherpa/onnx/index.html), [FUTO Voice Input review](https://korben.info/en/futo-voice-input-local-voice-dictation-mobile.html), [Whisper model sizes for FUTO](https://gist.github.com/danielrosehill/8de84be9e906b5a2d3b94e554e8961af), [Vosk](https://alphacephei.com/vosk/)
