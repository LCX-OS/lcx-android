#!/usr/bin/env bash
set -euo pipefail
set +x

usage() {
  cat <<'USAGE'
Usage:
  LCX_PLATFORM_BASE_URL=https://api.cleanx.mx \
  LCX_PLATFORM_BEARER_TOKEN=... \
  scripts/qa/loyalty-platform-smoke.sh

Optional:
  LCX_AUTH_EMAIL=<email>                       # used to derive a Supabase access token when bearer is empty
  LCX_AUTH_PASSWORD=<password>                 # used to derive a Supabase access token when bearer is empty
  NEXT_PUBLIC_SUPABASE_URL=<url>               # read from env or lcx-pwa/.env.local when deriving a token
  NEXT_PUBLIC_SUPABASE_ANON_KEY=<key>          # read from env or lcx-pwa/.env.local when deriving a token
  LCX_SMOKE_ENV_FILE=<path>                    # defaults to ../.env.development
  LCX_PLATFORM_ENV_FILE=<path>                 # defaults to ../lcx-platform/deploy/gcp-k3s/prod.env
  LCX_PWA_ENV_FILE=<path>                      # defaults to ../lcx-pwa/.env.local
  LCX_LOYALTY_SMOKE_ACCOUNT_ID=<uuid>          # also checks account detail and wallet issue
  LCX_LOYALTY_SMOKE_WALLET_LINKS=true          # opens issued Apple/Google wallet links, if jq is installed
  LCX_LOYALTY_SMOKE_MUTATE=true                # also queues wallet resync for the account
  LCX_LOYALTY_SMOKE_PROVIDER=google|apple      # provider for optional resync

If LCX_PLATFORM_BASE_URL is empty, the script reads PUBLIC_BASE_URL from LCX_PLATFORM_ENV_FILE.
If LCX_PLATFORM_BEARER_TOKEN is empty, the script can obtain one through Supabase Auth password sign-in
using LCX_AUTH_EMAIL/LCX_AUTH_PASSWORD plus NEXT_PUBLIC_SUPABASE_URL/NEXT_PUBLIC_SUPABASE_ANON_KEY.

The script never prints bearer tokens. Wallet link responses are not printed because they contain public tokens.
USAGE
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "ERROR: missing required command: $1" >&2
    exit 1
  fi
}

