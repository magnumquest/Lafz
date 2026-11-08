# Release notes

## 0.6.6
- **More scientific and technical suggestions.** English and Urdu now include additional medical, biological, engineering and physics terms while keeping everyday word suggestions prominent.
- **Check for updates.** Open Settings → About to check for a newer release, download it and install it.

## 0.6.5
- Bug fixes.

## 0.6.4
- **Roman Urdu suggestions on the English keyboard.** Type `kahan`, `kidher`, `zarorat` or `kia` on the normal English keyboard and Urdu words in English letters now show up in the suggestion bar, and after a word like `kia` the next likely word is offered. English suggestions always stay first. Turn it off in Settings → Text correction.
- **Shift characters shown on the Urdu (Pakistan) keyboard.** Every key now shows a small character in its top-right corner: the one you get by long-pressing it or using shift, so letters, marks and symbols that are not on the main keys are easy to find. Long-pressing a key offers that character first.
- **Suggestions that don't disappear.** The suggestion bar now stays visible everywhere you type, including browser address bars and fields that ask keyboards to hide it. It is still hidden in password fields. If you changed this setting yourself earlier, check Settings → Text correction.
- **Plain "Allah" is back in the suggestions.** Typing Allah used to show only "Allah Subhanahu Wa Ta'ala". Now the plain word is offered next to its honorific forms, and long honorifics no longer push every other suggestion out of the bar.

## 0.6.3
- **GIF and sticker search.** Find reactions, preview them, and insert or share them in supported messaging apps.
- **Redesigned emoji panel.** Rounded Emoji, GIFs and Stickers tabs match the keyboard theme, with a scrollable category bar and clearer selection indicators.
- **Local media library.** Save reactions for later or import GIF, WebP, PNG and JPEG images. Browse and share saved media offline.
- **WhatsApp sticker packs.** Create packs from 3–30 local static images, or share animated reactions as GIFs.
- **Optional media controls.** Enable GIFs and stickers in Settings → Emoji, GIFs & stickers. Regular typing and emoji remain available offline, and conversation text is never sent to media providers.

## 0.6.2
- **A separate, scrollable smart clipboard.** Open the toolbar arrow → clipboard icon to browse pinned clips, detected details and recent history. Phone numbers, IBANs, bank account numbers, cards, verification codes, emails and links appear as individual cards. Tap a card to paste just that detail; hold it to pin it independently of the original copied text.
- **Clipboard management.** Pinned details survive clearing or expiry of recent clips. Swipe recent clips left to delete them, hold a saved clip to pin or unpin it, and tap the original clip to paste its full text. Duplicate detected values are combined. Reopening the clipboard does not restore a clip deleted during the current keyboard session.
- **Clipboard suggestions that stay visible.** Suggestions refresh when you copy with the keyboard open, recover the latest copy missed while it was inactive, and are no longer hidden by the toolbar or replaced by a late prediction update. Scroll through extracted details above the keys, or choose “Paste full text”. Normal word suggestions resume when you type.
- **Better extraction and privacy.** Recognises local and international phone numbers, including Urdu and Arabic digits, alongside the existing bank details and codes. IBAN and card checksums are checked; nearby labels help distinguish phones, accounts and verification codes. Numeric fields filter out incompatible details. Content marked sensitive by its source app is masked in suggestions and excluded from saved history; password fields mask previews too. Extraction runs entirely on the device.
- **Refreshed toolbar icons.** A consistent set of 34 rounded outline icons for the main and clipboard toolbars, with matching sizes and stroke weights. Icons follow the selected theme colours and respect custom icon choices.
- **Settings.** Smart clipboard and clipboard suggestions remain optional in Settings → Text correction. Clipboard history, retention and pinned ordering remain in Settings → Preferences.

## 0.6.1
- **Keyboard appearance.** Refined key shapes, spacing and sizing. The keyboard follows system display and font sizes, uses system colors on Android 12 and newer, and keeps your custom appearance settings.

## 0.6.0
- **Offline voice typing (experimental, off by default).** Add a speech model in Settings → Voice typing, then use the microphone key to dictate in English, Urdu or Arabic. Recognition runs on your device.
- Voice typing settings include a speed test for imported models. Dictation can be switched off at any time.

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

## 0.4.3
- Improved swipe typing controls and resource handling when swipe typing is disabled.

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
