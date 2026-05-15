#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
WEB_ROOT="${WEB_ROOT:-/Users/diegolden/Code/LCX-OS/lcx-pwa}"
ADB_BIN="${ADB_BIN:-/Users/diegolden/Library/Android/sdk/platform-tools/adb}"
JAVA_HOME_DEFAULT="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

SERIAL=""
ALLOW_REAL_CHARGE=false
RUN_TICKET_HARDWARE_PATH=false
RUN_INSTALL=true

usage() {
  cat <<'USAGE'
Usage:
  scripts/qa/android-hardware-e2e.sh --serial <adb-serial> --allow-real-charge

Options:
  --serial <serial>              ADB serial. Defaults to first connected device.
  --allow-real-charge            Enables the real $1.00 Zettle test.
  --run-ticket-hardware-path     Also run the seeded-ticket charge/print path.
  --no-install                   Skip Gradle install and test APK install.
  LCX_E2E_ZETTLE_NETWORK_MODE    Required preconfigured network: wifi, cellular, or none.
  -h, --help                     Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial)
      SERIAL="${2:-}"
      shift 2
      ;;
    --allow-real-charge)
      ALLOW_REAL_CHARGE=true
      shift
      ;;
    --run-ticket-hardware-path)
      RUN_TICKET_HARDWARE_PATH=true
      shift
      ;;
    --no-install)
      RUN_INSTALL=false
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown arg: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ "$RUN_TICKET_HARDWARE_PATH" == true && "$ALLOW_REAL_CHARGE" != true ]]; then
  echo "ERROR: --run-ticket-hardware-path requires --allow-real-charge" >&2
  exit 2
fi

if [[ ! -x "$ADB_BIN" ]]; then
  echo "ERROR: adb not executable at $ADB_BIN" >&2
  exit 1
fi

if [[ -z "$SERIAL" ]]; then
  SERIAL="$("$ADB_BIN" devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')"
fi
if [[ -z "$SERIAL" ]]; then
  echo "ERROR: no connected ADB device in state 'device'" >&2
  exit 1
fi

adbq() {
  "$ADB_BIN" -s "$SERIAL" "$@"
}

shell_quote() {
  local value="$1"
  printf "'%s'" "${value//\'/\'\\\'\'}"
}

resolve_supabase_value() {
  local name="$1"
  local fallback="${2:-}"
  local direct="${!name:-}"
  if [[ -n "$direct" ]]; then
    printf '%s' "$direct"
    return
  fi
  if [[ -n "$fallback" ]]; then
    printf '%s' "$fallback"
  fi
}

DATE_DIR="$(date +%Y%m%d)"
TS="$(date +%H%M%S)"
EVIDENCE_DIR="$ANDROID_ROOT/docs/evidence/$DATE_DIR"
mkdir -p "$EVIDENCE_DIR"

LOG_FILE="$EVIDENCE_DIR/android-hardware-e2e-$TS.log"
SUMMARY_FILE="$EVIDENCE_DIR/android-hardware-e2e-$TS.md"
PAYLOAD_FILE="$EVIDENCE_DIR/android-hardware-e2e-$TS.jsonl"
SEED_FILE="$EVIDENCE_DIR/android-hardware-e2e-$TS.seed.json"
INSTRUMENTATION_FILE="$EVIDENCE_DIR/android-hardware-e2e-$TS.instrumentation.txt"

echo "== Android Hardware E2E =="
echo "Android root: $ANDROID_ROOT"
echo "Web root: $WEB_ROOT"
echo "ADB serial: $SERIAL"
echo "Evidence: $SUMMARY_FILE"

DEVICE_LINE="$(adbq devices -l | awk -v serial="$SERIAL" '$1 == serial { print }')"
if [[ -z "$DEVICE_LINE" ]]; then
  echo "ERROR: device $SERIAL not found in adb devices -l" >&2
  exit 1
fi
echo "Device: $DEVICE_LINE"

if [[ ! -f "$ANDROID_ROOT/feature/printing/libs/BrotherPrintLibrary.aar" ]]; then
  echo "ERROR: BrotherPrintLibrary.aar is missing" >&2
  exit 1
fi

if ! command -v node >/dev/null 2>&1; then
  echo "ERROR: node is required for the seed helper" >&2
  exit 1
