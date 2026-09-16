#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK_ROOT" ]]; then
  if [[ -d "$HOME/Library/Android/sdk" ]]; then
    SDK_ROOT="$HOME/Library/Android/sdk"
  else
    SDK_ROOT="$HOME/Android/Sdk"
  fi
fi

if [[ -d "$SDK_ROOT" ]]; then
  printf 'sdk.dir=%s\n' "$SDK_ROOT" > local.properties
fi

if [[ -x "./gradlew" ]]; then
  GRADLE=(./gradlew)
elif command -v gradle >/dev/null 2>&1; then
  GRADLE=(gradle)
else
  GRADLE_VERSION="8.10.2"
  CACHE_DIR="$ROOT/.pocketforge-gradle"
  mkdir -p "$CACHE_DIR"
  if [[ ! -x "$CACHE_DIR/gradle-$GRADLE_VERSION/bin/gradle" ]]; then
    curl -fsSL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$CACHE_DIR/gradle.zip"
    unzip -q -o "$CACHE_DIR/gradle.zip" -d "$CACHE_DIR"
  fi
  GRADLE=("$CACHE_DIR/gradle-$GRADLE_VERSION/bin/gradle")
fi

echo "PocketForge CI: unit tests + lint + beta APK + hardened release APK"
"${GRADLE[@]}" :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease --stacktrace --no-daemon

APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
RELEASE_APK="$ROOT/app/build/outputs/apk/release/app-release-unsigned.apk"
test -f "$APK"
test -f "$RELEASE_APK"

APKSIGNER=""
AAPT=""
if [[ -d "$SDK_ROOT/build-tools" ]]; then
  APKSIGNER="$(find "$SDK_ROOT/build-tools" -type f -name apksigner 2>/dev/null | sort | tail -n 1 || true)"
  AAPT="$(find "$SDK_ROOT/build-tools" -type f -name aapt 2>/dev/null | sort | tail -n 1 || true)"
fi
if [[ -n "$APKSIGNER" ]]; then
  "$APKSIGNER" verify --verbose "$APK"
fi

# Play builds must not retain the beta-only permission that can install downloaded APKs.
if [[ -n "$AAPT" ]] && "$AAPT" dump permissions "$RELEASE_APK" | grep -q "android.permission.REQUEST_INSTALL_PACKAGES"; then
  echo "RED: release APK still requests REQUEST_INSTALL_PACKAGES"
  exit 1
fi

if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$APK" | tee "$APK.sha256"
else
  shasum -a 256 "$APK" | tee "$APK.sha256"
fi

echo "GREEN beta: $APK"
echo "GREEN release hardening check: $RELEASE_APK"
