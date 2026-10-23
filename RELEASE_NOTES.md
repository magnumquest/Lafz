# Release notes

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