fi

if [[ ! -f "$WEB_ROOT/package.json" ]]; then
  echo "ERROR: WEB_ROOT does not look like the PWA repo: $WEB_ROOT" >&2
  exit 1
fi

if ! adbq shell pm list packages com.izettle.android | grep -q "com.izettle.android"; then
  echo "ERROR: Zettle app package com.izettle.android is not installed on $SERIAL" >&2
  exit 1
fi

if ! adbq shell pm list packages com.brother.printservice | grep -q "com.brother.printservice"; then
  echo "ERROR: Brother print service package com.brother.printservice is not installed on $SERIAL" >&2
  exit 1
fi

check_tcp_listener() {
  local port="$1"
  local label="$2"
  if command -v lsof >/dev/null 2>&1; then
    lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null || {
      echo "ERROR: no local listener on :$port for $label" >&2
      exit 1
    }
  fi
}

if command -v lsof >/dev/null 2>&1; then
  check_tcp_listener 3000 "PWA dev server"
  check_tcp_listener 54321 "local Supabase API"
fi

SUPABASE_ENV=""
if command -v supabase >/dev/null 2>&1; then
  SUPABASE_ENV="$(cd "$WEB_ROOT" && supabase status -o env 2>/dev/null || true)"
fi
extract_supabase_env() {
  local key="$1"
  printf '%s\n' "$SUPABASE_ENV" |
    awk -F= -v key="$key" '$1 == key { value=$2; gsub(/^"/, "", value); gsub(/"$/, "", value); print value; exit }'
}

ANON_KEY_FROM_STATUS="$(extract_supabase_env ANON_KEY)"
SERVICE_ROLE_KEY_FROM_STATUS="$(extract_supabase_env SERVICE_ROLE_KEY)"

export WEB_ROOT
export NEXT_PUBLIC_SUPABASE_URL="${NEXT_PUBLIC_SUPABASE_URL:-http://127.0.0.1:54321}"
export NEXT_PUBLIC_SUPABASE_ANON_KEY="$(resolve_supabase_value NEXT_PUBLIC_SUPABASE_ANON_KEY "$ANON_KEY_FROM_STATUS")"
export SUPABASE_SERVICE_ROLE_KEY="$(resolve_supabase_value SUPABASE_SERVICE_ROLE_KEY "$SERVICE_ROLE_KEY_FROM_STATUS")"
export LCX_E2E_PWA_BASE_URL="${LCX_E2E_PWA_BASE_URL:-http://127.0.0.1:3000}"
export LCX_E2E_BRANCH="${LCX_E2E_BRANCH:-La Esperanza}"
export LCX_E2E_OPERATOR_FULL_NAME="${LCX_E2E_OPERATOR_FULL_NAME:-Operador E2E}"
export LCX_E2E_PIN="${LCX_E2E_PIN:-1234}"
export LCX_E2E_TICKET_AMOUNT="${LCX_E2E_TICKET_AMOUNT:-1.00}"
export LCX_E2E_BROTHER_PRINTER_NAME="${LCX_E2E_BROTHER_PRINTER_NAME:-QL-810W}"
export LCX_E2E_ZETTLE_NETWORK_MODE="${LCX_E2E_ZETTLE_NETWORK_MODE:-wifi}"

case "$LCX_E2E_ZETTLE_NETWORK_MODE" in
  wifi|cellular|none) ;;
  *)
    echo "ERROR: LCX_E2E_ZETTLE_NETWORK_MODE must be wifi, cellular, or none" >&2
    exit 2
    ;;
esac

if [[ -z "$NEXT_PUBLIC_SUPABASE_ANON_KEY" || -z "$SUPABASE_SERVICE_ROLE_KEY" ]]; then
  echo "ERROR: could not resolve Supabase anon/service-role keys. Export NEXT_PUBLIC_SUPABASE_ANON_KEY and SUPABASE_SERVICE_ROLE_KEY." >&2
  exit 1
fi

echo "== Seed device auth and ticket data =="
node "$ANDROID_ROOT/scripts/qa/seed-device-auth.mjs" > "$SEED_FILE"
SEEDED_TICKET_NUMBER="$(node -e "const s=require(process.argv[1]); console.log(s.ticket?.ticketNumber || '')" "$SEED_FILE")"
SEEDED_TICKET_ID="$(node -e "const s=require(process.argv[1]); console.log(s.ticket?.id || '')" "$SEED_FILE")"
echo "Seeded ticket: ${SEEDED_TICKET_NUMBER:-<none>}"

