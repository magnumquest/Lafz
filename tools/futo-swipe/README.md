# FUTO Swipe (native swipe decoder)

Lafz decodes swipe typing with [FUTO Swipe](https://swipe.futo.tech) for English, Roman Urdu and Roman Arabic.
Fully offline. If the native library cannot load, `Suggest.recognizeSwipe` falls back to the simple path matcher in `latin/gesture/SwipeDecoder.java`.

| Part | Where | Licence |
|---|---|---|
| Native decoder `libswipe_jni.so` (+ `libc++_shared.so`) | `app/src/main/jniLibs/<abi>/` (built from the source below) | GPL-3.0-or-later |
| Kotlin wrapper `org.futo.ml.inference.SwipeDecoder` | `app/src/main/java/org/futo/ml/inference/` (+ our `nativeLoadTrie/FreeTrie`) | GPL-3.0-or-later |
| Models (encoder, English decoder, English context LM) | `app/src/main/assets/futo_swipe/` | FUTO Model Weights License 1.0 (`LICENSE.md` beside them) |
| Glue: dictionaries, layout normalisation, engines | `latin/gesture/FutoSwipe.kt` | GPL-3.0 |

The weights licence requires a visible "powered by FUTO Swipe" notice: it is in Settings > About and Settings > Gesture typing.

## Rebuilding the native library
Built from `https://gitlab.futo.org/keyboard/swipe-library` at commit `1b13f2c85d6b347f6ea3fbc4b3aaf01fce42429a`
(ExecuTorch submodule `0b0e2c5cdd67c8b4396a46ea1d1aa72ffb0128d7`).

    git clone https://gitlab.futo.org/keyboard/swipe-library.git && cd swipe-library
    git checkout 1b13f2c85d6b347f6ea3fbc4b3aaf01fce42429a
    git apply /path/to/lafz/tools/futo-swipe/lafz-trie-loading.patch     # adds nativeLoadTrie / nativeFreeTrie
    git submodule update --init --recursive                               # large; needs network once
    export ANDROID_NDK_HOME=~/Android/Sdk/ndk/28.0.13004108
    make android                                                          # build-android-<abi>/libswipe_jni.so

Copy each `libswipe_jni.so` plus the NDK's `libc++_shared.so` (`toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/<triple>/`) into `app/src/main/jniLibs/<abi>/`.
Models come from https://huggingface.co/futo-org/futo-swipe (`honorable_sturgeon`, `magic_macaw`, `hungry_jellyfish`).
`app/proguard-rules.pro` keeps `org.futo.ml.inference.**`; the native code builds its result objects through JNI.

## How it is used
* English: encoder + English decoder + context LM, dictionary = bundled en-US main dictionary.
* Roman Urdu/Arabic: encoder only; dictionaries = English words + the language's Roman spellings (`RomanTransliterator.swipeSpellings`), mapped to native script by the existing transliterator.
* Word lists ship prebuilt in `assets/futo_swipe/tries/{en,urdu,arabic}.combined` (AOSP `word=...,f=...` format), so the first load needs no dictionary scan. They were generated on a device by the app itself from `main_en-US.dict` (`SwipeDecoder.englishWords`) and `RomanTransliterator.swipeSpellings()`; **regenerate them if the transliteration models change**: delete `files/futo_swipe/tries/` on a debug build, swipe once, pull the files with `adb exec-out run-as ... cat`. Assets are re-copied after every app update (keyed by versionCode).
* Everything heavy loads on the `FutoSwipeLoader` thread at keyboard start / language switch (`Suggest.preloadSwipeEngine`); the swipe path never loads anything and returns no result until the engine is ready.
* Settings > Gesture typing: engine choice (FUTO, Google library, Lafz built-in) and a diagnostics screen (`SwipeDiagnostics`: persistent log + Android process-exit reasons).
* HeliBoard only handles swipes when a main dictionary exists; `LatinIME` treats Roman-script locales as available.
