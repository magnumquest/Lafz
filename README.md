<p align="center">
  <img src="art/lafz/icon_512.png" alt="Lafz icon" width="128">
</p>

<h1 align="center">Lafz &nbsp;·&nbsp; لفظ</h1>

<p align="center">
  <b>An offline Android keyboard that turns Roman Urdu into Urdu script as you type.</b><br>
  Type <code>mein kal aonga</code>, get <span dir="rtl">میں کل آؤں گا</span>.
</p>

---

## What it does

Lafz is a fork of [HeliBoard](https://github.com/Helium314/HeliBoard) with a built-in **abc → اردو** transliteration engine.
You type Urdu the way you text it, in Latin letters, and the keyboard shows real Urdu words in the suggestion strip and
commits the best one when you press space.

- **Not a fixed phonetic map.** Spelling is free: `kal`, `kl`, `kaal` all find کل. The engine scores dictionary words
  against what you typed, using a language model plus a learned model of how people romanize Urdu.
- **Dictionary-only output.** Every suggestion is a real Urdu word, so gibberish gives nothing instead of nonsense.
- **Whole phrases.** `aonga` → آؤں گا / آؤں گی, `karunga` → کروں گا, and about 1,200 more future-tense verb phrases.
- **English loanwords.** `computer` → کمپیوٹر, `message` → میسج, `laptop` → لیپ ٹاپ.
- **Next-word prediction** and prefix completion while you type.
- **Urdu punctuation.** `.` `?` `,` become `۔` `؟` `،`.
- **Islamic honorifics and terms**, in English, Roman-Urdu and Urdu: `Umar` → Umar Radiallahu Anhu, `Rasul` → Rasulullah ﷺ,
  bismillah, inshallah, jazakallah and more. (The list in `app/src/main/assets/honorifics/honorifics.tsv` is waiting for review by someone with religious knowledge.)
- **English typing is unchanged**, and you can switch languages like any other keyboard.
- **100% offline.** The app does not request the internet permission.
- A Gboard-style look (rounded keys, light and dark themes).

## How accurate is it?

On held-out romanized Urdu sentences from Google's Dakshina dataset: **89.6 % top-1** and **93.4 % top-3**,
at about 3 ms per word on a desktop. Those sentences are Wikipedia-style text, so casual chat will score lower.
It's a work in progress.

## Install

1. Download the latest `Lafz_*-release.apk` from [**Releases**](../../releases/latest) and open it on your phone
   (allow "install unknown apps" if asked).
2. Open Lafz and follow the prompts to enable it and select it as your keyboard.
3. Go to **Settings → Languages & layouts**, add **Urdu (abc → اردو)**, and start typing.

Requires Android 5.0 (API 21) or newer.

## How it works

The method follows Google's paper on [transliterated mobile keyboard input with weighted finite-state transducers](https://research.google/pubs/transliterated-mobile-keyboard-input-via-weighted-finite-state-transducers/)
(Hellsten et al., 2017), using a small custom decoder instead of OpenFst:

```
score(word) = ln P(word | previous word)  +  1.4 · ln P(roman | word)
```

- `P(roman | word)` is a character-alignment channel model learned by EM from human-written romanizations.
- The language model is a unigram plus pruned bigram over 8.8M tokens of Urdu Wikipedia (60k-word vocabulary).
- The whole model ships as a 3.5 MB asset and runs on the device.

The engine is pure Java: `app/src/main/java/helium314/keyboard/latin/urdu/UrduTransliterator.java`.
The data pipeline and evaluation tools are in [`tools/urdu/`](tools/urdu/README.md).

## Build from source

You need JDK 17+ and the Android SDK (with NDK).

```bash
./gradlew assembleDebug                                   # debug APK
./gradlew testDebugUnitTest --tests '*UrduTransliteratorTest*'   # engine tests
tools/build-release.sh                                    # signed release APK in dist/
```

`tools/build-release.sh` creates a signing key at `~/.android/lafz-release.jks` on first run.
Back that key up and never commit it: updates must be signed with the same key.

## Roadmap

- [ ] Better casual-chat accuracy (conversational training data, optional on-device learning)
- [ ] More verb forms (past, continuous, imperative) and slang
- [ ] Settings toggle for the honorific suggestions
- [ ] Offline-first GIF search (the only feature that would ever need the internet)

## License and credits

Lafz is licensed under the **GNU GPL v3.0**, like HeliBoard. See [LICENSE](LICENSE); the Apache 2.0 and CC BY-SA 4.0 notices for AOSP and the icon are in
[LICENSE-Apache-2.0](LICENSE-Apache-2.0) and [LICENSE-CC-BY-SA-4.0](LICENSE-CC-BY-SA-4.0).

- Built on [HeliBoard](https://github.com/Helium314/HeliBoard), itself based on OpenBoard and the Android Open Source Project keyboard. Thanks to their authors.
- The transliteration model is trained on the [Dakshina dataset](https://github.com/google-research-datasets/dakshina) (Google, CC BY-SA 4.0).
  The model file `urdu_translit.bin` is derived from it and carries the same license.