echo "== ADB reverse =="
adbq reverse tcp:3000 tcp:3000
adbq reverse tcp:54321 tcp:54321
adbq reverse tcp:8080 tcp:8080
adbq reverse --list

export JAVA_HOME="${JAVA_HOME:-$JAVA_HOME_DEFAULT}"
export PATH="$JAVA_HOME/bin:$PATH"
export LCX_ANDROID_APPLICATION_ID="com.cleanx.app"
export LCX_DEV_APPLICATION_ID_SUFFIX=""
export LCX_DEV_API_BASE_URL="${LCX_DEV_API_BASE_URL:-http://127.0.0.1:3000}"
export LCX_DEV_PLATFORM_BASE_URL="${LCX_DEV_PLATFORM_BASE_URL:-http://127.0.0.1:8080}"
export LCX_DEV_NOTIFICATIONS_BASE_URL="${LCX_DEV_NOTIFICATIONS_BASE_URL:-http://127.0.0.1:8080}"
export LCX_DEV_SUPABASE_URL="${LCX_DEV_SUPABASE_URL:-http://127.0.0.1:54321}"
export LCX_DEV_SUPABASE_ANON_KEY="$NEXT_PUBLIC_SUPABASE_ANON_KEY"
export LCX_DEV_USE_REAL_ZETTLE="true"
export LCX_DEV_USE_REAL_BROTHER="true"
export LCX_ZETTLE_APPROVED_APPLICATION_ID="${LCX_ZETTLE_APPROVED_APPLICATION_ID:-com.cleanx.app}"

if [[ "$RUN_INSTALL" == true ]]; then
  echo "== Gradle install =="
  (cd "$ANDROID_ROOT" && ./gradlew :app:installDevDebug :app:installDevDebugAndroidTest --console=plain)
fi

echo "== Device prep =="
grant_app_permissions() {
  for permission in \
    android.permission.BLUETOOTH_CONNECT \
    android.permission.BLUETOOTH_SCAN \
    android.permission.ACCESS_FINE_LOCATION \
    android.permission.CAMERA; do
    adbq shell pm grant com.cleanx.app "$permission" >/dev/null 2>&1 || true
  done
}

clear_app_data_once() {
  echo "Clearing com.cleanx.app data once at suite start"
  adbq shell pm clear com.cleanx.app >/dev/null || true
  grant_app_permissions
}

reset_app_process() {
  adbq shell am force-stop com.cleanx.app >/dev/null 2>&1 || true
  grant_app_permissions
}

validate_device_network() {
  if [[ "$LCX_E2E_ZETTLE_NETWORK_MODE" == "none" ]]; then
    echo "Network preflight skipped (LCX_E2E_ZETTLE_NETWORK_MODE=none)"
    return
  fi

  local wifi_status mobile_data connectivity_dump wifi_active cellular_active
  wifi_status="$(adbq shell cmd wifi status 2>/dev/null | tr -d '\r' || true)"
  mobile_data="$(adbq shell settings get global mobile_data 2>/dev/null | tr -d '\r' || true)"
  connectivity_dump="$(adbq shell dumpsys connectivity 2>/dev/null | tr -d '\r' || true)"
  wifi_active=false
  cellular_active=false
  if printf '%s\n' "$connectivity_dump" | grep -Eq 'WiFi active networks: \{[^}]*[0-9][^}]*\}'; then
    wifi_active=true
  fi
  if printf '%s\n' "$connectivity_dump" | grep -Eq 'Cellular active networks: \{[^}]*[0-9][^}]*\}'; then
    cellular_active=true
  fi

  echo "Network preflight: required=$LCX_E2E_ZETTLE_NETWORK_MODE wifi_active=$wifi_active cellular_active=$cellular_active mobile_data_setting=$mobile_data"
  case "$LCX_E2E_ZETTLE_NETWORK_MODE" in
    wifi)
      if [[ "$wifi_active" != true || "$cellular_active" == true ]]; then
        echo "ERROR: expected Wi-Fi as the only active network before running Zettle. Prepare the QA phone network; the runner will not change network state." >&2
        exit 1
      fi
      ;;
    cellular)
      if [[ "$wifi_active" == true || "$cellular_active" != true ]]; then
        echo "ERROR: expected cellular as the only active network before running Zettle. Prepare the QA phone network; the runner will not change network state." >&2
        exit 1
      fi
      ;;
  esac

  if ! adbq shell ping -c 1 -W 3 8.8.8.8 >/dev/null 2>&1; then
    echo "ERROR: QA phone has no outbound network. Prepare Wi-Fi/cellular before running; the runner will not toggle network during payment." >&2
    exit 1
  fi
}

