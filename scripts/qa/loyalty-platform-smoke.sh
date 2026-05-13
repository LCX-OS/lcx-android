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
  LCX_LOYALTY_SMOKE_ACCOUNT_ID=<uuid>          # also checks account detail and wallet issue
  LCX_LOYALTY_SMOKE_WALLET_LINKS=true          # opens issued Apple/Google wallet links, if jq is installed
  LCX_LOYALTY_SMOKE_MUTATE=true                # also queues wallet resync for the account
  LCX_LOYALTY_SMOKE_PROVIDER=google|apple      # provider for optional resync

The script never prints bearer tokens. Wallet link responses are not printed because they contain public tokens.
USAGE
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "ERROR: missing required command: $1" >&2
    exit 1
  fi
}

base_url="${LCX_PLATFORM_BASE_URL:-}"
token="${LCX_PLATFORM_BEARER_TOKEN:-}"

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ -z "$base_url" ]]; then
  echo "ERROR: LCX_PLATFORM_BASE_URL is required." >&2
  usage
  exit 1
fi

if [[ -z "$token" ]]; then
  echo "ERROR: LCX_PLATFORM_BEARER_TOKEN is required for protected loyalty endpoints." >&2
  usage
  exit 1
fi

require_cmd curl

base_url="${base_url%/}"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

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
