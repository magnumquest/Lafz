# Typing latency: baseline (Task 1)

Measured on the private emulator (Pixel_API36 AVD, port 5580, x86 with swiftshader), debug build of the
`lafz-live-typing` branch before any async or live-typing changes. **The emulator is not a phone.** Treat these as
relative numbers for comparing before/after, not as device latency.

Logs use the tag `LafzPerf`, emitted only when `BuildConfig.DEBUG` is true:
* `strip-sync UI-thread ms`: time spent in `InputLogic.performUpdateSuggestionStripSync` on the UI thread (includes the
  wait for the background suggestion thread).
* `decode ms`: time of `RomanTransliterator.suggest()` inside `Suggest.getRomanScriptSuggestions` (background thread).

Input: `tools/emu/emu.sh type "<text>" 0.15` (150 ms between key taps), into the Android settings search field.

## Baseline table

"Strip, all" includes samples taken at word boundaries (empty composing word, typed=0). "Strip, typed>0" only counts
samples where a word is being composed, which is what the typing feels like.

| Language | Text typed | Strip, all: n / mean / max ms | Strip, typed>0: n / mean / max ms | Decode: n / mean / max ms |
|---|---|---|---|---|
| en-US (no Roman decoder) | `the quick brown fox ` | 20 / 12.38 / 29.63 | 20 / 12.38 / 29.63 (no empty-word samples logged for English) | n/a (not a Roman script) |
| ur-Latn | `kiya haal hai ` | 14 / 5.77 / 16.11 | 7 / 7.79 / 16.11 | 7 / 5.42 / 13.84 |
| ar-Latn | `innama alamalu binniyyat ` | 25 / 9.00 / 27.67 | 16 / 13.03 / 27.67 | 16 / 10.29 / 24.69 |

Notes on the English row: every English sample had a non-empty composing word, so "all" and "typed>0" are the same set.

## Observations

* Strip-sync time is larger than decode time for both Roman scripts. The UI thread also does the strip layout and the
  non-decoder work, so decode is not the whole cost. The difference is the baseline for Task 2 (non-blocking strip update).
* English, which does no Roman decoding, still spends about 12 ms per keystroke on the UI thread in this emulator. That is
  the ordinary HeliBoard dictionary path, so it is a floor for the current architecture on this machine.
* The first Urdu samples are `typed=0` with about 1.5 to 12 ms each. These are the empty-word syncs at the start of the
  run, before the model finishes loading. Decode samples begin only after the first keystroke.
* Arabic is the slowest of the three (decode mean about 10 ms, max about 25 ms here). This matches the earlier
  CLAUDE.md note that Arabic decode is several times the Urdu cost.
* The field, not the keyboard, was the problem in one place: the Android settings search field kept text from earlier
  runs (screenshots show leftover Arabic and English text), so the typed strings are mixed with old text. The keyboard
  did type correctly in all three runs (screenshots `/tmp/t1_en.png`, `/tmp/t1_ur.png`, `/tmp/t1_ar.png`). The Urdu
  strip shows Urdu candidates for the typed text and the Arabic strip shows Arabic candidates.

## Raw logs

* `/tmp/t1_ur_log.txt` (21 LafzPerf lines)
* `/tmp/t1_ar_log.txt` (41 LafzPerf lines)
* English lines were read from `emu.sh logcat` immediately after the run (20 strip lines), not saved to a file.

## After Task 2 (async strip update)

Same method and emulator as the baseline. The UI thread now only snapshots state and posts the work
(`async-post`). The result is applied later on the UI thread (`async-apply`, latency measured from post to apply).
The baseline `strip-sync` lines are gone from this path; `strip-sync` now appears only for sync calls (commit
path with a pending or in-flight request).

| Language | Text typed | UI-thread ms per key, before (strip-sync, all / typed>0 mean) | UI-thread ms per key, after (async-post mean / max) | Apply latency after, ms (post to apply) |
|---|---|---|---|---|
| en-US | `teh quick brwn fox ` | 12.38 / 12.38 | 0.05 to 0.13 (n=20, first run) | 12.9 mean over 19 shown, max 31.5 |
| ur-Latn | `kiya haal hai ` | 5.77 / 7.79 | 0.05 to 0.18 (short run, mean not computed) | 1.1 to 3.7 on the lines shown (partial) |
| ar-Latn | `innama alamalu binniyyat ` | 9.00 / 13.03 | 0.065 mean, 0.140 max (n=25) | 9.70 mean, 32.5 max (n=25) |

Notes:
* The UI-thread cost per key dropped from roughly 6 to 13 ms to about 0.1 ms. The remaining decode runs on the
  suggestion thread and reaches the strip 1 to 30 ms later.