validate_device_network
clear_app_data_once

WINDOW_ANIMATION_SCALE="$(adbq shell settings get global window_animation_scale | tr -d '\r' || true)"
TRANSITION_ANIMATION_SCALE="$(adbq shell settings get global transition_animation_scale | tr -d '\r' || true)"
ANIMATOR_DURATION_SCALE="$(adbq shell settings get global animator_duration_scale | tr -d '\r' || true)"
WINDOW_ANIMATION_SCALE="${WINDOW_ANIMATION_SCALE:-1}"
TRANSITION_ANIMATION_SCALE="${TRANSITION_ANIMATION_SCALE:-1}"
ANIMATOR_DURATION_SCALE="${ANIMATOR_DURATION_SCALE:-1}"

CAP_PID=""
stop_capture() {
  if [[ -z "$CAP_PID" ]]; then
    return
  fi

  pkill -INT -P "$CAP_PID" >/dev/null 2>&1 || true
  kill -INT "$CAP_PID" >/dev/null 2>&1 || true
  for _ in 1 2 3 4 5; do
    if ! kill -0 "$CAP_PID" >/dev/null 2>&1; then
      CAP_PID=""
      return
    fi
    sleep 0.2
  done

  pkill -TERM -P "$CAP_PID" >/dev/null 2>&1 || true
  kill -TERM "$CAP_PID" >/dev/null 2>&1 || true
  for _ in 1 2 3 4 5; do
    if ! kill -0 "$CAP_PID" >/dev/null 2>&1; then
      CAP_PID=""
      return
    fi
    sleep 0.2
  done

  pkill -KILL -P "$CAP_PID" >/dev/null 2>&1 || true
  kill -KILL "$CAP_PID" >/dev/null 2>&1 || true
  wait "$CAP_PID" >/dev/null 2>&1 || true
  CAP_PID=""
}

redact_sensitive_stream() {
  perl -pe 's/(Authorization: Bearer )[A-Za-z0-9._-]+/${1}[REDACTED]/g; s/((?:\\)?"access_token(?:\\)?"\s*:\s*(?:\\)?")[^"\\]+/${1}[REDACTED]/g; s/((?:\\)?"refresh_token(?:\\)?"\s*:\s*(?:\\)?")[^"\\]+/${1}[REDACTED]/g; s/((?:\\)?"pin(?:\\)?"\s*:\s*(?:\\)?")[^"\\]+/${1}[REDACTED]/g; s/(accessToken=)[A-Za-z0-9._-]+/${1}[REDACTED]/g; s/(refreshToken=)[A-Za-z0-9._-]+/${1}[REDACTED]/g; s/(pin=)[^[:space:]&]+/${1}[REDACTED]/g'
}

redact_sensitive_files() {
  local files=()
  if [[ -f "$PAYLOAD_FILE" ]]; then
    files+=("$PAYLOAD_FILE")
  fi
  if [[ -f "$LOG_FILE" ]]; then
    files+=("$LOG_FILE")
  fi
  if [[ "${#files[@]}" -eq 0 ]]; then
    return
  fi

  perl -0pi -e 's/(Authorization: Bearer )[A-Za-z0-9._-]+/${1}[REDACTED]/g; s/((?:\\)?"access_token(?:\\)?"\s*:\s*(?:\\)?")[^"\\]+/${1}[REDACTED]/g; s/((?:\\)?"refresh_token(?:\\)?"\s*:\s*(?:\\)?")[^"\\]+/${1}[REDACTED]/g; s/((?:\\)?"pin(?:\\)?"\s*:\s*(?:\\)?")[^"\\]+/${1}[REDACTED]/g; s/(accessToken=)[A-Za-z0-9._-]+/${1}[REDACTED]/g; s/(refreshToken=)[A-Za-z0-9._-]+/${1}[REDACTED]/g; s/(pin=)[^[:space:]&]+/${1}[REDACTED]/g' "${files[@]}"
}

