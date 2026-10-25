# Release notes

## 0.6.3
- **GIFs and stickers, when you want them.** Enable Settings → Emoji, GIFs & stickers, then choose GIFs or Stickers from the emoji panel. Search GIPHY, preview a result and explicitly insert or share it. The private release includes the configured GIPHY key, so recipients do not need an account or key entry. Keyless web search is also available and is used if API requests fail; it shows the website’s initial batch of results.
- **A refreshed emoji panel.** Rounded Emoji / GIFs / Stickers controls, matching outline icons and ripple feedback follow the keyboard’s colors. Emoji categories sit underneath in a scrollable row with a tonal selection indicator and 48 dp touch targets. Offline emoji remains available with media disabled.
- **Your local media library.** Import GIF, WebP, PNG and JPEG images, save online reactions for later, preview them and remove saved items. Local media works offline. Selected GIFs can play on Android 9 and newer; grid previews stay static.
- **WhatsApp sharing and sticker packs.** Insert media into editors that advertise support, or use Android’s share chooser to pick WhatsApp. Export 3–30 local static images as a native WhatsApp sticker pack. Online animated stickers are shared as GIF reactions; animated native sticker packs are not supported yet. WhatsApp receiving and pack import still need confirmation on a phone with WhatsApp installed.
- **Typing stays offline.** Media is off by default. No provider request runs when opening the emoji panel or during ordinary typing. Searches and downloads run in the separate media browser, with bounded downloads, private URI grants and storage limits. Conversation text is never sent to GIF providers. Internet permission is used for optional media search and downloads.
- **Connection checks.** GIPHY GIF/sticker searches, previews and complete downloads passed from the Pakistan-connected emulator through both API and keyless web search. The nonworking adult provider has been removed entirely.
- **In-place upgrade.** Version code 6003, using the same package ID and release signing certificate as 0.6.2. Install the release APK over the existing app to keep settings and data.

## 0.6.2
- **A separate, scrollable smart clipboard.** Open the toolbar arrow → clipboard icon to browse pinned clips, detected details and recent history. Phone numbers, IBANs, bank account numbers, cards, verification codes, emails and links appear as individual cards. Tap a card to paste just that detail; hold it to pin it independently of the original copied text.
- **Clipboard management.** Pinned details survive clearing or expiry of recent clips. Swipe recent clips left to delete them, hold a saved clip to pin or unpin it, and tap the original clip to paste its full text. Duplicate detected values are combined. Reopening the clipboard does not restore a clip deleted during the current keyboard session.
- **Clipboard suggestions that stay visible.** Suggestions refresh when you copy with the keyboard open, recover the latest copy missed while it was inactive, and are no longer hidden by the toolbar or replaced by a late prediction update. Scroll through extracted details above the keys, or choose “Paste full text”. Normal word suggestions resume when you type.
- **Better extraction and privacy.** Recognises local and international phone numbers, including Urdu and Arabic digits, alongside the existing bank details and codes. IBAN and card checksums are checked; nearby labels help distinguish phones, accounts and verification codes. Numeric fields filter out incompatible details. Content marked sensitive by its source app is masked in suggestions and excluded from saved history; password fields mask previews too. Extraction runs entirely on the device.
- **Refreshed toolbar icons.** A consistent set of 34 rounded outline icons for the main and clipboard toolbars, with matching sizes and stroke weights. Icons follow the selected theme colours and respect custom icon choices.
- **In-place upgrade.** Uses the same package ID and release signing certificate as 0.6.1, with version code 6002. Install the release APK over the existing app to keep your settings and data.
- **Settings.** Smart clipboard and clipboard suggestions remain optional in Settings → Text correction. Clipboard history, retention and pinned ordering remain in Settings → Preferences.

## 0.6.1
- **UI / Design fixes.** The keyboard now follows Gboard's look and measurements, in dp and sp, so it scales with the system display size and font size like Gboard does.
  - Key height, row spacing, key gaps, letter size and the space bar text match Gboard. The number row is an extra row on top: the other rows keep their size instead of being squeezed.
  - Gboard's key structure: flat keys, rounded-rectangle shift, delete, comma and period keys, round `?123` and enter keys.
  - Colours follow the system (Material You) on Android 12 and newer. Settings you changed yourself (theme, height, gaps, font size) are kept.

## 0.6.0
- **Offline voice typing (experimental, off by default).** Settings → Voice typing → "Dictate with the microphone key". Tap the microphone key in the toolbar, speak, tap it again: the text is typed at the cursor. Urdu, Arabic and English, chosen by the keyboard language you are using. Everything runs on the phone; the audio never leaves it.
  - The speech models are large and are not part of the app: copy them to the phone and pick the folder in the same Settings screen (Urdu: Whisper small 190 MB, English: Whisper base.en 60 MB, Arabic: Omnilingual 300M 350 MB).
  - Only the model for the current keyboard language is loaded, it stays loaded while you type, it is swapped when you change the language and freed when you switch dictation off. With the switch off nothing is loaded and no microphone permission is used.
  - Runs in its own process, so it never slows down typing. The microphone permission is asked for when you switch dictation on.
  - Measured on a Nothing phone (8 cores): English and Arabic transcribe about 5 times faster than real time, Urdu about 1.3 times faster. Urdu word error rate about 25 % on clean read speech. Details: `docs/research/voice-typing.md`.
- Settings → Voice typing also has a speed test (quick and full) with the result saved as a text file.
- If dictation is off, the toolbar microphone key behaves as before (hands over to an installed voice keyboard such as FUTO Voice Input).

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
