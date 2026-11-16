# Suggestions, Roman-Urdu typing and real-time voice (2026-11-17)

Owner request (2026-11-17): Gboard-quality suggestions and learning, honorifics only when the honorific itself is typed,
Roman-Urdu phonetic output for unknown words with ٹ/ڈ options and teaching, and word-by-word (Gboard-like, words may change) voice.

Owner decisions: names stay names (`Umar` -> Umar). Honorifics are offered when the honorific is typed (`rad`, `sal`, `alai`, `rah`),
ordered by the previous name's type. Exceptions that still expand: Nabi, Rasul(ullah), Huzoor (assistant's call: Muhammad/Mustafa/Ali/
Hasnain are common personal names, so they are name-only). Voice: tentative words may change as speech continues (like Gboard).

Emulator findings (fresh install + 3 uses of "shoaib"): `sho` autocorrects to learned `shoaib` (history 558k > who 468k > show 341k);
Roman-Urdu completions take two visible slots; sentence-initial unknown words are never learned (`determineWordToUse` returns null);
`shou` matches Shuaib because `fold` shortens the key below the 4-letter prefix minimum.

### Task 1: Honorifics rework
- [x] TSV: kind `C` = context-only name (type kept for ordering), `S` = suffix trigger (classes); Muhammad/Mustafa/Ali/Hasnain + all P/Q/M/F/D/W names context-only
- [x] `ReligiousShortcuts.suggest`: names never expand except H names (Nabi/Rasul/Huzoor); suffix prefixes from 3 letters, ordered by prev type; fold prefix bug fixed
- [x] `afterWord`: only H names
- [x] Unit tests

### Task 2: English ranking and learning
- [x] Honorific placement: strong match (exact trigger, suffix after a name, prefix >= 5) gets one visible slot; other forms go to the end
- [x] Learned (history-only) completions cannot outrank a common dictionary completion on 1-3 typed letters
- [x] Roman-Urdu-on-English only in a visible slot for exact lexicon words with no strong English completion
- [x] Learn unknown sentence-initial words (decapitalised when auto-capitalised)
- [x] Emulator probe: sho/shou/alham/alhamdu/pompute/jekko/umar rad/nabi sal
- [x] Measurement harness (`SuggestionEval`, `tools/eval/`), technical terms and emoji out of the main slots, capital abbreviations of the typed letters demoted

Owner (2026-11-17): rarely uses autocorrect; what matters is the right options in the strip (judge by top-3).
English top-3, fresh install, 2967 cases (`make_typo_set.py`), before -> after: pre3 35.3 -> 42.0, pre4 63.3 -> 72.3, pre5 83.7 -> 88.7,
one-key typos 92.6-96.3 -> 92.6-97.3 (unchanged native engine). Autocorrect never fires inside the harness (not investigated).

### Task 3: Roman Urdu (ur-Latn) unknown words and teaching
- [x] Phonetic candidate for words the dictionary does not know (download, whatsapp), retroflex ٹ/ڈ variants offered
      (`RomanTransliterator.phonetic`: channel model + letter trigram model built at load from the dictionary; setting `PREF_SOUND_SPELLING`)
- [x] Listed loanword spellings found even when the channel cannot align them (`listedSpelling`); +33 chat/app loanwords, model rebuilt
- [x] Committed phonetic words are learned; long-press a suggestion -> check mark teaches it (English: typed word to personal dictionary)
- [x] Learned words rank first (`PersonalModel.preferred`, latest hand-pick wins)
- [x] Owner add-on: letter editing inside Urdu/Arabic words (`InputLogic.handleNativeLetterEdit`, `lettersFor`, setting `PREF_LETTER_EDIT`)

Held-out Dakshina (20,445 tokens, `tools/urdu/eval/Eval.java`, data in `~/AI_Works/datasets/dakshina/dakshina_dataset_v1.0/ur`, set `DAKSHINA`):
top-1 89.4 -> 89.7, top-3 93.1 -> 93.8, in-vocab top-1 91.1 -> 91.4, 1.8 -> 2.6 ms/query desktop. Urdu model heap +0.9 MB.
Emulator-verified: whatsapp/tiktok/faargiv, t inside فائنل -> ت with ٹ ط offered and swapped, k+h -> خ, teach via long press,
normal `mein kal aonga` unchanged. Not verified: real phone, Arabic letter editing, English long-press teach.

### Task 4: Real-time voice
- [ ] Research a streaming approach that fits the existing models and phone speed
- [ ] Tentative words shown while speaking, replaced as decoding firms up
