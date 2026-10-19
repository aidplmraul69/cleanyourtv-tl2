#!/usr/bin/env bash
# Build the launcher in ../launcher and install it on the TV (keeps it as default home).
set -euo pipefail
TV="${TV_HOST:?set TV_HOST to the TV IP, e.g. TV_HOST=192.168.1.50}:5555"
export JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
cd "$(dirname "$0")/../launcher"
./gradlew assembleRelease -q --console=plain
adb connect "$TV" >/dev/null
adb -s "$TV" install -r app/build/outputs/apk/release/app-release.apk
adb -s "$TV" shell pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION || true
adb -s "$TV" shell cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity
adb -s "$TV" shell am start -W -n com.gathin.tvhome/.MainActivity | grep -E '^(Status|Error)'
