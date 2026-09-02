#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

avd_name="nexus_test"
skip_build=false
while (($#)); do
  case "$1" in
    --avd)
      avd_name="$2"
      shift 2
      ;;
    --skip-build)
      skip_build=true
      shift
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

read_local_property() {
  local name="$1"
  [[ -f local.properties ]] || return 0
  sed -n "s/^[[:space:]]*${name}[[:space:]]*=[[:space:]]*//p" local.properties | head -n 1
}

sdk_dir="$(read_local_property sdk.dir)"
sdk_dir="${sdk_dir:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}}"
if [[ -z "$sdk_dir" || ! -d "$sdk_dir" ]]; then
  echo "Android SDK not found. Configure local.properties sdk.dir, ANDROID_HOME, or ANDROID_SDK_ROOT." >&2
  exit 1
fi

adb="$sdk_dir/platform-tools/adb"
emulator="$sdk_dir/emulator/emulator"
[[ -x "$adb" ]] || { echo "adb not found: $adb" >&2; exit 1; }

if [[ "$skip_build" == false ]]; then
  ./gradlew assembleDebug --no-daemon
fi

connected_devices() {
  "$adb" devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }'
}

if [[ "$(connected_devices)" == "0" ]]; then
  [[ -x "$emulator" ]] || { echo "emulator not found: $emulator" >&2; exit 1; }
  if ! "$emulator" -list-avds | grep -Fxq "$avd_name"; then
    echo "AVD '$avd_name' not found. Available AVDs:" >&2
    "$emulator" -list-avds >&2
    exit 1
  fi
  nohup "$emulator" -avd "$avd_name" >"${TMPDIR:-/tmp}/nexus-android-emulator.log" 2>&1 </dev/null &
  emulator_pid=$!
  disown "$emulator_pid" 2>/dev/null || true
fi

deadline=$((SECONDS + 240))
until [[ "$(connected_devices)" != "0" ]] && [[ "$("$adb" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
  if (( SECONDS >= deadline )); then
    echo "Timed out waiting for emulator boot." >&2
    exit 1
  fi
  echo "Waiting for emulator boot..."
  sleep 3
done

apk="$PWD/app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$apk" ]] || { echo "APK not found: $apk" >&2; exit 1; }

"$adb" install -r "$apk"
"$adb" logcat -c
"$adb" shell am force-stop com.pinealctx.nexus
"$adb" shell am start -W -n com.pinealctx.nexus/.MainActivity
sleep 8

app_pid="$("$adb" shell pidof com.pinealctx.nexus | tr -d '\r')"
if [[ -z "$app_pid" ]]; then
  "$adb" logcat -d -t 300 | grep -E "FATAL EXCEPTION|AndroidRuntime|UnsatisfiedLinkError|NexusApp" || true
  echo "App process is not running after launch." >&2
  exit 1
fi

if "$adb" logcat -d -t 300 | grep -E "FATAL EXCEPTION|Failed to initialize core"; then
  echo "Fatal logcat entries found after launch." >&2
  exit 1
fi

echo "Nexus Android is running. pid=$app_pid"
