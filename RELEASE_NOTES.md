# Release notes

## 0.5.0
- **Learns from your typing (Roman Urdu and Roman Arabic), on the device only.** Lafz remembers the words you commit, which word follows which and the spellings you use, and ranks suggestions accordingly.
  Only the final word counts (space, punctuation or tapping a suggestion), never keystrokes. A backspace right after an auto-commit takes it back, and a spelling needs to be used twice before it has any effect.
  Nothing is learned in password fields or incognito mode. Typing speed is unchanged. Turn it off or clear it in Settings → Text correction.
- **Export, import and back up learned words.** Settings → Text correction: Export saves them to a file, Import adds them to what is already learned on the other phone (nothing is lost). They are also part of the full backup.
- **Smart clipboard.** Codes and numbers inside copied text (OTP and PIN codes, IBAN, card and account numbers) are offered as separate chips above the keyboard and under the entries in the clipboard history. Tap a chip to paste just that number.
  Switch it off in Settings → Text correction to get the old behaviour. Optional: keep copied codes out of the clipboard history.
- **Contacts in Roman Urdu and Roman Arabic.** With "Use contacts" on, names from your contacts appear as suggestions while you type in the Urdu and Arabic layouts (never auto-committed).
- **Settings switch for the religious honorific suggestions.**
- **Key size and spacing:** slightly taller rows, more room between keys and no tiny hint characters on the keys. These are the defaults for new installs; settings you changed yourself stay as they are.
- Voice typing research is in `docs/research/voice-typing.md` (nothing ships yet; the toolbar voice key still hands over to an installed voice keyboard such as FUTO Voice Input).

## 0.4.3
- Swipe typing off now means off: nothing swipe-related is loaded (no native swipe library, no model copy, no diagnostics), and turning it off releases the engines and word lists that were already in memory.
- Version bump only otherwise; swipe behaviour is unchanged from 0.4.2.

## 0.4.2
- Fixed the keyboard vanishing while swiping on arm64 phones (a native crash in the old gesture path), words dropped from fast swipes, and the slow first swipe (engines now load in the background).
- Settings → Gesture typing: choose the swipe engine (FUTO Swipe, Google library if you load one yourself, or Lafz's built-in matcher) and open Swipe diagnostics.
- Apostrophe words (`don't`) and weighted Roman Urdu/Arabic dictionaries.

## 0.4.0 – 0.4.1
- FUTO Swipe decoder for English, Roman Urdu and Roman Arabic, fully offline.
- Modern toolbar icons and a redesigned setup wizard.

## 0.3.0
- Contextual emoji suggestions (English, Urdu, Arabic, including Roman input), redesigned Settings, bundled Urdu dictionary.

## 0.2.0
- Arabic v2 model, non-blocking suggestion strip, live transliteration for Urdu and Arabic, native numerals and punctuation.

## 0.1.0
- First release: rebrand, Roman → Urdu and Roman → Arabic transliteration, honorific suggestions.
