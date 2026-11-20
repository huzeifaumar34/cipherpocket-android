# Changelog

All notable changes to CipherPocket are documented here.

## Unreleased

### Added
- Initial Android project scaffold with Kotlin, Jetpack Compose and a C++17 engine.
- Native Base64 encode/decode operation shared by Android and the desktop CLI.
- JNI byte-array protocol, offline-first manifest and initial CI workflows.

### Fixed
- Upper/lower-case conversion no longer corrupts emoji and other 4-byte UTF-8 characters; malformed bytes pass through unchanged.
- Statistics now count characters instead of bytes.
- Morse encoding no longer leaves a trailing word separator after unsupported characters.

### Security
- Decrypted history previews and typed passwords are cleared when the app goes to the background.
