#!/usr/bin/env bash
# Installs the Android SDK command-line tools, accepts licences and installs the platform needed by this project.
# Runs automatically when the Codespace is created. Safe to run again.
set -euo pipefail

SDK_DIR="${ANDROID_HOME:-$HOME/android-sdk}"
TOOLS_ZIP="commandlinetools-linux-11076708_latest.zip"

echo ">> Java version:"
java -version

mkdir -p "$SDK_DIR/cmdline-tools"
if [ ! -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo ">> Downloading Android command-line tools..."
  curl -fsSL -o "/tmp/$TOOLS_ZIP" "https://dl.google.com/android/repository/$TOOLS_ZIP"
  rm -rf "$SDK_DIR/cmdline-tools/latest" /tmp/cmdline-tools
  unzip -q "/tmp/$TOOLS_ZIP" -d /tmp
  mv /tmp/cmdline-tools "$SDK_DIR/cmdline-tools/latest"
  rm -f "/tmp/$TOOLS_ZIP"
fi

export PATH="$SDK_DIR/cmdline-tools/latest/bin:$PATH"
yes | sdkmanager --licenses > /dev/null || true
sdkmanager "platform-tools" "platforms;android-36" "build-tools;35.0.0"

# Tell Gradle where the SDK is (this file is git-ignored).
echo "sdk.dir=$SDK_DIR" > local.properties

# Make sure the Gradle wrapper can run.
chmod +x ./gradlew || true

echo ">> Done. Try:  ./gradlew testDebugUnitTest"
