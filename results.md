# Arabic (abc → عربي) test results: Qur'an / Hadith typing

Tested 2026-10-13 on the Android emulator (Pixel_API36, debug build `Lafz_0.1.0-debug.apk`, HEAD `740e52aa` plus untracked files).
Field: SMS compose box. Keys were tapped one at a time. The apostrophe was typed by long-pressing the `c` key.
"Strip" = suggestion strip. "Best" = the bold, auto-committed candidate.

## Summary

Single words work well. Anything where the owner's usual spelling glues two Arabic words together (`al'amal`, `innamal'amal`, `ma'al'usri`, `binniyyaat`) does not give the right text. The decoder maps one Roman token to one dictionary entry. Arabic writes `ال` + noun and `ب`/`مع` + `ال` as separate words, or as a prefix on a noun that the vocabulary may not contain.

## Inputs tried

| # | Typed (owner style) | Expected | Result | Verdict |
|---|---|---|---|---|
| 1 | `inna ma'al'usri yusra␣` (94:6) | إن مع العسر يسرا | `إن ma'al'usri يسرا` (middle token stays Roman) | FAIL |
| 2 | `innama al'amalu binniyyaat␣` | إنما الأعمال بالنيات | `إنما العمل binniyyaat` | FAIL |
| 3 | `innamal'amal` (alone) | إنما الأعمال | strip: النمل (best), أناملة, `innamal` | FAIL |

## Per-token breakdown (isolated, to find the cause)

| Typed | Strip while typing (best in **bold**) | After space | Notes |
|---|---|---|---|
| `ma'a` | ماء, **مع**, معه, معا, مائة, `ma'a` | **مع** ✅ | the apostrophe as ʿayn works |
| `ma'al'usri` | معالم, `ma'al`, **مآل**, معلق, معلم, معلوم | stays `ma'al'usri` (Roman) ❌ | no good candidate. The correct pair "مع العسر" is not offered, and neither is "معالعسر" |
| `al'amalu` | العمال, الأعمال, **العمل**, `al'amalu` | العمل ❌ | the correct الأعمال is 2nd. The best is the wrong word. Probably a unigram frequency effect (العمل is much more common) |
| `innamal'amal` | النمل, أناملة, `innamal` | n/a | the correct two-word result إنما الأعمال is never offered |
| `binniyyaat` | بأنني, بانني, بناني | stays `binniyyaat` (Roman) ❌ | the correct بالنيات is not offered. The strip shows the shape `bi`+`n`+`niyyaat`, which resembles ب+ال+نيات (the "l" assimilated to "n") |
| `niyyaat` | n/a | نيات (not النيات) | OK as a dictionary word, but النيات is the form in the hadith |
| `inna` (first word of #1) | n/a | إن ✅ | |
| `innama` (first word of #2) | n/a | إنما ✅ | |

## Issues to fix (suggested order)

1. **Definite-article / prefix fusion is missing.** The owner types `al'amal`, `binniyyaat`, `lillah`, `wal…` as one token.
   - Give the decoder a prefix mode: split off known proclitics (ال, بال, وال, لل, ب, ل, و, ف, ك), decode the rest, and reattach.
   - It should also handle the sun-letter assimilation in Roman: `bin niyyaat` = بالنيات, `ash shams` = الشمس.
   - Alternatively add these as generated multi-word or prefixed vocabulary entries. The Urdu side already supports phrase entries that contain a space.
2. **Two-word glued input (`innamal'amal`, `ma'al'usri`).** Offer multi-word candidates (word + word) when no single-word candidate scores well. Split the token at plausible boundaries and run the same decoder over each half. Most useful for the common Qur'anic and hadith collocations that the bigrams already contain, such as مع العسر and إنما الأعمال.
3. **Failure shows nothing useful and the Roman text is committed.** `ma'al'usri` and `binniyyaat` were committed as Roman text on space. The strip still showed Arabic alternatives, but none was applied. Consider auto-committing the top Arabic candidate anyway for Arabic mode, or at least keeping the Roman word while making the strip's best slot obvious. This is a design decision for the owner.
4. **Ranking of الأعمال vs العمل** for `al'amalu`. The scripture-weighted LM should rank الأعمال first when the previous word is إنما (the hadith bigram). In test #2, "العمل" won even though the previous word was إنما. Check that bigram context really reaches the decoder when the previous token is a committed phrase, and check bigram coverage for إنما → الأعمال.
5. **Without apostrophes** (earlier run, plain letters `inna maa al usri yusra`) gave `إن ما الله أسري يسرا` and `innama al amalu bin niyyat` gave `إنما الله أمل بن نية`. Mapping bare `al` to الله is wrong when the next word is a noun. It should prefer ال. Related to issue 1.

## What did work

- `salam` → السلام عليكم / سلام, `kitab` → كتاب (6-slot strip), `ma'a` → مع, `yusra` → يسرا, `inna` → إن, `innama` → إنما.
- The keyboard did not crash and showed no lag in these tests. The Arabic punctuation key `،` and the Arabic-Indic digit hints are present.

## How to repeat

Build: `. ~/.android-env.sh; ./gradlew assembleDebug`. Boot a private emulator: `emulator -avd Pixel_API36 -port 5580 -read-only -no-snapshot -gpu swiftshader_indirect`. A read-only boot loses settings each time, so re-enable "Arabic (abc → عربي)" in Languages & Layouts, open an SMS compose box, and long-press the space bar to pick the Arabic subtype.

The tap script used for typing is at `/tmp/claude-1000/-home-thomas-AI-Works-PersonalBoard/16510f07-be2d-4c61-8548-1dda43655000/scratchpad/typ.sh`. It maps letters to key coordinates for a 1080x2400 screen.

Not yet tested: Arabizi digits (`ma3a al3usri`), honorific suggestions in Arabic, typing speed on a real phone, and the Urdu keyboard on these inputs.
