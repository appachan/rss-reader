#!/usr/bin/env bash
#
# Provisions the Android SDK for "Claude Code on the web" sessions so the
# SDK is baked into the environment snapshot and reused on later sessions.
#
# Wire this up as the environment's Setup script (claude.ai/code → environment
# settings) with a single line:
#
#     bash .claude/scripts/setup-claude-code-web.sh
#
# Prerequisites configured in the same environment settings dialog:
#   - Network access = Custom (defaults + dl.google.com, maven.google.com;
#     add api.foojay.io if relying on Gradle's JDK 17 toolchain resolver).
#     The Trusted default does NOT include dl.google.com / maven.google.com,
#     so AGP, AndroidX and the SDK packages below are all unreachable without it.
#   - Environment variable ANDROID_HOME=/opt/android-sdk (exports inside this
#     script do not reach the session shell; the env config does).
#
set -euo pipefail

SDK_ROOT="${ANDROID_HOME:-/opt/android-sdk}"
CMDLINE_VERSION="11076708"
CMDLINE_URL="https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_VERSION}_latest.zip"
SDKMANAGER="${SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager"

# Run from the repo root regardless of the caller's cwd so the version
# catalog lookup below is stable.
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

# compileSdk is the source of truth in the version catalog; track it instead
# of hardcoding so the installed platform never drifts from the build.
COMPILE_SDK="36"
CATALOG="gradle/libs.versions.toml"
if [ -f "$CATALOG" ]; then
  parsed="$(sed -n 's/^compileSdk *= *"\([0-9][0-9]*\)".*/\1/p' "$CATALOG" | head -n1)"
  [ -n "$parsed" ] && COMPILE_SDK="$parsed"
fi

command -v unzip >/dev/null 2>&1 || { apt-get update -qq && apt-get install -y -qq unzip; }

if [ ! -x "$SDKMANAGER" ]; then
  mkdir -p "${SDK_ROOT}/cmdline-tools"
  tmp="$(mktemp -d)"
  curl -fsSL -o "${tmp}/clt.zip" "$CMDLINE_URL"
  unzip -q "${tmp}/clt.zip" -d "$tmp"
  rm -rf "${SDK_ROOT}/cmdline-tools/latest"
  mv "${tmp}/cmdline-tools" "${SDK_ROOT}/cmdline-tools/latest"
  rm -rf "$tmp"
fi

yes | "$SDKMANAGER" --sdk_root="$SDK_ROOT" --licenses >/dev/null
"$SDKMANAGER" --sdk_root="$SDK_ROOT" \
  "platform-tools" \
  "platforms;android-${COMPILE_SDK}" \
  "build-tools;${COMPILE_SDK}.0.0" >/dev/null

# Bake the Gradle distribution into the snapshot too. --version only downloads
# and unzips the wrapper distribution; it does not configure the build, so it
# does not require the Google Maven repository.
./gradlew --version >/dev/null 2>&1 || true
