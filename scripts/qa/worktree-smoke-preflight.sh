#!/usr/bin/env bash
set -euo pipefail

# Non-destructive smoke helper for local Android+backend loop.
# Designed for worktree lanes to validate environment quickly.

ROOT_ANDROID="/Users/diegolden/Code/LCX/lcx-android"
ROOT_WEB="/Users/diegolden/Code/LCX/v0-lcx-pwa"
ADB_BIN="${ADB_BIN:-/Users/diegolden/Library/Android/sdk/platform-tools/adb}"
JAVA_HOME_DEFAULT="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

RUN_INSTALL=false
if [[ "${1:-}" == "--install" ]]; then
  RUN_INSTALL=true
fi

echo "== Smoke Preflight =="
echo "ADB: $ADB_BIN"

if [[ ! -x "$ADB_BIN" ]]; then
  echo "ERROR: adb binary not found/executable at $ADB_BIN"
  exit 1
fi

# 1) Device + reverse ports
"$ADB_BIN" devices -l
"$ADB_BIN" reverse tcp:3000 tcp:3000
"$ADB_BIN" reverse tcp:54321 tcp:54321
"$ADB_BIN" reverse --list

# 2) Local backend checks
echo "\n== Backend checks =="
if command -v lsof >/dev/null 2>&1; then
  lsof -nP -iTCP:3000 -sTCP:LISTEN || echo "WARN: nothing listening on :3000"
fi

if command -v supabase >/dev/null 2>&1; then
  (cd "$ROOT_WEB" && supabase status) || true
else
  echo "WARN: supabase CLI not found"
fi

# 3) Build env extraction
ANON_KEY=""
if command -v supabase >/dev/null 2>&1; then
  ANON_KEY="$(cd "$ROOT_WEB" && supabase status -o env | awk -F= '/^ANON_KEY=/{print $2}' | sed 's/^"//; s/"$//')"
fi

if [[ -z "$ANON_KEY" ]]; then
  echo "WARN: ANON_KEY could not be auto-resolved"
fi

echo "\n== Env preview =="
echo "LCX_DEV_API_BASE_URL=http://127.0.0.1:3000"
echo "LCX_DEV_SUPABASE_URL=http://127.0.0.1:54321"
if [[ -n "$ANON_KEY" ]]; then
  echo "LCX_DEV_SUPABASE_ANON_KEY=<resolved>"
else
  echo "LCX_DEV_SUPABASE_ANON_KEY=<missing>"
fi

# 4) Optional install step
if [[ "$RUN_INSTALL" == true ]]; then
  echo "\n== installDevDebug =="
  export JAVA_HOME="${JAVA_HOME:-$JAVA_HOME_DEFAULT}"
  export PATH="$JAVA_HOME/bin:$PATH"
  export LCX_DEV_API_BASE_URL="http://127.0.0.1:3000"
  export LCX_DEV_SUPABASE_URL="http://127.0.0.1:54321"
  export LCX_DEV_SUPABASE_ANON_KEY="$ANON_KEY"

  (cd "$ROOT_ANDROID" && ./gradlew :app:installDevDebug --console=plain)
fi

echo "\nSmoke preflight complete."
