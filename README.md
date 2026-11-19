# CipherPocket

**A private toolkit for text and files — offline by design.**

CipherPocket is the Android companion to the conversor v2 project. It brings encoding, decoding and authenticated encryption into a compact Android app without accounts, telemetry, ads, cloud sync or network access.

## Project status

- [x] M0 scaffold: Android modules, native engine, JNI contract, Base64 encode/decode and CI workflow.
- [ ] M1 full native engine and desktop CLI parity.
- [ ] M2 complete text tools, Detect, History, Settings and English/Portuguese UI.
- [ ] M3 AES-256-GCM Vault and SAF file operations.
- [ ] M4 signed 1.0 release.

The complete requirements and acceptance criteria are in PROJECT.md.

## Architecture

- **app** — Kotlin, Jetpack Compose, Material 3 and ViewModel/StateFlow.
- **core** — Android library, background Engine and ByteArray-only NativeBridge.
- **engine** — portable C++17 library, shared with the desktop CLI and GoogleTest.
- **OpenSSL** — static 3.5 LTS build reserved for AES/PBKDF2; no hand-written cryptography.

## Privacy commitments

- No INTERNET permission.
- Android backups disabled.
- No persistent operation history.
- JNI exchanges bytes, not JNI String values.
- Classical ciphers will be labelled as educational and unsuitable for protecting secrets.

## Build

Requirements: JDK 17, Android SDK 36, NDK 28.2.13676358 and CMake 3.22.1.

Build the debug APK with ./gradlew :app:assembleDebug.

Build and test the portable engine with:
- cmake -S native -B native/build -DCMAKE_BUILD_TYPE=Release
- cmake --build native/build --parallel
- ctest --test-dir native/build --output-on-failure

The Android CI prepares the SDK and builds the pinned OpenSSL 3.5 LTS static libraries. The APK is emitted at app/build/outputs/apk/debug/app-debug.apk.

## License

Project code: MIT. Third-party notices, including OpenSSL (Apache-2.0), will be maintained in the app's open-source notices as the related modules are introduced.
