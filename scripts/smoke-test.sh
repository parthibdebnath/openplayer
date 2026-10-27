#!/usr/bin/env bash
# Installs an APK on an emulator and checks that it starts and stays up. Run it on every APK
# before it's published: 0.1.0-beta.3 shipped crashing on launch for everyone, and a single
# install-and-open would have caught it.
#
#   scripts/smoke-test.sh app.apk                 fresh install
#   scripts/smoke-test.sh app.apk previous.apk    also upgrades over previous.apk first
#
# Emulators only (the serial must start with "emulator-"): it uninstalls the app, which wipes
# its data. Set ANDROID_SERIAL to pick one when several are running.
set -u

PKG=com.hyperperformant.openplayer
# The applicationId differs from the Kotlin package, so the activity needs its full class name.
ACTIVITY=$PKG/io.github.parthibdebnath.openplayer.MainActivity
ADB=${ADB:-adb}
APK=${1:?usage: smoke-test.sh app.apk [previous.apk]}
PREVIOUS=${2:-}

if [ -z "${ANDROID_SERIAL:-}" ]; then
    ANDROID_SERIAL=$($ADB devices | awk '$1 ~ /^emulator-/ && $2 == "device" {print $1; exit}')
fi
case "${ANDROID_SERIAL:-}" in
    emulator-*) export ANDROID_SERIAL ;;
    *) echo "No running emulator found (this never runs against a phone)." >&2; exit 2 ;;
esac

failures=0
fail() { echo "FAIL: $*"; failures=$((failures + 1)); }
pass() { echo "ok:   $*"; }

# Launches cold, waits, and checks the process is alive and nothing of ours hit the crash log.
launch_and_check() {
    local label=$1
    $ADB logcat -b all -c
    $ADB shell am force-stop $PKG
    timeout 60 $ADB shell am start -W -n $ACTIVITY >/dev/null
    sleep "${SETTLE_SECONDS:-8}"
    local crash
    crash=$($ADB logcat -d -b crash | grep -A 12 "Process: $PKG")
    if [ -n "$crash" ]; then
        fail "$label: crashed"
        echo "$crash" | sed 's/^/      /'
    elif [ -z "$($ADB shell pidof $PKG)" ]; then
        fail "$label: process isn't running"
    else
        pass "$label"
    fi
    answer_permission_prompt
}

# The first launch asks for notification access. Left open, that system prompt survives a
# force-stop and swallows the next launch, so it's answered the way a person would: Allow.
answer_permission_prompt() {
    $ADB shell uiautomator dump /sdcard/smoke-ui.xml >/dev/null 2>&1 || return 0
    local bounds
    bounds=$($ADB shell cat /sdcard/smoke-ui.xml | tr '>' '\n' \
        | grep 'resource-id="com.android.permissioncontroller:id/permission_allow_button"' \
        | grep -o 'bounds="[^"]*"' | head -1 | tr -c '0-9\n' ' ')
    [ -z "$bounds" ] && return 0
    set -- $bounds
    $ADB shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
    sleep 2
    echo "      (answered the notification permission prompt: Allow)"
}

echo "Emulator $ANDROID_SERIAL, Android $($ADB shell getprop ro.build.version.release | tr -d '\r')"
$ADB uninstall $PKG >/dev/null 2>&1

if [ -n "$PREVIOUS" ]; then
    $ADB install "$PREVIOUS" >/dev/null || { echo "Could not install $PREVIOUS" >&2; exit 2; }
    timeout 60 $ADB shell am start -W -n $ACTIVITY >/dev/null
    sleep 5
    answer_permission_prompt
    if $ADB install -r "$APK" >/dev/null 2>&1; then
        launch_and_check "upgrade over $(basename "$PREVIOUS")"
    else
        fail "can't install over $(basename "$PREVIOUS") (signed with a different key?)"
    fi
    $ADB uninstall $PKG >/dev/null 2>&1
fi

$ADB install "$APK" >/dev/null || { echo "Could not install $APK" >&2; exit 2; }
launch_and_check "first launch"
launch_and_check "second launch"

# Leaving and coming back runs the foreground/background paths, which start and stop the
# playback service.
$ADB shell input keyevent KEYCODE_HOME
sleep 2
$ADB shell am start -n $ACTIVITY >/dev/null
sleep 3
if [ -z "$($ADB shell pidof $PKG)" ] || $ADB logcat -d -b crash | grep -q "Process: $PKG"; then
    fail "back from the home screen"
else
    pass "back from the home screen"
fi

if [ $failures -eq 0 ]; then echo "PASS"; else echo "$failures check(s) failed"; exit 1; fi