read_dotenv_value() {
  local file="$1"
  local key="$2"
  local value

  if [[ ! -f "$file" ]]; then
    return 0
  fi

  value="$(
    sed -nE "s/^[[:space:]]*(export[[:space:]]+)?${key}[[:space:]]*=[[:space:]]*//p" "$file" |
      tail -n 1
  )"
  value="${value%$'\r'}"
  if [[ "$value" == \"*\" && "$value" == *\" ]]; then
    value="${value:1:${#value}-2}"
  elif [[ "$value" == \'*\' && "$value" == *\' ]]; then
    value="${value:1:${#value}-2}"
  fi

  printf '%s' "$value"
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
android_root="$(cd "${script_dir}/../.." && pwd)"
workspace_root="$(cd "${android_root}/.." && pwd)"

platform_env_file="${LCX_PLATFORM_ENV_FILE:-${workspace_root}/lcx-platform/deploy/gcp-k3s/prod.env}"
pwa_env_file="${LCX_PWA_ENV_FILE:-${workspace_root}/lcx-pwa/.env.local}"
smoke_env_file="${LCX_SMOKE_ENV_FILE:-${workspace_root}/.env.development}"

base_url="${LCX_PLATFORM_BASE_URL:-$(read_dotenv_value "$smoke_env_file" "LCX_PLATFORM_BASE_URL")}"
token="${LCX_PLATFORM_BEARER_TOKEN:-$(read_dotenv_value "$smoke_env_file" "LCX_PLATFORM_BEARER_TOKEN")}"

if [[ -z "$base_url" ]]; then
  base_url="$(read_dotenv_value "$platform_env_file" "PUBLIC_BASE_URL")"
  if [[ -n "$base_url" ]]; then
    echo "INFO derived LCX_PLATFORM_BASE_URL from ${platform_env_file}"
  fi
fi

if [[ -z "$base_url" ]]; then
  echo "ERROR: LCX_PLATFORM_BASE_URL is required or PUBLIC_BASE_URL must exist in LCX_PLATFORM_ENV_FILE." >&2
  usage
  exit 1
fi

require_cmd curl

base_url="${base_url%/}"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

if [[ -z "$token" ]]; then
  supabase_url="${NEXT_PUBLIC_SUPABASE_URL:-}"
  supabase_anon_key="${NEXT_PUBLIC_SUPABASE_ANON_KEY:-}"
  auth_email="${LCX_AUTH_EMAIL:-$(read_dotenv_value "$smoke_env_file" "LCX_AUTH_EMAIL")}"
  auth_password="${LCX_AUTH_PASSWORD:-$(read_dotenv_value "$smoke_env_file" "LCX_AUTH_PASSWORD")}"

  if [[ -z "$auth_email" ]]; then
    auth_email="${E2E_EMAIL:-$(read_dotenv_value "$smoke_env_file" "E2E_EMAIL")}"
  fi

  if [[ -z "$auth_password" ]]; then
    auth_password="${E2E_PASSWORD:-$(read_dotenv_value "$smoke_env_file" "E2E_PASSWORD")}"
  fi

  if [[ -z "$supabase_url" ]]; then
    supabase_url="$(read_dotenv_value "$smoke_env_file" "NEXT_PUBLIC_SUPABASE_URL")"
  fi

  if [[ -z "$supabase_url" ]]; then
    supabase_url="$(read_dotenv_value "$pwa_env_file" "NEXT_PUBLIC_SUPABASE_URL")"
  fi

  if [[ -z "$supabase_anon_key" ]]; then
    supabase_anon_key="$(read_dotenv_value "$smoke_env_file" "NEXT_PUBLIC_SUPABASE_ANON_KEY")"
  fi

  if [[ -z "$supabase_anon_key" ]]; then
    supabase_anon_key="$(read_dotenv_value "$pwa_env_file" "NEXT_PUBLIC_SUPABASE_ANON_KEY")"
  fi

  if [[ -n "$auth_email" && -n "$auth_password" && -n "$supabase_url" && -n "$supabase_anon_key" ]]; then
    require_cmd node
    auth_body="$(
      LCX_AUTH_EMAIL="$auth_email" LCX_AUTH_PASSWORD="$auth_password" node -e \
        'process.stdout.write(JSON.stringify({ email: process.env.LCX_AUTH_EMAIL, password: process.env.LCX_AUTH_PASSWORD }))'
    )"
    auth_output="$tmp_dir/supabase-auth.json"
    auth_code="$(
      curl -sS -o "$auth_output" -w '%{http_code}' \
        -X POST \
        -H "accept: application/json" \
        -H "apikey: ${supabase_anon_key}" \
        -H "content-type: application/json" \
        --data "$auth_body" \
        "${supabase_url%/}/auth/v1/token?grant_type=password"
    )"

    if [[ "$auth_code" =~ ^2 ]]; then
      token="$(
        node -e '
          let input = "";
          process.stdin.on("data", (chunk) => { input += chunk; });
          process.stdin.on("end", () => {
            try {
              const parsed = JSON.parse(input);
              if (typeof parsed.access_token === "string") {
                process.stdout.write(parsed.access_token);
              }
            } catch {}
          });
        ' < "$auth_output"
      )"
      if [[ -n "$token" ]]; then
        echo "INFO derived LCX_PLATFORM_BEARER_TOKEN from Supabase Auth password sign-in"
      fi
    else
      echo "WARN Supabase Auth sign-in failed (${auth_code}); set LCX_PLATFORM_BEARER_TOKEN explicitly." >&2
    fi
  fi
fi

if [[ -z "$token" ]]; then
  echo "ERROR: LCX_PLATFORM_BEARER_TOKEN is required for protected loyalty endpoints." >&2
  echo "       Or set LCX_AUTH_EMAIL/LCX_AUTH_PASSWORD so the script can obtain a Supabase Auth access token." >&2
  usage
  exit 1
fi

request() {
  local label="$1"
  local method="$2"
  local path="$3"
  local output="$4"
  local body="${5:-}"
  local code

  if [[ -n "$body" ]]; then
    code="$(
      curl -sS -o "$output" -w '%{http_code}' \
        -X "$method" \
        -H "accept: application/json" \
        -H "content-type: application/json" \
        -H "authorization: Bearer ${token}" \
        --data "$body" \
        "${base_url}${path}"
    )"
  else
    code="$(
      curl -sS -o "$output" -w '%{http_code}' \
        -X "$method" \
        -H "accept: application/json" \
        -H "authorization: Bearer ${token}" \
        "${base_url}${path}"
    )"
  fi

  if [[ "$code" =~ ^2 ]]; then
    echo "OK   $label ($code)"
  else
    echo "FAIL $label ($code)" >&2
    return 1
  fi
}