restore_device() {
  set +e
  adbq shell settings put global window_animation_scale "$WINDOW_ANIMATION_SCALE" >/dev/null 2>&1
  adbq shell settings put global transition_animation_scale "$TRANSITION_ANIMATION_SCALE" >/dev/null 2>&1
  adbq shell settings put global animator_duration_scale "$ANIMATOR_DURATION_SCALE" >/dev/null 2>&1
  stop_capture
  redact_sensitive_files
}
trap restore_device EXIT

adbq shell settings put global window_animation_scale 0
adbq shell settings put global transition_animation_scale 0
adbq shell settings put global animator_duration_scale 0

echo "== Logcat capture =="
adbq logcat -c
if command -v rg >/dev/null 2>&1; then
  (adbq logcat -v threadtime | rg -i "TXN|HTTP|TICKET|PAYMENT|PRINT|AUTH|WATER|CHECKLIST|CAJA|Correlation|Session|Zettle|Brother" | redact_sensitive_stream) > "$LOG_FILE" &
else
  (adbq logcat -v threadtime | grep -Ei "TXN|HTTP|TICKET|PAYMENT|PRINT|AUTH|WATER|CHECKLIST|CAJA|Correlation|Session|Zettle|Brother" | redact_sensitive_stream) > "$LOG_FILE" &
fi
CAP_PID=$!

echo "== Instrumentation =="
set +e
TEST_STATUS=0
LAST_TEST_CLASS=""
LAST_CLASS_PASSED=false
: > "$INSTRUMENTATION_FILE"
: > "$PAYLOAD_FILE"

REMOTE_PAYLOAD="/sdcard/Android/data/com.cleanx.app/files/payload-capture/payload-capture.jsonl"
append_payload_capture() {
  if adbq shell "[ -f '$REMOTE_PAYLOAD' ]"; then
    adbq shell "cat '$REMOTE_PAYLOAD'" | redact_sensitive_stream >> "$PAYLOAD_FILE"
  fi
}

TEST_CLASSES=(
  "com.cleanx.lcx.e2e.PreflightHardwareE2eTest"
  "com.cleanx.lcx.e2e.DeviceLoginE2eTest"
  "com.cleanx.lcx.e2e.CriticalOperatorSmokeE2eTest"
  "com.cleanx.lcx.e2e.RealBrotherPrintE2eTest"
  "com.cleanx.lcx.e2e.RealZettleChargeE2eTest"
  "com.cleanx.lcx.e2e.TicketHardwarePathE2eTest"
)

