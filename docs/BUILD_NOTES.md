# Build notes

This file records first-build checks required by PROJECT.md. Do not silently change pinned versions to work around them.

## Version checks at project start

- AGP 9.5.0 requires Gradle 9.6.0 and JDK 17; both are pinned in the wrapper and CI.
- Kotlin 2.4.21 is pinned as specified. The Kotlin compatibility table lists full AGP support through 9.3.1, so the first CI run must confirm the AGP 9.5.0 built-in Kotlin integration and Compose compiler plugin work together. Resolve this documented compatibility point before changing versions.
- Compose UI 1.13.0 was still at RC01 when this scaffold was started, so the catalog pins 1.13.0-rc01 rather than a not-yet-published stable artifact.
- Material 3 1.5.0 is pinned as specified.
- OpenSSL is pinned to 3.5.9 LTS with its published SHA-256. The build script verifies the tarball before compiling any ABI.

## M0 validation boundary

M0 implements Base64 encode/decode only. All other operation identifiers are reserved and synchronized across Kotlin/C++, but return a clear not-yet-implemented error until their milestone is implemented. AES/PBKDF2 are not implemented in this milestone; later crypto operations must use OpenSSL and must not introduce hand-written cryptography.
