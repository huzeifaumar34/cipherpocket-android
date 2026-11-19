# CipherPocket

**A private toolkit for text and files — offline by design.**

CipherPocket is the Android companion to conversor v2. It brings encoding, classical ciphers and authenticated encryption into a compact Android experience without accounts, telemetry, ads, cloud sync or network access.

## Project status

- [x] M0 scaffold and native Base64 baseline.
- [x] Native C++ implementation for encoders, classical ciphers, text analysis, base conversion and AES-256-GCM.
- [ ] M0 Android acceptance: CI green, debug APK built and Base64 verified on a device.
- [ ] M1 native test coverage and desktop CLI parity.
- [ ] M2 complete Compose screens, Detect, History, Settings and English/Portuguese UI.
- [ ] M3 Vault and SAF file operations.
- [ ] M4 signed 1.0 release.

The detailed requirements and acceptance criteria are in PROJECT.md. Do not treat this project as release-ready until all roadmap and device checks pass.

## Architecture

- **app** — Kotlin, Jetpack Compose, Material 3 and ViewModel/StateFlow.
- **core** — Android library, background Engine and ByteArray-only NativeBridge.
- **engine** — portable C++17 library, shared with the desktop CLI and GoogleTest.
- **OpenSSL** — static OpenSSL 3.5 LTS for AES-256-GCM and PBKDF2. Cryptographic primitives are delegated to OpenSSL.

## Implemented native operations

Binary, hexadecimal and Base64 conversion; Morse; Caesar and brute force; ROT13; Atbash; Vigenère; repeating-key XOR; AES-256-GCM text and raw-byte formats; format detection; reverse; case conversion; statistics; letter frequency; base conversion 2–36; and password-strength feedback.

Classical ciphers and repeating-key XOR are educational only and must not be used to protect secrets. Use AES-256-GCM for confidentiality and integrity.

## Privacy commitments

- No INTERNET permission.
- Android backups disabled.
- No persistent operation history.
- JNI exchanges bytes, not JNI String values.
- Native temporary copies of operation parameters are wiped after use.
- Initial file support is limited to 10 MB.

## Build

Requirements: JDK 17, Android SDK 36, NDK 28.2.13676358 and CMake 3.22.1.

Build the debug APK with ./gradlew :app:assembleDebug.

Build and test the portable engine with:
- cmake -S native -B native/build -DCMAKE_BUILD_TYPE=Release
- cmake --build native/build --parallel
- ctest --test-dir native/build --output-on-failure

The CLI reads input from stdin. Operation parameters are passed with CP_ARGUMENT1 and CP_ARGUMENT2 environment variables. The Android CI builds OpenSSL 3.5 LTS as static libraries for the supported ABIs; the debug APK is emitted at app/build/outputs/apk/debug/app-debug.apk.

## License

Project code: MIT. Third-party notices, including OpenSSL (Apache-2.0), must be kept in the app's open-source notices.
