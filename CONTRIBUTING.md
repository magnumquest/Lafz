# Contributing to Lafz

Lafz is an Android keyboard focused on English, Urdu and Arabic typing. Contributions that improve reliability, accessibility, language support or typing quality are welcome.

## Build and run

Use JDK 17 or newer and install the Android SDK and NDK required by the project.

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Open the project in Android Studio or install the debug APK on an emulator or device. Before submitting a change, check the diff and include the steps needed to verify any user-visible behavior.

## Change guidelines

- Keep typing responsive, especially in code paths used on every key press.
- Preserve Lafz's Urdu and Arabic transliteration, suggestions, layouts and settings when changing shared keyboard behavior.
- Keep optional network features separate from ordinary typing, and describe any data sent to an external service.
- Keep changes focused and explain their effect from a user's perspective.
- Include tests for behavior that can be tested without a device. For keyboard UI or input changes, include emulator or device verification steps.
- Credit external code and data, and ensure their licenses are compatible with this project.

For layout changes, see [layouts.md](layouts.md). For dictionary sources and licenses, see [docs/dictionaries/README.md](docs/dictionaries/README.md). HeliBoard updates are handled through the [upstream review workflow](docs/upstream-workflow.md).

## License

By contributing, you agree that your contribution is distributed under the [GNU GPL v3.0](LICENSE), unless a file or asset carries a different license notice.
