# Optional keyboard media

## Provider decision (2026-10-25)

* GIPHY: everyday GIFs and transparent animated reactions. Private builds bundle `GIFFY_KEY` from root `.env` into BuildConfig. No key entry for recipients. API requests fall back to public web search, which can also be selected explicitly without any key. Explicit ratings on API requests, strict rating filtering of web results. Show attribution. https://developers.giphy.com/docs/api/
* Tenor: its public API shut down June 30, 2026, and its public site reset connections on this connection. https://support.google.com/tenor/answer/10455265
* FlorisBoard: inspiration for separate emoji/media experiences; retain Lafz's existing offline emoji implementation. https://github.com/florisboard/florisboard
* WhatsApp: Android commitContent first, only for advertised MIME types. Explicit Android sharing otherwise. Native sticker packs require 512×512 WebP, 3–30 stickers, and a dedicated content provider. https://github.com/WhatsApp/stickers/tree/main/Android

Reachability was tested inside the emulator through the production repository implementation: both GIPHY API and web search returned GIF/sticker results and downloaded previews and full media. Local media remains available offline. GIPHY’s mature-rating toggle is retained.

## Implementation

1. Dedicated settings destination: master switch off by default, bundled API key with optional override and keyless mode, mature-rating toggle, connection tests and cache controls.
2. Optional GIF/Sticker buttons in the existing emoji panel. A separate browser activity allows searching with the normal keyboard without intercepting text intended for the conversation.
3. Explicit searches, paginated results, static bounded thumbnails, tap preview, local library/import, provider attribution, explicit insert/share actions.
4. MIME-negotiated insertion after returning to the original editor, with package/field/type checks and short-lived in-memory pending state. Never fall back to inserting a URL or modifying the clipboard.
5. Native WhatsApp pack export from local static stickers; online animated reactions retain their animation and are shared as GIFs.
6. No provider SDK, no media initialization or network requests during normal typing. IO and image decoding on background coroutines; cancel screen work, cap downloads, thumbnails and library size. Private FileProvider URI grants for transfers.

## Validation

Build and existing unit suite; focused tests for URL restrictions, byte bounds, rating/provider gates, media parsing, insertion target matching, and sticker conversion/provider metadata. Emulator smoke checks where available. Emulator network checks cover search, previews and complete validated media downloads. Actual WhatsApp acceptance still needs a device with WhatsApp installed.

## Follow-up

Animated native sticker pack conversion, richer favorites/collections, and a browser embedded entirely in the IME can build on the provider and library layer. Avoid adding video transcoding or autoplay to the typing process.

## Implemented and checked

The initial implementation includes all six steps above, with a separate browser activity rather than a fully embedded IME browser. Optional media settings are searchable. Grid previews are static; selected GIFs can play on Android 9+.

Offline Gradle debug assembly and the unit suite passed (277 tests, using the repository's offline-test exclusions), including native graphics tests for the WebP conversion, transparent margins, 100 KB sticker limits and 96×96 tray icon. Emulator checks covered settings, default-off switches, missing-key messaging, local PNG import, preview, and the Android share chooser with the image attached. No message was sent.

Not verified: WhatsApp rich-content acceptance or its pack-import UI (WhatsApp is not installed in the emulator), and typing-latency benchmarks. The implementation uses WhatsApp's documented protocol and standard Android MIME negotiation; that is not a substitute for a WhatsApp device test.

## Live emulator verification after bundled-key support

A temporary instrumentation APK invoked the actual production MediaRepository from the emulator. No message was sent. Original media preferences were restored after the probe.

| Path | Search | Preview | Full validated download |
| --- | --- | --- | --- |
| Bundled API · GIFs | 24 results | 8,063 bytes | 1,387,162 bytes |
| Bundled API · stickers | 24 results | 6,962 bytes | 30,626 bytes |
| Keyless web · GIFs | 24 results | 8,063 bytes | 1,387,162 bytes |
| Keyless web · stickers | 24 results | 6,962 bytes | 30,626 bytes |

The keyless adapter reads the public page's rendered React results; it never extracts or borrows website credentials. It filters out unknown ratings, hidden and removed content, and separates GIFs from stickers. Public pages currently expose an initial batch of roughly 25 results; the browser pages through that batch locally. Refining the query searches the full site's catalog. Website markup may change, so this path is less stable than the official API.

The bundled key is read using Gradle's tracked file-content provider, affects every build variant, is not logged, and is not copied as a `.env` asset. The optional override is hidden behind a separate button. Builds without `.env` remain functional through keyless web search. Media as a whole is still off by default.

Release artifact: `dist/Lafz_0.6.3-release.apk`, signed with the existing release key. The bundled GIPHY key is included without copying the `.env` file.

## Emoji panel refresh

Removed the unsupported adult provider, including its requests, credentials, domain allowlist entries, settings, tab, transfer flags and video-only preview code. GIFs and stickers continue using GIPHY.

The keyboard panel now uses native, theme-colored rounded Emoji/GIFs/Stickers controls with icons and ripple feedback. Category icons sit underneath, scroll horizontally at 48 dp touch targets and show the current category with a tonal selection capsule. These controls use the keyboard palette, including custom themes. No Compose tree or provider requests are created when opening the emoji panel. Category space is included in panel measurements while media navigation takes space from the emoji grid, preserving the surrounding keyboard layout.

For 0.6.3, debug/release assembly, release lint and the offline unit suite passed. Emulator screenshots verified the actual emoji panel in light and dark mode and with media disabled. Temporary instrumentation was used only for checks and is not part of the release.
