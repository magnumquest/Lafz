# Plan: learned-words backup, smart clipboard (codes), voice research (2026-10-23)

Owner rules: every feature behind a Settings switch, original behaviour and speed untouched when off; not a Gboard clone; commit to `main`, no release build, no push.

### Task 1: Learned words backup and restore
- [x] `PersonalModel.merge` (sums counts) + test
- [x] `RomanScript.exportLearned / importLearned / reloadLearned`
- [x] learned files included in the full backup zip, learned models reloaded after a restore
- [x] Settings: "Export learned words" / "Import learned words" (merge)

### Task 2: Smart code extractor
- [ ] `SmartCodeExtractor` (OTP / PIN, IBAN, card numbers with Luhn, account numbers) pure Kotlin + unit tests

### Task 3: Smart clipboard suggestion and history chips
- [ ] Settings switch "Smart clipboard (codes and numbers)"
- [ ] clipboard suggestion shows the extracted values as chips, tap pastes just that value
- [ ] clipboard history entries show chips, tap pastes the value
- [ ] Settings switch "Keep copied codes out of history" (default off)

### Task 4: Clipboard history improvements
- [ ] decide from use on the emulator, keep each behind a switch

### Task 5: Voice typing research
- [ ] `docs/research/voice-typing.md`: models, sizes, Urdu/Arabic quality, integration options, verdict
- [ ] if feasible: spike on the emulator before any mic key