* Arabic output on device: `innama alamalu binniyyat ` commits إنما الأعمال بالنيات.
* Urdu output on device: `kiya haal hai ` commits کیا حال ہی. The last word is the known Wikipedia-register case from
  CLAUDE.md (حال ہی, not ہے); it is not a regression.
* English autocorrect was NOT verified. The Android settings search field has `inputType=0x100001` (TYPE_TEXT_FLAG_NO_SUGGESTIONS),
  so the IME suppresses autocorrect there. The typed text came out uncorrected, so this test does not tell whether autocorrect
  still works. Needs a field without that flag.
* The stale-drop path (`async-apply dropped=...`) produced no lines in these runs, so it is not exercised on device yet.

## Tasks 6-7 live transliteration

Build: `assembleDebug` OK. Unit tests `*Urdu* *Arabic* *Phonetic*` pass (RomanTransliteratorTest 1, ArabicTransliteratorTest 3, PhoneticFallbackTest 3).
Device: private emulator (port 5580), debug build, Urdu (ur-Latn) subtype selected through the long-press space chooser
(the `emu.sh subtype` prefs edit did not change the active subtype). Field: Android settings search.

What works (verified on device, screenshots `/tmp/t7_a.png`, `/tmp/t7_b.png`, `/tmp/t7_c.png`, `/tmp/t7_d.png`):
* `kiya` with no space: the field shows کیا; strip `kiya | کیا`.
* `kiya haal hai ` commits `کیا حال ہی ` (the known Wikipedia-register case, not a regression).
* `kaha` then one backspace: the field re-renders from `kah` to کہ; the strip highlights the matching candidate.
* `kah` then space: the committed text is `کہ ` (the displayed text), so display and commit agree.
* Cursor is not reset after each letter (composition survives many keystrokes in a row).

Not verified (time): Arabic `innama alamalu binniyyat ` on the live path; en-US unaffected check; tap on a strip
candidate; backspace to an empty word; revert after auto-commit; the Settings toggle off (Text correction screen).

Known remaining issues (code reasoning, not reproduced):
* Moving the cursor inside a live word ends the composition (text stays as shown). Repro: type `kiya`, tap between
  letters. The Roman composer positions do not map onto the display, so the composition is ended instead of mapped.
* After such a cursor move, `restartSuggestionsOnWordTouchedByCursor` can resume composing on the Urdu text as the
  composer's typed word. Typing more letters then appends Roman letters to Urdu. Repro: tap into a committed Urdu word
  and type a letter.
* `revertCommit` (non-space-language branch) still sets the composing text raw; unreachable for ur-Latn/ar-Latn.
* Gesture (batch) input on ur-Latn/ar-Latn now displays through the live helper; not tested.
* Learning: committed live text enters user history as Urdu/Arabic words (commitChosenWord learns the committed text).

## Task 3 decoder speed

Desktop JVM, `RomanTransliterator.suggest()` only (no Android, no emulator). Harness in `/tmp/t3_src` (not in the repo):
`Bench` prints every candidate list with its score for a fixed query set and times the best of several warm passes.
Query sets: Arabic 1157 queries (owner phrase tokens, every prefix, with and without context); Urdu 3360 queries
(246 words and 20 phrases, every prefix, with context words). The machine was shared with the emulator and gradle,
so single runs vary by about 10 percent; compare numbers within one block only.

| Build | Arabic ms/query (1157 queries) | Urdu ms/query (3360 queries) |
|---|---|---|
| HEAD (kept) | 1.51 to 1.61 | 0.93 to 1.01 |
| Row-range scan (scratch buffer, live-range tracking) | 1.64 to 1.67 | 1.00 to 1.12 |
| Exp tables and single-entry query cache, original scan | not measured | 0.95 to 1.04 |

Where the time goes (Arabic and Urdu, same query sets, glued-word lattice disabled with `maxSegments = 1`):

| | with lattice | without lattice | lattice share |
|---|---|---|---|
| Arabic | 1.64 ms | 0.54 ms | about 67 % |
| Urdu | 0.94 ms | 0.45 ms | about 52 % |

Findings:
* The matrix allocation per `scan()` call is not the bottleneck. Replacing it with a reused buffer and skipping zero
  regions did not make suggest() faster; it was slightly slower in every alternating run, so it was reverted.
* `Math.exp` in `lmLn` is not significant either. Precomputed tables were bit-identical but within noise.
* The single-entry query cache only helps when the same query is repeated back to back. It needs a tunables snapshot
  in the key, since the tunables are public mutable fields. The benefit is unmeasured and the code is not kept.
* The main cost is the channel DP in `scan()`, run once per typed position by the glued-word lattice (m scans over the
  first-letter buckets). That is inherent to the algorithm, so a real speedup needs a structural change: for example,
  reusing DP columns across keystrokes (the column for `r[start+j]` depends only on the typed prefix, so appending a
  letter adds one column), or a trie over channel keys for the buckets. Both are larger changes and must be checked for
  identical output.
