#!/usr/bin/env bash
#
# Setup script to prepare a fresh Linux machine for developing this
# Kotlin Android project (AGP 8.7.3, Kotlin 2.0.21, JDK 21).
#
# Tested on Debian/Ubuntu-based distros. Adjust the package manager
# section if you use Fedora/Arch.
#
set -euo pipefail

# ----------------------------------------------------------------------
# Configurable versions
# ----------------------------------------------------------------------
JDK_VERSION="21"
# Android cmdline-tools "latest" zip (check the website for the newest build):
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

COMPILE_SDK="35"
BUILD_TOOLS="35.0.0"
ANDROID_SDK_ROOT="${HOME}/Android/Sdk"

# ----------------------------------------------------------------------
# 1. System packages (JDK, git, unzip, curl)
# ----------------------------------------------------------------------
echo ">>> Installing system packages..."
sudo apt-get update
sudo apt-get install -y \
    "openjdk-${JDK_VERSION}-jdk" \
    git \
    curl \
    unzip \
    wget

# Some 64-bit distros need 32-bit libs for older Android tooling:
sudo apt-get install -y libc6:i386 libstdc++6:i386 zlib1g:i386 2>/dev/null || \
    echo "    (32-bit libs skipped/unavailable - usually fine for modern tooling)"

# ----------------------------------------------------------------------
# 2. Android SDK command-line tools
# ----------------------------------------------------------------------
echo ">>> Installing Android command-line tools into ${ANDROID_SDK_ROOT}..."
mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools"

TMP_ZIP="$(mktemp --suffix=.zip)"
curl -L "${CMDLINE_TOOLS_URL}" -o "${TMP_ZIP}"
unzip -q "${TMP_ZIP}" -d "${ANDROID_SDK_ROOT}/cmdline-tools"
rm -f "${TMP_ZIP}"

# sdkmanager expects tools under cmdline-tools/latest/
rm -rf "${ANDROID_SDK_ROOT}/cmdline-tools/latest"
mv "${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools" \
   "${ANDROID_SDK_ROOT}/cmdline-tools/latest"

# ----------------------------------------------------------------------
# 3. Environment variables (persisted to ~/.bashrc)
# ----------------------------------------------------------------------
echo ">>> Configuring environment variables..."
{
    echo ""
    echo "# --- Android SDK (added by setup-dev.sh) ---"
    echo "export ANDROID_HOME=\"${ANDROID_SDK_ROOT}\""
    echo "export ANDROID_SDK_ROOT=\"${ANDROID_SDK_ROOT}\""
    echo "export PATH=\"\$PATH:\$ANDROID_HOME/cmdline-tools/latest/bin\""
    echo "export PATH=\"\$PATH:\$ANDROID_HOME/platform-tools\""
} >> "${HOME}/.bashrc"

export ANDROID_HOME="${ANDROID_SDK_ROOT}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT}"
export PATH="${PATH}:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin"

# ----------------------------------------------------------------------
# 4. Install SDK packages and accept licenses
# ----------------------------------------------------------------------
echo ">>> Accepting licenses..."
yes | sdkmanager --licenses >/dev/null

echo ">>> Installing SDK platform, build-tools, and platform-tools..."
sdkmanager \
    "platform-tools" \
    "platforms;android-${COMPILE_SDK}" \
    "build-tools;${BUILD_TOOLS}"

# ----------------------------------------------------------------------
# 5. Tell Gradle where the SDK is (local.properties)
#    Run this script from the project root for this step to take effect.
# ----------------------------------------------------------------------
if [ -f "./settings.gradle.kts" ] || [ -f "./settings.gradle" ]; then
    echo ">>> Writing local.properties..."
    echo "sdk.dir=${ANDROID_SDK_ROOT}" > local.properties
fi

echo ""
echo "============================================================"
echo " Setup complete!"
echo " Open a new terminal (or run 'source ~/.bashrc') and then:"
echo "     ./gradlew build"
echo "============================================================"
