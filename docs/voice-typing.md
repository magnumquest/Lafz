# Continuous offline dictation

Enable **Settings → Voice typing → Dictate with the microphone key** and import the existing Urdu, English and Arabic model folders. The switch remains off by default.

The microphone occupies a bottom-row slot to the left of the emoji key and space bar on phone layouts. It takes width from the space bar, leaving the suggestion strip intact. It is absent when dictation is disabled, the editor disallows microphone input, or the device is locked. External voice keyboards are not required.

Press and hold the microphone, speak, then release to finish and insert the text. Recording starts on touch-down; no second tap is required. The microphone owns that touch, so drifting onto another key does not type a character or open a long-press popup.

Slide upward at least 48 dp while holding to lock recording. A haptic pulse and a small lock badge confirm the change; lifting the finger then keeps recording. Tap the microphone to finish a locked recording. Before locking, an upward arrow on the active mic indicates the gesture. Normal held recording buffers completed text until release; locked recording inserts phrases as they become ready. The microphone is red while recording. When capture stops, it changes to three processing dots until queued recognition finishes. Silence does not end a held or locked recording.

Recognition still runs in the background at phrase pauses (about 650 ms), keeping release-to-insert latency low for phrases already decoded. The final unfinished phrase is submitted on release. Text that is still being decoded appears when the model finishes; release does not eliminate inference time. Toolbar/accessibility activation without a held touch uses locked recording.

## Implementation

- `VoiceSegmenter` consumes 20 ms PCM frames at 16 kHz. It keeps 200 ms of pre-roll, retains trailing silence, rejects isolated clicks, and bounds a phrase to 20 seconds. After 12 seconds it can use a shorter 240 ms pause to avoid cutting continuous speech at the hard limit.
- Capture, service control and inference have separate threads. One inference executor owns model loading, transcription and destruction, preventing concurrent access to Whisper/ONNX native state. Cancel interrupts Whisper CPU encoder/decoder graphs and ONNX runs through a per-recording token; model destruction remains on the inference executor.
- There is one loaded model for the selected keyboard language. Preloading and subsequent mic taps reuse it; language changes replace it. Dictation disabled/unbinding releases it. Model weights, decoder settings and four-thread default are unchanged.
- `VoiceKeyGesture` captures only touches that begin on the built-in microphone. It owns the gesture through release/cancel, consumes extra-finger events, and leaves normal keys and external voice IMEs on their existing touch path.
- Phrase results carry a session ID. Cancellation, changing text fields and changing languages suppress stale results. A rapid stop before binding completes is delivered after the queued start.
- If inference falls six phrases or 40 seconds of outstanding audio behind, capture ends with an explicit notice and queued phrases drain. The last submitted phrase can cross the duration threshold by at most 20 seconds. Long dictation otherwise has no overall 28-second recording limit.
- Partial PCM frames are split at the hard buffer boundary without dropping their remainder. A short tail after a hard speech split is retained.
- Errors and voice-process disconnection recover already decoded held text; deliberate cancellation still discards it. Terminal service replies follow microphone release.
- Timing logs include phrase audio duration, inference and cancellation time, without logging dictated text.

This is phrase-by-phrase recognition using the existing batch models. It does not repeatedly decode an expanding recording or fabricate word-level streaming. Inference latency still depends on the model and phone; endpointing uses audio energy rather than a separate neural VAD model. Room noise and very long uninterrupted speech need real-phone validation.

## Verification

Run the local offline suite (which excludes existing upstream expected failures and network link probes):

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest --offline -I tools/offline-tests.gradle
```

Regression tests cover phrase boundaries, soft speech, click/silence rejection, pre-roll, hesitation, bounded continuous audio, press/release, swipe-up lock, sideways drift, multi-touch, touch cancellation, buffering until release, locked streaming, cancelled-session isolation, early release before service connection, and microphone layout/settings guards.

Debug builds accept `files/voice_inject.pcm` (mono 16 kHz signed little-endian PCM16) in place of microphone input. It is replayed in real time through the same capture/segment/inference path and ends at EOF. Remove that file before testing the real microphone. Release builds ignore it.

Final emulator check (2026-11-12, debug build, replayed existing test WAVs): English inserted two phrases in order while capture continued, at 1,379/1,436 ms inference for 7,460/6,800 ms of audio. Urdu produced Urdu-script output at 6,050 ms for 7,500 ms of audio. Arabic produced Arabic-script output at 2,020 ms for 8,080 ms of audio. Logs confirmed one English load across repeated mic use and model replacement only on language changes. These are emulator timings, not phone benchmarks or an accuracy evaluation. The previous screenshot verified full-width suggestions and an enabled microphone without an external voice IME. The subsequent push-to-talk update moves phone key order to mic → emoji → space.

Push-to-talk emulator verification: an 18-second hold kept the editor empty while the sentence decoded in the background; release inserted it without another tap. Swipe-up locking kept recording after finger release, inserted decoded text while locked, and displayed the lock badge through a pause longer than eight seconds. A subsequent tap stopped recording and cleared the badge. Offline suite: 298 tests, zero failures.

## Review fixes and tuning screen (2026-11-12)

Background-noise detection is explicitly deferred: the existing RMS/noise-floor and peak gates are unchanged. Buffer-boundary repair is independent of those gates.

A local CPU screening run compared the existing decoder against a shorter audio context (at least 768 frames, otherwise audio length plus two seconds rounded to 64 frames) and disabled temperature fallback on eight English and eight Urdu clips. Aggregate character error excluding punctuation/whitespace was English 1.75% for both contexts, Urdu 9.08% default versus 8.46% shorter context. However, shorter context increased errors on one English and two Urdu clips. Disabling fallback did not change these transcripts and gave little timing benefit. Decoder defaults therefore remain unchanged. Desktop runs include model loading and overlapped Android builds; they are screening results, not phone speed measurements. Reproduce with `tools/voice-spike/benchmark_whisper_options.py --help`.

Whisper cancellation now propagates to GGML CPU graph backends as well as Whisper callbacks; each graph clears the callback before its per-run flag can be freed. ONNX uses a fresh RunOptions termination flag per run. Resource unregistration is serialized with cancellation to prevent use after free.

Cancellation checks on the private emulator: Urdu Whisper returned about 0.46 s after the cancellation request, and its still-loaded model then produced Urdu text successfully. Arabic ONNX returned about 0.38 s after the request and the same loaded session subsequently completed a full Arabic clip without a reload. These include ADB/UI overhead and are operation checks, not phone benchmarks. Processing dots were verified visually. A first multilingual test run hit the keyboard process's 192 MiB Java heap limit after cycling English/Urdu/Arabic; the separate voice process survived, and Arabic was retested in a fresh keyboard process. This keyboard heap issue is separate from native voice-run cancellation and remains a real-phone/multilingual follow-up.

Final validation: debug and release builds plus release vital lint pass; offline suite 309 tests, zero failures. Local signed phone preview: `dist/Lafz_0.6.6-voice-fixes-preview.apk`, existing release certificate and application ID.
