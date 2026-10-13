# CLAUDE.md — Lafz (Urdu transliteration keyboard, fork of HeliBoard)

Read this first. It is the handoff from the cloud session that did the first round of work. That session had **no Android SDK**
(dl.google.com was blocked), so **nothing Kotlin/Android-side has been compiled or run yet**. Step 1 on a machine with the SDK is to build.

## Goal (from the owner)
1. Rebrand: new name (done: **Lafz**, لفظ), custom icon (done), no "HeliBoard" in user-visible text (done; upstream credits/URLs kept on purpose, they are license attribution).
2. Gboard look (first pass done, needs eyes-on tuning).
3. **Main feature: Gboard-style Roman → Urdu ("abc → اردو")**: type `mein kal aonga`, get Urdu script while typing, Urdu word suggestions in the strip.
   Explicitly **not** a fixed phonetic map (other keyboards do that, owner rejects it). English typing stays as is. 100% offline.
4. **Secondary, only after Urdu works: GIF search** from public GIF sites. The app gets INTERNET permission *only* for GIF download; everything else stays offline.

## How the Urdu feature works (the research conclusion)
Gboard's own paper (Hellsten et al. 2017, "Transliterated mobile keyboard input via weighted finite-state transducers") describes: lexicon + language
model decoded through a noisy-channel transliteration model, on-device. We do the same, with a small custom decoder instead of OpenFst:

    score(word) = ln P(word | previous word)  +  1.4 * ln P(roman | word)

* `P(roman | word)`: monotone character-alignment channel model. Each Urdu letter emits a short Roman chunk (ک → k / ka / ki …), summed over all
  alignments (forward algorithm). Learned by EM from human-written romanizations (Google **Dakshina** dataset, CC BY-SA 4.0). Words people were seen
  romanizing also get a bonus for those exact spellings.
* LM: unigram + pruned bigram over 8.8M tokens of Urdu Wikipedia (Dakshina native-script text), 60k-word vocabulary.
* **Dictionary-only output** (owner requirement, like Gboard): every candidate is a dictionary entry; weak candidates are cut (`minLogChannel`, `maxScoreGap`,
  no completions to rare words), gibberish returns nothing. The typed Roman text is never drawn in the strip (index 0 is omitted by `omitTypedWord` for typing).
* **Multi-word entries**: ~1,200 generated future-tense verb phrases (`aonga` → آؤں گا / آؤں گی, `karunga` → کروں گا …) from ~150 seed + attested infinitives. Entries may
  contain a space; the channel model runs over the letters without it. Prev-word context for the next word is the last token of a phrase.
* **English loanwords** (`computer` → کمپیوٹر, `message` → میسج, `laptop` → لیپ ٹاپ): English spellings are extra "observed variants" of Urdu dictionary entries, from
  (a) hand-curated `tools/urdu/loanwords_curated.tsv` (~230, **edit this to add words**), (b) `"اردو (english)"` pairs mined from Wikipedia and confirmed by the decoder,
  (c) decoder-voted CMUdict phonetic guesses for common English words (wordfreq). See `tools/urdu/README.md` step 2.