public_request() {
  local label="$1"
  local path="$2"
  local output="$3"
  local code

  code="$(curl -sS -o "$output" -w '%{http_code}' -H "accept: application/json" "${base_url}${path}")"
  if [[ "$code" =~ ^2 ]]; then
    echo "OK   $label ($code)"
  else
    echo "FAIL $label ($code)" >&2
    return 1
  fi
}

echo "== LCX platform loyalty smoke =="
echo "Base URL: ${base_url}"

public_request "health readiness" "/health/readiness" "$tmp_dir/readiness.json"
request "loyalty rewards" "GET" "/v1/loyalty/rewards" "$tmp_dir/rewards.json"
request "loyalty account list" "GET" "/v1/loyalty/accounts?limit=1" "$tmp_dir/accounts.json"

account_id="${LCX_LOYALTY_SMOKE_ACCOUNT_ID:-}"
if [[ -n "$account_id" ]]; then
  request "loyalty account detail" "GET" "/v1/loyalty/accounts/${account_id}" "$tmp_dir/account-detail.json"
  request \
    "wallet issue" \
    "POST" \
    "/v1/loyalty/wallet/issue" \
    "$tmp_dir/wallet-issue.json" \
    "{\"account_id\":\"${account_id}\"}"

  if [[ "${LCX_LOYALTY_SMOKE_WALLET_LINKS:-false}" == "true" ]]; then
    if ! command -v jq >/dev/null 2>&1; then
      echo "WARN wallet link check skipped because jq is not installed"
    else
      apple_url="$(jq -r '.data.add_to_apple_wallet_url // empty' "$tmp_dir/wallet-issue.json")"
      google_url="$(jq -r '.data.add_to_google_wallet_url // empty' "$tmp_dir/wallet-issue.json")"

      if [[ -n "$apple_url" ]]; then
        apple_code="$(curl -sS -o /dev/null -w '%{http_code}' "$apple_url" || true)"
        echo "INFO apple wallet link status: ${apple_code}"
      fi

      if [[ -n "$google_url" ]]; then
        google_code="$(curl -sS -o /dev/null -w '%{http_code}' -L "$google_url" || true)"
        echo "INFO google wallet link status: ${google_code}"
      fi
    fi
  fi

  if [[ "${LCX_LOYALTY_SMOKE_MUTATE:-false}" == "true" ]]; then
    provider="${LCX_LOYALTY_SMOKE_PROVIDER:-}"
    if [[ -n "$provider" ]]; then
      body="{\"account_id\":\"${account_id}\",\"provider\":\"${provider}\"}"
    else
      body="{\"account_id\":\"${account_id}\"}"
    fi
    request "wallet resync queue" "POST" "/v1/loyalty/wallet/resync" "$tmp_dir/wallet-resync.json" "$body"
  else
    echo "SKIP wallet resync queue (set LCX_LOYALTY_SMOKE_MUTATE=true to run)"
  fi
else
  echo "SKIP account detail / wallet checks (set LCX_LOYALTY_SMOKE_ACCOUNT_ID)"
fi

echo "Smoke complete."
