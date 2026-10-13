<p align="center">
  <img src="art/lafz/icon_512.png" alt="Lafz icon" width="128">
</p>

<h1 align="center">Lafz &nbsp;·&nbsp; لفظ</h1>

<p align="center">
  <b>An offline Android keyboard that turns Roman Urdu and Roman Arabic into native script as you type.</b><br>
  Type <code>mein kal aonga</code>, get <span dir="rtl">میں کل آؤں گا</span>.
</p>

---

## What it does

Lafz is a fork of [HeliBoard](https://github.com/Helium314/HeliBoard) with built-in **abc → اردو** and **abc → عربي** transliteration engines.
You type the way you text, in Latin letters. The text field shows the native script **while you type** (like Gboard), the best
word replaces it as the decoder settles, and space commits what you see. The suggestion strip offers alternatives.

- **Live transliteration (Urdu and Arabic).** Typing `kiya` shows کیا immediately, then the model's best word. Words the model
  doesn't know stay in Roman letters, so English words still work. Turn it off in Settings → Text correction ("Live transliteration while typing").
- **Arabic (abc → عربي).** Trained on Qur'an and Hadith text plus real Arabizi chat (digits like `3 7 5 9` work as letters).
  `innama alamalu binniyyat` → إنما الأعمال بالنيات. Prefixes like و ف ب ل ال join the next word automatically.
- **Native numerals and punctuation** on the symbol page: Arabic-Indic digits ٠١٢٣ with ، ؛ ؟ for Arabic, Urdu digits ۰۱۲۳ with ۔ ، ؟ for Urdu.
  (Number/phone fields still show Western digits.)
- **Fast typing.** Suggestions are computed off the UI thread, so keys never wait for the decoder.

- **Not a fixed phonetic map.** Spelling is free: `kal`, `kl`, `kaal` all find کل. The engine scores dictionary words
  against what you typed, using a language model plus a learned model of how people romanize Urdu.
- **Dictionary-only output.** Every suggestion is a real Urdu word, so gibberish gives nothing instead of nonsense.
- **Whole phrases.** `aonga` → آؤں گا / آؤں گی, `karunga` → کروں گا, and about 1,200 more future-tense verb phrases.
- **English loanwords.** `computer` → کمپیوٹر, `message` → میسج, `laptop` → لیپ ٹاپ.
- **Next-word prediction** and prefix completion while you type.
- **Native punctuation.** `.` `?` `,` become `۔` `؟` `،`.
- **Islamic honorifics and terms**, in English, Roman-Urdu and Urdu: `Umar` → Umar Radiallahu Anhu, `Rasul` → Rasulullah ﷺ,
  bismillah, inshallah, jazakallah and more. (The list in `app/src/main/assets/honorifics/honorifics.tsv` is waiting for review by someone with religious knowledge.)
- **English typing is unchanged**, and you can switch languages like any other keyboard.
- **100% offline.** The app does not request the internet permission.
- A Gboard-style look (rounded keys, light and dark themes).

## How accurate is it?

Urdu, on held-out romanized sentences from Google's Dakshina dataset: **89.6 % top-1** and **93.4 % top-3**,
at about 1 ms per word on a desktop (Arabic about 1.6 ms). Those sentences are Wikipedia-style text, so casual chat will score lower.
It's a work in progress.

## Install

1. Download the latest `Lafz_*-release.apk` from [**Releases**](../../releases/latest) and open it on your phone
   (allow "install unknown apps" if asked).
2. Open Lafz and follow the prompts to enable it and select it as your keyboard.
3. Go to **Settings → Languages & layouts**, add **Urdu (abc → اردو)** and/or **Arabic (abc → عربي)**, and start typing.

Requires Android 5.0 (API 21) or newer.

## How it works

The method follows Google's paper on [transliterated mobile keyboard input with weighted finite-state transducers](https://research.google/pubs/transliterated-mobile-keyboard-input-via-weighted-finite-state-transducers/)
(Hellsten et al., 2017), using a small custom decoder instead of OpenFst:

```
score(word) = ln P(word | previous word)  +  1.4 · ln P(roman | word)
```

- `P(roman | word)` is a character-alignment channel model learned by EM from human-written romanizations.
- Urdu: unigram plus pruned bigram over 8.8M tokens of Urdu Wikipedia (60k words), a 3.5 MB asset.
- Arabic: unigram, bigram and trigram model over Qur'an, Hadith and web text (400k words, about 2.7B tokens counted), a 37 MB asset.
  It can also glue up to three words together, so `wakitab` finds و كتاب.
- Everything runs on the device.

The engine is pure Java: `app/src/main/java/helium314/keyboard/latin/urdu/RomanTransliterator.java`.
The data pipelines and evaluation tools are in [`tools/urdu/`](tools/urdu/README.md) and [`tools/arabic/`](tools/arabic/README.md).

## Build from source

You need JDK 17+ and the Android SDK (with NDK).

```bash
./gradlew assembleDebug                                   # debug APK
./gradlew testDebugUnitTest --tests '*RomanTransliteratorTest*'   # engine tests
tools/build-release.sh                                    # signed release APK in dist/
```

`tools/build-release.sh` creates a signing key at `~/.android/lafz-release.jks` on first run.
Back that key up and never commit it: updates must be signed with the same key.

## Roadmap

- [ ] Better casual-chat accuracy (conversational training data, optional on-device learning)
- [ ] More verb forms (past, continuous, imperative) and slang
- [ ] Native digits in number/phone fields
- [ ] Settings toggle for the honorific suggestions
- [ ] Offline-first GIF search (the only feature that would ever need the internet)

## License and credits

Lafz is licensed under the **GNU GPL v3.0**, like HeliBoard. See [LICENSE](LICENSE); the Apache 2.0 and CC BY-SA 4.0 notices for AOSP and the icon are in
[LICENSE-Apache-2.0](LICENSE-Apache-2.0) and [LICENSE-CC-BY-SA-4.0](LICENSE-CC-BY-SA-4.0).

- Built on [HeliBoard](https://github.com/Helium314/HeliBoard), itself based on OpenBoard and the Android Open Source Project keyboard. Thanks to their authors.
- The transliteration model is trained on the [Dakshina dataset](https://github.com/google-research-datasets/dakshina) (Google, CC BY-SA 4.0).
  The model file `urdu_translit.bin` is derived from it and carries the same license.
- The Arabic model is derived from Qur'an, Hadith and web-corpus text (see [`tools/arabic/README.md`](tools/arabic/README.md) for sources).
  The Arabic honorific column needs review by someone with religious knowledge.