* Prefix completions while typing (`ka` → کا, کالج…), next-word prediction from bigrams, auto-commit of best Urdu word on space.
* Held-out accuracy (Dakshina romanized sentences, eval sentences removed from LM training, previous word = engine's own prediction):
  **top-1 89.6 %, top-3 93.4 %**, ~3 ms/query on a desktop JVM (mobile will be slower, still fine; unmeasured). Caveat: Wikipedia register.
  Casual chat is likely lower. Known example: `yaar kya haal hai` → `…حال ہی` because "حال ہی" ("recently") is a very common Wikipedia bigram.
* Other research notes: Roman-Urdu-Parl (6.37M sentence pairs) + m2m100 transformer reaches Char-BLEU ~97 but is far too big for a keyboard
  (arXiv 2503.21530); seq2seq paper arXiv 1712.02959. Could be used offline to *generate training data* for the lexicon/LM, not shipped.

## Code map
| What | Where |
|---|---|
| Engine (pure Java, no Android deps) | `app/src/main/java/helium314/keyboard/latin/urdu/RomanTransliterator.java` |
| Language registry + lazy model loader (`RomanScript.URDU` / `ARABIC`, punctuation, honorific script) | `.../latin/urdu/RomanScript.kt` |
| Hook into suggestions (branch at top of `getSuggestedWordsForNonBatchInput`) | `.../latin/Suggest.kt` → `getRomanScriptSuggestions` |
| Model asset (3.5 MB, binary "UTRL" v1) | `app/src/main/assets/urdu/urdu_translit.bin` |
| Data/model pipeline + eval harness | `tools/urdu/` (see its README) |
| Arabic (abc → عربي) model asset + pipeline | `app/src/main/assets/arabic/arabic_translit.bin`, `tools/arabic/` (README: data, build, held-out results). Subtype `ar-Latn` id 0x61726c74, Arabizi digits are word characters via `res/values-b+ar+Latn/` |
| Unit test (needs the asset) | `app/src/test/java/helium314/keyboard/latin/urdu/RomanTransliteratorTest.kt` |
| Subtype `ur-Latn` | `res/xml/method.xml` (id 0x75726c74), `strings.xml` `subtype_ur_Latn`, `LocaleUtils.kt`, `donottranslate.xml` |
| Icon generator | `tools/make_icon.py` (writes vectors + PNGs; preview in `art/lafz/`) |
| Gboard palette + defaults | `keyboard/KeyboardTheme.kt` (THEME_LIGHT/THEME_DARK), `latin/settings/Defaults.kt` (Rounded style, key borders on) |

Integration design: the locale `ur-Latn` (mirrors the existing `hi-Latn`/Hinglish pattern) selects the QWERTY layout and RTL-neutral Latin script.
When `DictionaryFacilitator.mainLocale == ur-Latn`, `Suggest` returns `[typed roman, urdu1, urdu2 …]` with `willAutoCorrect = true`, so the existing
`commitCurrentAutoCorrection` path commits `urdu1` on space/punctuation. Typed Roman stays in the composing region (like Gboard). If the model is not
loaded yet (first keystroke after start), it falls back to normal suggestions while loading on a background thread.

## Religious honorifics & terms (owner request)
English (any Latin typing), Roman→Urdu typing and Urdu-script typing all get honorific suggestions from `latin/urdu/ReligiousShortcuts.java`
(data: `app/src/main/assets/honorifics/honorifics.tsv`, loader `Honorifics.kt`, hook `Suggest.addHonorificSuggestions`):
* `Rasul` → `Rasulullah ﷺ` / `Rasulullah Sallallahu Alaihi Wasallam`; `Isa` → `Isa Alaihis Salaam`; `Umar` → `Umar Radiallahu Anhu`; Urdu output `عمر رضی اللہ عنہ`.
* After a committed name, next-word prediction offers the right suffix (`Umar ␣` → `Radiallahu Anhu`; `Aisha ␣` → `Radiallahu Anha`; Prophet → `Alaihis Salaam`).
* Abbreviations `ra/rz`, `as`, `saw`, `rh` expand according to the previous name's type (M/F/D/P/Q/H/W in the TSV). `as`/`saw` need a matching previous name so ordinary English is never touched.
* Phrases/terms: bismillah, assalamu alaikum, inshallah, mashallah, subhanallah, alhamdulillah, jazakallah, ameen, Quran, Hadith, namaz/prayer names, Eid/Ramadan greetings…
* Spelling-insensitive matching (`fold`: doubled letters collapsed, y=i, u=o): `salallahu`/`sallallahu`, `rasool`/`rasul`, `eesa`/`isa`. Latin prefix match needs ≥4 letters (short prefixes fired on ordinary words), Urdu ≥3.
* Placement: honorifics are inserted at strip slot 1 (centre) when there is no auto-correction, slot 3 when there is, so they never become the auto-correct target; in Urdu mode slot 2.
* **Please have a religious-knowledge owner review `honorifics.tsv`** (spellings, which honorific for whom, and the Urdu text). Add/modify by editing the TSV only. Long phrases may need strip text-scaling checks on device.
* TODO: a Settings toggle ("Religious honorific suggestions") — not added yet; learning: committed phrases enter user history like any word.

## Rebrand details
* applicationId `app.lafz.keyboard` (was `helium314.keyboard`), versionName `0.1.0`, APK name `Lafz_*.apk`. Kotlin/Java **package names are unchanged**
  (`helium314.keyboard.*`, namespace `helium314.keyboard.latin`) to keep merges from upstream possible. Provider authorities changed to `app.lafz.keyboard.*`.
* All `HeliBoard` strings in `res/values*/` replaced by `Lafz` except URLs and two upstream-project credits ("HeliBoard Gesture Data Project").
* README.md, fastlane/, CONTRIBUTING.md, About screen links still describe HeliBoard — rewrite README for Lafz; keep the license files and attribution (GPL-3.0).
* Licensing: app is GPL-3.0 (HeliBoard). Dakshina-derived `urdu_translit.bin` is CC BY-SA 4.0 data: keep attribution (add to About/credits screen). It is a separate data file.

## TODO, in priority order
1. **Build & fix compile errors.** `./gradlew assembleDebug` (needs Android SDK 37 + NDK for the JNI part, JDK 17+). Or push and let `.github/workflows/build-debug-apk.yml` build it.
   Then `./gradlew testDebugUnitTest --tests '*RomanTransliteratorTest*'`.
2. **Enable the layout on device**: Settings → Languages & layouts → add "Urdu (abc → اردو)" (it appears like Hinglish). Verify: typing shows Urdu candidates, space commits the best one,
   backspace after auto-commit reverts to Roman (standard HeliBoard behaviour), tap on a suggestion commits it.
   Possible issues to check: `ur-Latn` handling in places that special-case `hi-Latn` (grep it), spell-checker for the locale, dictionary-facilitator without a main dict,
   user-history dictionary learning Urdu words under `ur-Latn` (may be undesirable), strip layout with `[typed | urdu1 | urdu2]` ordering (Gboard shows Urdu in all slots,
   consider hiding the Roman literal or putting it first).
3. **Punctuation**: map `.`→`۔`, `?`→`؟`, `,`→`،` when composing/committing in `ur-Latn` (Gboard does this). Hook near `commitCurrentAutoCorrection` / separator handling in `InputLogic`.
4. **Quality**
   * Domain gap: LM is Wikipedia-only. Add conversational data (Dakshina romanized sentences are only 9.7k; consider licensed chat/subtitle corpora, or a user-opt-in *on-device* learning of committed words/bigrams).
   * OOV: only 60k words (98 % token coverage on Dakshina eval) plus extras. Names / unseen words currently show nothing (by design: dictionary-only). More verb forms are worth adding (past `kardiya`, `hogaya`, continuous `kar raha`, imperatives `karo`, `kardo`), plus a bigger curated loanword list and common chat words/slang (`yaar`, `bhai`, `acha`…): all go through `tools/urdu/build_extra.py`.
   * Roman `h`-aspirates, `ain`/`hamza` handled implicitly by the channel model; check hard cases (`kh`, `gh`, `ch`, nasal ں).
   * Re-tune weights in `RomanTransliterator` (channelWeight 1.4, observedBonus 1.0, bigramWeight 0.5, completionPenalty 1.2) with `tools/urdu/eval/Eval.java`.
   * Mobile perf: measure `suggest()` latency on a mid-range phone; first-letter buckets already prune ~90 %. Move heavy work off the UI thread if needed (HeliBoard already calls Suggest from a background handler).
5. **Gboard look polish**: compare side by side with Gboard on a phone. Key radius/gaps/heights, enter key accent color (blue pill), spacebar label "اردو" / language chip, suggestion strip font (Urdu needs Noto Nastaliq/Naskh; check bundled fonts), popup style, emoji/toolbar icon style. Colors are in `KeyboardTheme.kt` and style XMLs `res/values/themes-rounded-*.xml`.
6. **GIF feature (phase 2)** — do not start before 1–3 work. Constraints from owner: internet only for GIF fetching.
   * Keep the keyboard process offline: add the `INTERNET` permission but gate all network use behind one class, only triggered by the GIF panel; consider a separate `<service>`/activity or product flavor so a no-network build can still exist. No analytics, no other network calls.
   * Sources: GIPHY API (needs key), Klipy/Tenor-style APIs (check current availability; Tenor API has been wound down for new signups — verify), or keyless public sources. Ask the owner which.
   * Insert via `InputConnection.commitContent(InputContentInfo)` (needs `EditorInfo.contentMimeTypes` check; fall back to sharing a link). Cache downloaded GIFs in app cache. Search box inside the GIF panel (HeliBoard already has an emoji-search precedent).
7. Replace `README.md`, fastlane metadata, and upstream links in About screen; add Dakshina/HeliBoard attribution.

## Rebuilding the model
See `tools/urdu/README.md`. Needs `dakshina_dataset_v1.0.tar` (https://storage.googleapis.com/gresearch/dakshina/dakshina_dataset_v1.0.tar, 2 GB, extract only `ur/`), Python 3 (stdlib only),
JDK for the eval harness. Training takes ~2 minutes. Shipping build: `python3 build_lm.py 60000` (no `--holdout`), `python3 export_model.py ../../app/src/main/assets/urdu/urdu_translit.bin --ship`.
Binary format is defined by `export_model.py` and read by `RomanTransliterator.load` — change both together.

## Working notes
* **Git workflow (owner preference): commit directly to `main`; do not create new branches** unless the owner asks for one for a risky change. Repo is `magnumquest/Lafz`; `upstream` remote = HeliBoard.
* Never commit the signing key (`~/.android/lafz-release.jks`) or personal screenshots (they contain private chats).
* `tools/urdu/eval/Try.java` prints candidates for phrases: `java -Dstdout.encoding=UTF-8 -cp eval/out Try model.bin "mein kal aonga"`.

## Arabic (abc → عربي) — added after the Urdu work
* Same engine/file format as Urdu, scripture-focused: Qur'an + Hadith text (unigram 65 %, bigrams) + real Arabizi chat pairs (digits 3 7 5 6 8 9 2). Details, build steps and held-out numbers: `tools/arabic/README.md`.
* Honorifics have an Arabic column (6th) in `honorifics.tsv` (`ReligiousShortcuts.ARABIC`); it was written by the assistant — **needs review by a religious-knowledge person**.
* The data bundle (`lafz_arabic_data.zip`) has unclear/restrictive licences (Tanzil verbatim-only, scraped hadith): **the repo must stay private**; never commit the raw bundle (only derived `channel_trained.json` and the model).
* Keyboard lag finding: suggestion updates run `performUpdateSuggestionStripSync` on the UI thread and *wait* for the background decoder (also on space via `commitCurrentAutoCorrection`), so decoder time (Arabic ~8 ms desktop, several times that on a phone) is felt as typing lag. Done: chunk indexing in the decoder (−25 %, bit-identical output), no IPC for `.`/`,` mapping, strip width measured once, model preloaded at subtype start. A DP cutoff was tried and reverted (no speedup). Next step if lag remains: make the strip update non-blocking for roman-script locales (needs on-device testing: stale results must be dropped and space must still use a fresh result).

## Typing speed, native numerals, live transliteration (branch `lafz-live-typing`)
Design/plan/results: `docs/superpowers/{specs/2026-10-14-live-typing-design.md,plans/2026-10-14-live-typing.md,results-typing.md}`. Emulator helper: `tools/emu/emu.sh` (private AVD on port 5580; `boot install subtype <ur-Latn|ar-Latn|en-US> field2 clear type "<text>" <delay> shot ui`; `field2` = Google Messages compose box, the settings search field disables suggestions).
* **Async strip update**: `InputLogic.performUpdateSuggestionStripAsync` (generation counter, result applied on the UI thread only if the typed word is unchanged); the UI thread no longer waits for the decoder/dictionary (was 6-13 ms per key on the emulator, now ~0.1 ms). The sync variant is kept for commit paths. `config_delay_in_milliseconds_to_update_suggestions` 100 -> 20.
* **Live transliteration** (setting "Live transliteration while typing", default on, Urdu/Arabic only): the composing region shows `PhoneticFallback` text instantly, then the decoder's first candidate (`mLiveCandidate`); `mWordComposer` keeps the Roman word. What is displayed is what space commits. Unknown words (decoder had no candidate) stay Roman and the Roman spelling is always offered in the strip. Words resumed from existing text (revert after auto-commit, cursor tap) and gesture words are not live-rendered. Cursor bookkeeping: `mLiveComposingStart/mLiveMaxLen` so our own two-step updates are not mistaken for cursor moves.
* **Arabic clitics**: a committed proclitic (و ف ب ل ك ال ...) keeps its space; when the next word is committed the space is removed (`joinPrecedingArabicProclitic`).
* **Bidi marks**: the keyboard inserts an RLM at paragraph start; it sticks to the first word and must be stripped from the decoder's context words (done in `Suggest.kt` and `RomanTransliterator.context`).
* **Native numerals**: symbol pages for ur-Latn / ar-Latn use `symbols_ur_latn.txt` / `symbols_ar_latn.txt` (chosen in `LayoutParser.nativeSymbolsLayoutName`, NOT via method.xml extra values: those change subtype identity and break stored subtypes) and native digits (Arabic-Indic / Urdu forms). Known gap: number, phone and numpad layouts (numeric fields) still Western.
* Decoder speed: ~1-1.6 ms/query on a desktop JVM (Urdu ~1 ms, Arabic ~1.6 ms); the glued-word lattice is ~50-65 % of it; no cheap speed-up found (see results-typing.md Task 3).
* Not verified on a device: toggle OFF behaviour, tap-on-candidate while composing in Arabic, cursor-tap inside a committed word, gesture typing in ur/ar, a real phone. The async path has no unit test.