for test_class in "${TEST_CLASSES[@]}"; do
  echo "-- $test_class --" | tee -a "$INSTRUMENTATION_FILE"
  if [[ "$test_class" == "com.cleanx.lcx.e2e.TicketHardwarePathE2eTest" &&
    "$LAST_TEST_CLASS" == "com.cleanx.lcx.e2e.RealZettleChargeE2eTest" &&
    "$LAST_CLASS_PASSED" != true ]]; then
    echo "Skipping ticket hardware path because RealZettleChargeE2eTest did not pass" | tee -a "$INSTRUMENTATION_FILE"
    TEST_STATUS=1
    LAST_TEST_CLASS="$test_class"
    LAST_CLASS_PASSED=false
    continue
  fi

  if [[ "$test_class" == "com.cleanx.lcx.e2e.TicketHardwarePathE2eTest" &&
    "$LAST_TEST_CLASS" == "com.cleanx.lcx.e2e.RealZettleChargeE2eTest" &&
    "$LAST_CLASS_PASSED" == true ]]; then
    echo "Preserving app process and data for ticket path to reuse Zettle session" | tee -a "$INSTRUMENTATION_FILE"
    grant_app_permissions
  else
    reset_app_process
  fi

  INSTRUMENT_CMD="am instrument -w -r"
  INSTRUMENT_CMD+=" -e class $(shell_quote "$test_class")"
  INSTRUMENT_CMD+=" -e lcxBranch $(shell_quote "$LCX_E2E_BRANCH")"
  INSTRUMENT_CMD+=" -e lcxOperatorName $(shell_quote "$LCX_E2E_OPERATOR_FULL_NAME")"
  INSTRUMENT_CMD+=" -e lcxPin $(shell_quote "$LCX_E2E_PIN")"
  INSTRUMENT_CMD+=" -e lcxAllowRealCharge $(shell_quote "$ALLOW_REAL_CHARGE")"
  INSTRUMENT_CMD+=" -e lcxRunTicketHardwarePath $(shell_quote "$RUN_TICKET_HARDWARE_PATH")"
  INSTRUMENT_CMD+=" -e lcxSeededTicketNumber $(shell_quote "$SEEDED_TICKET_NUMBER")"
  INSTRUMENT_CMD+=" -e lcxSeededTicketId $(shell_quote "$SEEDED_TICKET_ID")"
  INSTRUMENT_CMD+=" -e lcxBrotherPrinterName $(shell_quote "$LCX_E2E_BROTHER_PRINTER_NAME")"
  INSTRUMENT_CMD+=" -e lcxRealChargeTimeoutMs $(shell_quote "${LCX_E2E_REAL_CHARGE_TIMEOUT_MS:-300000}")"
  INSTRUMENT_CMD+=" com.cleanx.app.test/androidx.test.runner.AndroidJUnitRunner"
  CLASS_OUTPUT="$(mktemp "${TMPDIR:-/tmp}/lcx-e2e-class.XXXXXX")"
  adbq shell "$INSTRUMENT_CMD" | tee "$CLASS_OUTPUT" | tee -a "$INSTRUMENTATION_FILE"
  CLASS_STATUS=${PIPESTATUS[0]}
  append_payload_capture
  CLASS_FAILED=false
  if [[ "$CLASS_STATUS" -ne 0 ]] || grep -Eq "FAILURES!!!|INSTRUMENTATION_STATUS_CODE: -2|commandError=true" "$CLASS_OUTPUT"; then
    TEST_STATUS=1
    CLASS_FAILED=true
  fi
  rm -f "$CLASS_OUTPUT"
  LAST_TEST_CLASS="$test_class"
  if [[ "$CLASS_FAILED" == true ]]; then
    LAST_CLASS_PASSED=false
  else
    LAST_CLASS_PASSED=true
  fi
done
set -e

if grep -q "commandError=true" "$INSTRUMENTATION_FILE"; then
  TEST_STATUS=1
fi
if grep -Eq "FAILURES!!!|INSTRUMENTATION_STATUS_CODE: -2" "$INSTRUMENTATION_FILE"; then
  TEST_STATUS=1
fi

redact_sensitive_files

LOG_LINES="$(wc -l < "$LOG_FILE" | tr -d ' ')"
PAYLOAD_LINES="$(wc -l < "$PAYLOAD_FILE" | tr -d ' ')"
RESULT_LABEL="PASS"
if [[ "$TEST_STATUS" -ne 0 ]]; then
  RESULT_LABEL="FAIL"
fi

cat > "$SUMMARY_FILE" <<SUMMARY
# Android Hardware E2E

Date: $(date -u +"%Y-%m-%dT%H:%M:%SZ")
Result: $RESULT_LABEL
Device: $DEVICE_LINE
Package: com.cleanx.app
Allow real charge: $ALLOW_REAL_CHARGE
Run ticket hardware path: $RUN_TICKET_HARDWARE_PATH
Brother printer: $LCX_E2E_BROTHER_PRINTER_NAME
Zettle required network: $LCX_E2E_ZETTLE_NETWORK_MODE
App data clear scope: suite start only; Zettle session preserved across test classes

## Seed
- Branch: $LCX_E2E_BRANCH
- Operator: $LCX_E2E_OPERATOR_FULL_NAME
- Ticket: ${SEEDED_TICKET_NUMBER:-none}

## Evidence
- Instrumentation: $INSTRUMENTATION_FILE
- Logcat: $LOG_FILE ($LOG_LINES lines)
- Payload capture: $PAYLOAD_FILE ($PAYLOAD_LINES lines)
- Seed data: $SEED_FILE
SUMMARY

echo "== Summary =="
cat "$SUMMARY_FILE"

exit "$TEST_STATUS"
