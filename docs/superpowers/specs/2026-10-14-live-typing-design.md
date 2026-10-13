# Lafz: typing speed, Arabic/Urdu numerals, Gboard-style live transliteration (design)

Branch `lafz-live-typing` (from `lafz`). Owner decisions: Urdu AND Arabic use native digits + punctuation on the symbol page; code is written by Haiku 5.5 subagents; reviews happen after batches of tasks; review findings are fixed in one pass.

## Goals
1. **Smooth typing**: English (US) and Urdu/Arabic transliteration must not stall the UI thread per keystroke.
2. **Native numerals/punctuation**: ar-Latn symbol/number page shows Arabic-Indic digits (٠١٢٣٤٥٦٧٨٩) and Arabic punctuation (؟ ، ؛ « » ٪ ٫ ٬); ur-Latn shows Urdu digits (۰۱۲۳۴۵۶۷۸۹) and Urdu punctuation (؟ ، ؛ ۔ « » ٪ ٫ ٬). Typing a digit on that page inserts the native digit.
3. **Live transliteration (Gboard style)**: while typing `kiya`, the text field already shows Urdu (کیا) and re-renders after every keystroke; the strip offers alternatives; space commits what is displayed. Same for Arabic. No second engine: a tiny phonetic map renders instantly, the existing decoder's best candidate replaces it as soon as it is ready (a few ms).

## Findings that drive the design
* `LatinIME.UIHandler` handles `MSG_UPDATE_SUGGESTION_STRIP` 100 ms after a keystroke (`config_delay_in_milliseconds_to_update_suggestions`) and calls `InputLogic.performUpdateSuggestionStripSync`, which posts the work to `InputLogicHandler`'s non-UI thread and then **blocks the UI thread** on an `AsyncResultHolder` (`holder.get(null, GET_SUGGESTED_WORDS_TIMEOUT)`). Decoder/dictionary time = typing lag. `commitCurrentAutoCorrection` also calls the sync variant if an update is pending.
* The composing text in the field is set from `mWordComposer.getTypedWord()` in `InputLogic` (`setComposingTextInternal` at ~6 sites).
* `Suggest.getRomanScriptSuggestions` returns `[typed roman, best, ...]`, `willAutoCorrect=true`; `commitCurrentAutoCorrection` commits the best on space.
* Symbols layouts: `assets/layouts/symbols/symbols.txt`, `symbols_arabic.txt`; localized digits exist via `LocaleKeyboardInfos.localizedNumberKeys` (used in `KeyboardParser`). ur-Latn/ar-Latn have Latin script so they get the Western files.

## Design
### A. Non-blocking strip update
* `performUpdateSuggestionStripAsync(settings, inputStyle)`: increments `mStripGeneration`, posts the suggestion computation to the non-UI thread (as today) and, in the callback, posts to the UI handler: if generation is unchanged and the composing word is unchanged, apply (`setSuggestions`, retrieve-older-suggestions rule unchanged); else drop. The UI thread never waits.
* `MSG_UPDATE_SUGGESTION_STRIP` uses the async variant. Delay config 100 → 20 ms.
* Anything that needs a fresh result right now (`commitCurrentAutoCorrection`, tap-commit, batch input) keeps the sync variant. If an async request is in flight when space arrives, `commitCurrentAutoCorrection` runs the sync variant (one wait per word, not per key).
* Measurement first (Task 1) so the gain is shown on the emulator, and on Urdu/Arabic decode time (`tools/arabic/eval`, `tools/urdu/eval`).

### B. Native numerals/punctuation
* New layout files `symbols/symbols_ar_latn.txt` and `symbols_ur_latn.txt` (copy of `symbols_arabic.txt`, Urdu one adds `۔`), selected for subtype locales ar-Latn / ur-Latn; number row / numpad digit keys use `localizedNumberKeys` for these locales (Arabic-Indic for ar, Persian/Urdu forms ۴۵۶۷ for ur). Alphabet-page number-row hint labels already show native digits and stay.
* Roman-mode typing is unaffected: digits typed on the alphabet page in ar-Latn remain Arabizi letters; only the symbol/number page emits native digits.

### C. Live transliteration
* `PhoneticFallback` (pure Java, no Android deps, unit-tested): greedy longest-match Roman→Urdu / Roman→Arabic map (sh ش, ch چ, kh خ, gh غ, th/ṭ…, vowels a→ا/zabar-less, i→ی, u→و, e→ے/ی, aa→ا, …). Only used for the instant frame and when the decoder has no candidate; never the main path.
* `InputLogic` keeps `mWordComposer.typedWord` as the **Roman** text (needed by decoder, backspace, learning). New field `mLiveDisplay` (String): what the composing region shows. Rule: after each keystroke show `PhoneticFallback(typed)` immediately (UI thread, microseconds); when the async result arrives (generation matches) show the best **non-completion** candidate if its score is within the usual gap, else the best candidate, else keep the phonetic text. A single helper `renderComposing()` is used by every call site that currently does `setComposingTextInternal(typedWord…)` when `RomanScript.isActive(locale)`.
* Consistency: the text shown is the text committed. `commitCurrentAutoCorrection` uses the same candidate (store `mLiveCandidate` with the generation); on space with a stale display it runs the sync variant first (existing behaviour) and re-renders.
* Cursor bookkeeping: `RichInputConnection` tracks composing length from what is set; code that compares `mWordComposer.size()` with the composing-region length must use the displayed length for these locales (find with grep for `getTypedWord().length()` / `size()` near selection handling).
* Backspace removes one Roman char from the composer and re-renders. Tapping a strip candidate commits it (unchanged).
* Settings toggle "Live transliteration while typing" (default on). Off = today's behaviour (Roman composing text until space).
* Auto-correct underline (`getTextWithUnderline`) is not applied to the live text.

## Testing
* Unit: `PhoneticFallbackTest` (Urdu/Arabic examples, empty, digits, apostrophes), existing `*Urdu*` `*Arabic*` tests.
* Emulator (private AVD on port 5580, debug build, subtype via prefs; see `tools/emu/`): typed phrases and screenshots after each sub-project: English typing latency (logcat timings before/after), Urdu `kiya haal hai`, Arabic `innama alamalu binniyyat`, symbol pages, backspace, tap-commit.

## Out of scope
GIF search, new models, README rebrand.
