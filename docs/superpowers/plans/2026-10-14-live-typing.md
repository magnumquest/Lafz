# Live typing, speed, native numerals — implementation plan

Spec: `docs/superpowers/specs/2026-10-14-live-typing-design.md`. Branch `lafz-live-typing`. Coding by Haiku 5.5 subagents; review after Tasks 1-4 and again after 5-9; fixes from a review are applied in one pass.
Build/test: `. ~/.android-env.sh; JAVA_HOME=~/Android/jdk ./gradlew assembleDebug --offline -q` ; unit tests `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest --tests '*Urdu*' --tests '*Arabic*' --offline -q`. Emulator: `tools/emu/emu.sh` (boot, install, subtype, field, type, shot, logcat). Commit locally only: `git -c user.name="Umar Qureshi" -c user.email="moizaq@gmail.com" commit` with trailer `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`; never push; do not commit resume.md or data/.

### Task 1: Measure the current typing latency
- [ ] Add temporary-but-kept debug timing (`Log.d("LafzPerf", ...)`, only when `DebugFlags.DEBUG_ENABLED`) around `performUpdateSuggestionStripSync` (UI-thread wait ms) and around `Suggest.getRomanScriptSuggestions` (decode ms).
- [ ] Debug build, emulator: type "the quick brown fox" in en-US and "kiya haal hai" in ur-Latn with `tools/emu/emu.sh type`; collect `LafzPerf` lines; write the numbers to `docs/superpowers/results-typing.md` (baseline table).

### Task 2: Non-blocking suggestion strip update
- [ ] In `InputLogic`: add `performUpdateSuggestionStripAsync` with a generation counter; callback applies on the UI thread only if generation and composing word are unchanged. Reuse the existing "retrieve older suggestions" rule.
- [ ] `LatinIME.UIHandler.MSG_UPDATE_SUGGESTION_STRIP` calls the async variant; `config_delay_in_milliseconds_to_update_suggestions` 100 → 20.
- [ ] `commitCurrentAutoCorrection`: if an async request is pending or in flight, run the sync variant first (unchanged semantics otherwise).
- [ ] Verify on the emulator: repeat Task 1 measurements (UI-thread wait ≈ 0), Urdu `kiya haal hai` still commits correctly on space, English autocorrect still works ("teh " → "the ").

### Task 3: Urdu / decoder speed
- [ ] Measure Urdu `suggest()` ms (`tools/urdu/eval` Try/Eval or a JUnit micro-benchmark) with the shared prefix-DP engine; if > 5 ms/query on desktop, profile `scan()` and fix (e.g. cache by (typed,prev) for the last query, skip the glued-word lattice when `m < segMinTyped`, avoid per-call allocation of `double[MAXN+1][mm+1]`).
- [ ] Update `docs/superpowers/results-typing.md` with before/after.

### Task 4: Native numerals and punctuation (Arabic + Urdu)
- [ ] Create `assets/layouts/symbols/symbols_ar_latn.txt` and `symbols_ur_latn.txt` from `symbols_arabic.txt` (Arabic: ؟ ، ؛ « » ٪ ٫ ٬; Urdu adds ۔). Select them for locales ar-Latn / ur-Latn (find where `symbols`/`symbols_arabic` is chosen; layout name by locale/subtype).
- [ ] Symbol/number pages: digit keys use native digits (Arabic-Indic for ar-Latn, ۰۱۲۳۴۵۶۷۸۹ for ur-Latn) — via `LocaleKeyboardInfos.localizedNumberKeys` or the layout files; typing the key inserts the native digit.
- [ ] Emulator: switch to the symbols page in ar-Latn and ur-Latn (`emu.sh key symbols`), screenshot, type digits and punctuation, verify inserted characters.

### Task 5: PhoneticFallback (pure Java) + tests
- [ ] `app/src/main/java/helium314/keyboard/latin/urdu/PhoneticFallback.java`: `static String toUrdu(String roman)` and `toArabic(String roman)`: greedy longest-match tables (digraphs sh ch kh gh th ph; vowels; apostrophe = ع/ء for Arabic; word-initial vowel handling آ/ا; ending ay/ey → ے for Urdu); non-letters pass through. Must be instant and allocation-light.
- [ ] `PhoneticFallbackTest` (JUnit): `ki`→`کی`, `kiya`→`کیا`, `shab`→`شب`, `sh` etc., Arabic `ma'a`→`مع`, empty string, digits.

### Task 6: Live composing display plumbing
- [ ] Setting `pref_live_transliteration` (default true) in `Settings`/`Defaults`/settings screen ("Live transliteration while typing").
- [ ] `InputLogic.renderComposing()`: when `RomanScript.isActive(locale)` and the setting is on, the composing region shows `PhoneticFallback` text (instant) instead of the typed Roman text, at every `setComposingTextInternal(typedWord…)` site; keep `mWordComposer` Roman. Fix cursor/selection bookkeeping so the displayed length is used (grep `getTypedWord().length()` / composing-region length uses in `onUpdateSelection`).
- [ ] Backspace re-renders. Typing separators commits as before.

### Task 7: Decoder-driven live text
- [ ] When the async strip result arrives (generation matches), `renderComposing()` shows the best non-completion candidate (else best candidate) and stores it as `mLiveCandidate`; `commitCurrentAutoCorrection` commits that same candidate; tap on a strip candidate commits it; if display is stale on space, run sync variant then render.
- [ ] No underline on live text; honorific/phrase candidates (strip) unaffected.

### Task 8: Emulator verification of live typing
- [ ] Urdu: type `kiya` → field shows کیا while typing; `kiya haal hai ` → کیا حال ہے; backspace mid-word; tap a strip candidate; English subtype unaffected.
- [ ] Arabic: `innama alamalu binniyyat ` → إنما الأعمال بالنيات; clitic attach `wa `.
- [ ] Screenshots to `/tmp`, summarize in `docs/superpowers/results-typing.md`.

### Task 9: Docs and commit
- [ ] Update `CLAUDE.md` (new sections: async strip update, live transliteration, native numerals, emu.sh), `tools/arabic/README.md` pointer, `docs/superpowers/results-typing.md`.
- [ ] Unit tests `*Urdu* *Arabic* *Phonetic*` pass; release APK built with `tools/build-release.sh`; commit on the branch.
