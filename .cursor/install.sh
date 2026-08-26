#!/usr/bin/env bash
# Idempotent Cloud Agent bootstrap for the CNV Android app.
# Installs the Android SDK components required by app/build.gradle.kts and
# app/../gradle/libs.versions.toml, wires up local.properties, and warms the
# Gradle build so a debug APK, unit tests, and lint all run offline afterwards.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"

# Base image already ships JDK 21, which matches gradle/gradle-daemon-jvm.properties.
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
export ANDROID_HOME="$ANDROID_SDK_ROOT"

# Pin the command-line tools revision so builds are reproducible.
CMDLINE_TOOLS_VERSION="16111833"
# SDK Platform for compileSdk = release(37) { minorApiLevel = 1 }.
PLATFORM="platforms;android-37.1"
# Build tools matching the compile SDK. AGP additionally auto-downloads its
# default build-tools (licenses are accepted below) during the warm-up build.
BUILD_TOOLS="build-tools;37.0.0"

install_cmdline_tools() {
  local sdkmanager="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
  if [ -x "$sdkmanager" ]; then
    echo "Android command-line tools already present."
    return
  fi
  echo "Installing Android command-line tools ($CMDLINE_TOOLS_VERSION)..."
  local workdir
  workdir="$(mktemp -d)"
  curl -fsSL -o "$workdir/cmdtools.zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
  unzip -q "$workdir/cmdtools.zip" -d "$workdir"
  mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
  rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  mv "$workdir/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  rm -rf "$workdir"
}

install_cmdline_tools
export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

echo "Accepting SDK licenses..."
yes | sdkmanager --licenses >/dev/null 2>&1 || true

echo "Installing SDK packages (idempotent)..."
sdkmanager "platform-tools" "$PLATFORM" "$BUILD_TOOLS" >/dev/null

# Gradle discovers the SDK through local.properties (git-ignored).
echo "sdk.dir=$ANDROID_SDK_ROOT" > "$REPO_ROOT/local.properties"

echo "Warming the Gradle build (downloads the wrapper + dependencies)..."
cd "$REPO_ROOT"
chmod +x ./gradlew
./gradlew :app:assembleDebug --no-daemon

echo "Android SDK ready at $ANDROID_SDK_ROOT"
