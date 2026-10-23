# On-device learning for Roman Urdu / Roman Arabic (design)

Owner request (2026-10-23): "on-device learning, without slowing down the typing experience". Builds on the parked proposal in resume.md.

## Goal
Lafz learns from what the owner commits in `ur-Latn` / `ar-Latn`: favourite words, word pairs (next-word habits) and the exact Roman spellings they use for a word.
Everything stays on the device. Typing must not get slower.

## Non-goals
No cloud, no sync, no learning in password fields or incognito mode, no change to English (HeliBoard's user history already learns it), no new candidates that are not
either in the bundled dictionary or something the owner kept in Roman letters on purpose.

## Design
* `PersonalModel` (pure Java, `latin/urdu`): three count tables, `word`, `(prev, word)` and `(roman spelling, word)`. Thread safe, tiny critical sections.
  * `bonus(prev, word, roman)`: saturating ln-unit bonus added to the decoder score: spelling <= 3.0, pair <= 1.5, word <= 1.2 (`maxScoreGap` is 8, so a habit
    reorders close candidates but cannot drag in far-off ones).
  * `injected(roman)`: words the owner used for exactly this spelling >= 2 times (covers names kept in Roman, e.g. `Ahmed`), offered even when the decoder has nothing.
  * `nextWords(prev, n)`: learned successors, shown before the model's predictions.
  * Binary save/load, capped sizes, counts halve every 4000 learning events so old habits fade.
* Where it costs time: only in the final merge of `RomanTransliterator.suggest` (about 10-20 hash lookups on candidates already computed; the DP is untouched) and
  one map update per committed word. Disk writes happen on a single background thread, debounced to 10 s after the last change plus on keyboard hide.
  The model loads in the same background thread as the engine; until it is loaded learning is skipped, never waited for.
* Hooks in `InputLogic`: `performAdditionToUserHistoryDictionary` (same gating as HeliBoard's own learning: not incognito, not password/no-suggestion fields, not a
  slow connection) learns; `revertCommit` unlearns (a backspace after auto-commit means the guess was wrong). Weights: manual candidate pick 2, space auto-commit 1.
* Settings (Text correction): "Learn from my typing" (Roman Urdu/Arabic, default on) and "Clear learned words" with confirmation.
* Storage: `files/learned_<lang>.bin` (app-private, `allowBackup=false`).

## Anti-goals
Rich-get-richer drift (the model's own auto-commits reinforcing themselves): handled by smaller weight for auto-commits, capped bonuses, unlearning on revert.

## Left to builder
Exact constants (tuned against `RomanTransliteratorTest`), file format details.
