#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OPENSSL_DIR="$ROOT_DIR/third_party/openssl"
VERSION="$(tr -d '[:space:]' < "$OPENSSL_DIR/VERSION")"
EXPECTED_SHA="$(tr -d '[:space:]' < "$OPENSSL_DIR/SHA256")"
NDK_VERSION="${ANDROID_NDK_VERSION:-28.2.13676358}"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
NDK_ROOT="${ANDROID_NDK_HOME:-$ANDROID_SDK_ROOT/ndk/$NDK_VERSION}"
TOOLCHAIN="$NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64"
JOBS="${JOBS:-2}"

if [[ ! -d "$TOOLCHAIN" ]]; then
    echo "Android NDK toolchain not found: $TOOLCHAIN" >&2
    exit 1
fi

export ANDROID_NDK_ROOT="$NDK_ROOT"
export ANDROID_NDK_HOME="$NDK_ROOT"
export PATH="$TOOLCHAIN/bin:$PATH"

WORK_DIR="$(mktemp -d)"
cleanup() {
    rm -rf "$WORK_DIR"
}
trap cleanup EXIT

ARCHIVE="$WORK_DIR/openssl-$VERSION.tar.gz"
URL="https://github.com/openssl/openssl/releases/download/openssl-$VERSION/openssl-$VERSION.tar.gz"

echo "Downloading OpenSSL $VERSION..."
curl --fail --location --retry 3 --output "$ARCHIVE" "$URL"
echo "$EXPECTED_SHA  $ARCHIVE" | sha256sum --check -

build_abi() {
    local abi="$1"
    local target="$2"
    local source_root="$WORK_DIR/source-$abi"
    local source_dir="$source_root/openssl-$VERSION"
    local prefix="$WORK_DIR/install-$abi"
    local output="$OPENSSL_DIR/build/$abi"

    mkdir -p "$source_root"
    tar -xzf "$ARCHIVE" -C "$source_root"

    echo "Building OpenSSL $VERSION for $abi ($target)..."
    pushd "$source_dir" >/dev/null

    CFLAGS="-O2 -fPIC -D__ANDROID_API__=26" \
        ./Configure "$target" -D__ANDROID_API__=26 \
        no-shared no-tests no-apps no-ssl3 no-comp no-dso no-engine \
        no-module no-docs no-legacy no-weak-ssl-ciphers \
        --prefix="$prefix" --libdir=lib

    make -j"$JOBS" build_libs
    make install_dev
    popd >/dev/null

    rm -rf "$output"
    mkdir -p "$output/lib"
    cp -a "$prefix/include" "$output/include"
    cp "$prefix/lib/libcrypto.a" "$output/lib/libcrypto.a"
}

build_abi "armeabi-v7a" "android-arm"
build_abi "arm64-v8a" "android-arm64"
build_abi "x86_64" "android-x86_64"

echo "OpenSSL $VERSION static builds are ready in $OPENSSL_DIR/build/"
