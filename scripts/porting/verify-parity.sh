#!/usr/bin/env bash
# verify-parity.sh — Reads route-registry.json and outputs boolean parity status.
# Usage: ./scripts/porting/verify-parity.sh [--json] [--section <section>]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
REGISTRY="$ANDROID_ROOT/docs/porting/route-registry.json"

if [[ ! -f "$REGISTRY" ]]; then
  echo "ERROR: route-registry.json not found at $REGISTRY" >&2
  exit 1
fi

# Check for jq
if ! command -v jq &>/dev/null; then
  echo "ERROR: jq is required. Install with: brew install jq" >&2
  exit 1
fi

OUTPUT_JSON=false
FILTER_SECTION=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --json) OUTPUT_JSON=true; shift ;;
    --section) FILTER_SECTION="$2"; shift 2 ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
done

# Build jq filter
JQ_FILTER='.routes'
if [[ -n "$FILTER_SECTION" ]]; then
  JQ_FILTER="${JQ_FILTER} | map(select(.section == \"$FILTER_SECTION\"))"
fi

ROUTES=$(jq -c "$JQ_FILTER" "$REGISTRY")
TOTAL=$(echo "$ROUTES" | jq 'length')
PRESENT=$(echo "$ROUTES" | jq '[.[] | select(.route_present == true)] | length')
DONE=$(echo "$ROUTES" | jq '[.[] | select(.parity_done == true)] | length')
MISSING=$(echo "$ROUTES" | jq '[.[] | select(.route_present == false)] | length')

NOW="$(date '+%Y-%m-%d %H:%M:%S')"

if $OUTPUT_JSON; then
  # JSON output for automation
  jq -n \
    --arg ts "$NOW" \
    --argjson total "$TOTAL" \
    --argjson present "$PRESENT" \
    --argjson done "$DONE" \
    --argjson missing "$MISSING" \
    --argjson routes "$ROUTES" \
    '{
      timestamp: $ts,
      summary: {
        total: $total,
        route_present: $present,
        parity_done: $done,
        missing: $missing
      },
      routes: [
        $routes[] | {
          id,
          pwa_path,
          section,
          ROUTE_PRESENT: .route_present,
          PARITY_DONE: .parity_done
        }
      ]
    }'
else
  # Human-readable table output
  echo "# Parity Verification Report"
  echo "# Generated: $NOW"
  echo "# Registry: $REGISTRY"
  if [[ -n "$FILTER_SECTION" ]]; then
    echo "# Section filter: $FILTER_SECTION"
  fi
  echo ""
  echo "## Summary"
  echo "  Total routes:       $TOTAL"
  echo "  ROUTE_PRESENT=YES:  $PRESENT"
  echo "  PARITY_DONE=YES:    $DONE"
  echo "  Missing:            $MISSING"
  echo ""

  # Section breakdown
  echo "## By Section"
  for section in $(echo "$ROUTES" | jq -r '[.[].section] | unique[]'); do
    sec_total=$(echo "$ROUTES" | jq "[.[] | select(.section == \"$section\")] | length")
    sec_present=$(echo "$ROUTES" | jq "[.[] | select(.section == \"$section\" and .route_present == true)] | length")
    sec_done=$(echo "$ROUTES" | jq "[.[] | select(.section == \"$section\" and .parity_done == true)] | length")
    printf "  %-12s total=%-3d present=%-3d done=%-3d\n" "$section" "$sec_total" "$sec_present" "$sec_done"
  done
  echo ""

  # Per-route detail
  echo "## Route Detail"
  printf "  %-6s %-6s %-12s %s\n" "PRES" "DONE" "SECTION" "PWA_PATH"
  printf "  %-6s %-6s %-12s %s\n" "----" "----" "-------" "--------"
  echo "$ROUTES" | jq -r '.[] | [
    (if .route_present then "YES" else "NO" end),
    (if .parity_done then "YES" else "NO" end),
    .section,
    .pwa_path
  ] | @tsv' | while IFS=$'\t' read -r pres done sec path; do
    printf "  %-6s %-6s %-12s %s\n" "$pres" "$done" "$sec" "$path"
  done
fi