* Output check: the optimized builds gave byte-identical candidate lists on both query sets (`diff` of the full outputs).
  The final source is identical to HEAD, and its outputs match the saved baseline (`/tmp/t3_before_ar.txt`,
  `/tmp/t3_before_ur.txt`).
* Phrase harness (Arabic, 63 phrases) unchanged: strict 54.0 %, loose 54.0 %, words found 76.2 %.

## Task 8 verification

Device: private emulator emulator-5580 (Pixel_API36, 1080x2400), debug build installed from `app/build/outputs/apk/debug/Lafz_0.1.0-debug.apk`
(`assembleDebug --offline` exit 0). Field: Google Messages compose box (`am start -a android.intent.action.SENDTO -d sms:5551234`), which has
normal suggestions, unlike the settings search field. Screenshots in `/tmp/claude-shots/` (`t1_kiya.png`, `t1_commit.png`, `t1_bs1.png`,
`t1_bs7.png`, `t1_bs10.png`, `t1_bs16.png`, `t1_tap_after.png`, `t7_fast.png`, `clear_check.png`). The strip on this AVD is at y≈1718, not y≈1432.
Starting subtype was already Urdu (space bar reads "Urdu (abc → اردو)"); the Urdu switch was not re-done.

| # | Check | Result | Observed |
|---|---|---|---|
| 1a | `kiya` before space shows Urdu in the field | PASS | Field shows کیا; strip `kiya | کیا` |
| 1b | `kiya haal hai ` commits | PASS (with note) | Field `کیا حال ہی ` (the known Wikipedia-register case: حال ہی, not ہے); next-word strip کی، اس، سے، میں، نہیں، کے |
| 1c | Backspace once after auto-commit space | FAIL vs expectation | Field `کیا حال ہی` (space removed). Last word is recomposed as Urdu, not reverted to Roman `hai`. Strip shows ہائی، ہیں، ہے، ہی, and `hai` as a Roman candidate. Repro: 1b then `emu.sh key backspace` |
| 1d | Backspace until word is empty | PARTIAL | Deletes Urdu letters one at a time, not word-wise: `کیا ح` (after 7 backspaces, strip empty), `کی` (after 10), field empty with placeholder after 16. Gboard-style word deletion is not reproduced |
| 1e | Type again after emptying | not run separately | Covered by the fast-typing run below |
| 1f | Tap a strip candidate while composing | PASS | `kiya`, tap کیا at (572,1718): field `کیا`, strip cleared |
| 7 | Fast typing `kiya haal hai kal aonga mein` at 0.05 s | FAIL | Final text is not in typed order. Field (after a previous `کیا` from 1f stayed in the field): displayed `kiya حال ہی کل آؤں گا میں|کیا` with the cursor between `میں` and `کیا`. Roman `kiya` is at the end. Repro: after 1f, `emu.sh type "kiya haal hai kal aonga mein" 0.05`. Note: the pre-existing text was not cleared (see clear below), so the cause is partly the test setup |
| - | Cursor-move / composition restart (item 5) | not run | Only code analysis in results above |

Not run (time): Arabic checks (`innama alamalu binniyyat `, `wa kitab `, backspace), English en-US autocorrect check (item 3), Settings
toggle OFF (item 4), cursor-move on device (item 5), native digit symbol page and numeric field (item 6).

### emu.sh status (this task)
* `field2`: works (opens Google Messages compose, taps the EditText by uiautomator bounds).
* `ui [regex]` and `uitap <regex>`: work (dump via uiautomator; helper `tools/emu/uinode.py`).
* `switch <ur|ar|en>`: written (long-press space, tap entry by text) but NOT verified on device. The language chooser was not opened in this run.
* `clear`: FAILS. Sending 10 batches of 30 `KEYCODE_DEL` after `KEYCODE_MOVE_END` did not empty the field. The field lost only part
  of its text (`clear_check.png`). Needs a different approach (e.g. `input keycombination` select-all, or per-key sleeps).
* `type` and `key backspace` as before.

### Repro for the FAILs
* 1c: `tools/emu/emu.sh field2`, `tools/emu/emu.sh type "kiya haal hai " 0.3`, `tools/emu/emu.sh key backspace`. Expected: `کیا حال ہی` with Roman `hai` back in the composing region (or at least the Roman `hai` as the candidate to commit). Observed: last word recomposed as Urdu `ہی`.
* 7: start from an empty field (clear does not work, so restart the Messages field), then `tools/emu/emu.sh type "kiya haal hai kal aonga mein" 0.05`. Observed text is out of order and contains the stale `کیا`.
