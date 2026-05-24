#!/usr/bin/env bash
#
# Android SDK provisioning for "Claude Code on the web" sessions, so the SDK is
# baked into the environment snapshot and reused on later sessions.
#
# IMPORTANT: the setup script runs BEFORE the repository is checked out, so it
# cannot be referenced by path (e.g. `bash .claude/scripts/...` fails with 127).
# This file is the version-controlled source of truth: paste its contents
# verbatim into the environment's Setup script field (claude.ai/code →
# environment settings). It is intentionally self-contained with no repo deps.
#
# Prerequisites configured in the same environment settings dialog:
#   - Network access = Custom (defaults + dl.google.com, maven.google.com).
#     The Trusted default does NOT include dl.google.com / maven.google.com,
#     so AGP, AndroidX and the SDK packages below are all unreachable without it.
#     (api.foojay.io is NOT needed: JDK 17 is apt-installed below instead of
#     being resolved via Gradle's toolchain downloader.)
#   - Environment variable ANDROID_HOME=/opt/android-sdk (exports inside this
#     script do not reach the session shell; the env config does).
#
set -euo pipefail

SDK_ROOT="${ANDROID_HOME:-/opt/android-sdk}"
# Mirrors compileSdk in gradle/libs.versions.toml (not readable here: the repo
# is not checked out yet when this runs).
COMPILE_SDK="36"
CMDLINE_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
SDKMANAGER="${SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager"

command -v unzip >/dev/null 2>&1 || { apt-get update -qq && apt-get install -y -qq unzip; }

# build-logic is an included build that requests jvmToolchain(17); the root
# foojay resolver does not apply to included builds, so Gradle needs a real
# JDK 17 on disk (the base image ships JDK 21 only). Installing it lets Gradle's
# toolchain auto-detection find it under /usr/lib/jvm. Idempotent like unzip.
ls -d /usr/lib/jvm/java-17-openjdk* >/dev/null 2>&1 \
  || { apt-get update -qq && apt-get install -y -qq openjdk-17-jdk-headless; }

if [ ! -x "$SDKMANAGER" ]; then
  mkdir -p "${SDK_ROOT}/cmdline-tools"
  tmp="$(mktemp -d)"
  curl -fsSL -o "${tmp}/clt.zip" "$CMDLINE_URL"
  unzip -q "${tmp}/clt.zip" -d "$tmp"
  rm -rf "${SDK_ROOT}/cmdline-tools/latest"
  mv "${tmp}/cmdline-tools" "${SDK_ROOT}/cmdline-tools/latest"
  rm -rf "$tmp"
fi

# `yes` is killed by SIGPIPE once sdkmanager stops reading; without this
# pipefail would surface that as exit 141 and abort the script.
set +o pipefail
yes | "$SDKMANAGER" --sdk_root="$SDK_ROOT" --licenses >/dev/null
set -o pipefail
"$SDKMANAGER" --sdk_root="$SDK_ROOT" \
  "platform-tools" \
  "platforms;android-${COMPILE_SDK}" \
  "build-tools;${COMPILE_SDK}.0.0" >/dev/null
