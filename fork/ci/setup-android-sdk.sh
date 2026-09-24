#!/usr/bin/env bash
#
# Provisions the Android SDK components this project needs and writes local.properties.
#
# Why this exists: the build has a native (JNI/CMake) component and pins an exact NDK and
# build-tools, so a plain `apt install` of the SDK is not enough. This script installs the same
# versions used by upstream's CI image (.ci/Dockerfile) via the official command-line tools,
# into a writable SDK location, and accepts the licenses.
#
# It is idempotent and reusable both locally and in CI. Override any version via environment
# variables (see defaults below).
#
# Usage:
#   fork/ci/setup-android-sdk.sh
#   ANDROID_HOME="$HOME/Android/Sdk" fork/ci/setup-android-sdk.sh
#
# After running, build with:
#   JAVA_HOME=<jdk17+ or jdk21> ./gradlew assembleUnstableDebug
#
set -euo pipefail

# On GitHub-hosted runners ANDROID_HOME is preset to the preinstalled SDK; reuse it. Otherwise
# default to a user-local SDK that needs no root.
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_HOME ANDROID_SDK_ROOT

# Versions — keep in sync with .ci/Dockerfile and build.gradle (compileSdk / ndkVersion).
CLI_TOOLS_URL="${CLI_TOOLS_URL:-https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip}"
ANDROID_PLATFORM_VERSION="${ANDROID_PLATFORM_VERSION:-35}"
ANDROID_BUILD_TOOLS_VERSION="${ANDROID_BUILD_TOOLS_VERSION:-35.0.0}"
ANDROID_NDK_VERSION="${ANDROID_NDK_VERSION:-28.2.13676358}"
ANDROID_CMAKE_VERSION="${ANDROID_CMAKE_VERSION:-3.22.1}"

REPO_ROOT="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

echo "Android SDK location: $ANDROID_HOME"
mkdir -p "$ANDROID_HOME"

# Locate sdkmanager; install command-line tools if it isn't already present.
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKMANAGER" ]; then
  echo "Installing Android command-line tools ..."
  tmp="$(mktemp -d)"
  curl -fsSL -o "$tmp/cli.zip" "$CLI_TOOLS_URL"
  unzip -q -o "$tmp/cli.zip" -d "$tmp"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -rf "$tmp"
fi

echo "Accepting SDK licenses ..."
# `yes` receives SIGPIPE (exit 141) once sdkmanager stops reading — expected, and not an error.
# Disable pipefail around this pipeline so the broken pipe doesn't abort the script (the pipeline's
# status then reflects sdkmanager itself).
set +o pipefail
yes | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses >/dev/null
set -o pipefail

echo "Installing SDK packages ..."
"$SDKMANAGER" --sdk_root="$ANDROID_HOME" \
  "platform-tools" \
  "platforms;android-${ANDROID_PLATFORM_VERSION}" \
  "build-tools;${ANDROID_BUILD_TOOLS_VERSION}" \
  "ndk;${ANDROID_NDK_VERSION}" \
  "cmake;${ANDROID_CMAKE_VERSION}"

echo "sdk.dir=$ANDROID_HOME" > "$REPO_ROOT/local.properties"
echo "Wrote $REPO_ROOT/local.properties (sdk.dir=$ANDROID_HOME)"
echo "Done."
