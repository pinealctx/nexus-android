#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

read_local_property() {
  local name="$1"
  [[ -f local.properties ]] || return 0
  sed -n "s/^[[:space:]]*${name}[[:space:]]*=[[:space:]]*//p" local.properties | head -n 1
}

sdk_dir="$(read_local_property sdk.dir)"
sdk_dir="${sdk_dir:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}}"

failures=0
check_command() {
  local command_name="$1"
  if command -v "$command_name" >/dev/null 2>&1; then
    printf 'ok  %-18s %s\n' "$command_name" "$(command -v "$command_name")"
  else
    printf 'err %-18s not found\n' "$command_name"
    failures=$((failures + 1))
  fi
}

check_command java
check_command buf

if [[ -x ./gradlew ]]; then
  printf 'ok  %-18s %s\n' gradlew "$PWD/gradlew"
else
  printf 'err %-18s not executable\n' gradlew
  failures=$((failures + 1))
fi

if [[ -z "$sdk_dir" || ! -d "$sdk_dir" ]]; then
  printf 'err %-18s not configured\n' android-sdk
  failures=$((failures + 1))
else
  printf 'ok  %-18s %s\n' android-sdk "$sdk_dir"
  for relative_path in \
    "platforms/android-36/android.jar" \
    "build-tools/36.0.0" \
    "platform-tools/adb"; do
    if [[ -e "$sdk_dir/$relative_path" ]]; then
      printf 'ok  %-18s %s\n' "$(basename "$relative_path")" "$sdk_dir/$relative_path"
    else
      printf 'err %-18s missing: %s\n' "$(basename "$relative_path")" "$sdk_dir/$relative_path"
      failures=$((failures + 1))
    fi
  done

  emulator="$sdk_dir/emulator/emulator"
  if [[ -x "$emulator" ]]; then
    printf 'ok  %-18s %s\n' emulator "$emulator"
    if "$emulator" -list-avds | grep -Fxq nexus_test; then
      printf 'ok  %-18s %s\n' nexus_test "API 36 development AVD"
    else
      printf 'err %-18s not found\n' nexus_test
      failures=$((failures + 1))
    fi
  else
    printf 'err %-18s missing: %s\n' emulator "$emulator"
    failures=$((failures + 1))
  fi
fi

if [[ -d ../nexus-proto/proto ]]; then
  printf 'ok  %-18s %s\n' nexus-proto "$(cd ../nexus-proto && pwd)/proto"
else
  printf 'err %-18s expected at ../nexus-proto/proto\n' nexus-proto
  failures=$((failures + 1))
fi

if (( failures > 0 )); then
  printf '\nEnvironment check failed with %d issue(s).\n' "$failures"
  exit 1
fi

printf '\nEnvironment is ready.\n'
