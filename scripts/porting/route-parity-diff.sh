#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PWA_ROOT="${PWA_ROOT:-/Users/diegolden/Code/LCX/v0-lcx-pwa}"

DATE_TAG="$(date +%Y%m%d)"
NOW_HUMAN="$(date '+%Y-%m-%d %H:%M:%S %Z')"
OUT_DIR="$ANDROID_ROOT/docs/archive/porting/generated"
OUT_FILE="$OUT_DIR/route-parity-diff-$DATE_TAG.md"

mkdir -p "$OUT_DIR"

if [[ ! -d "$PWA_ROOT" ]]; then
  echo "ERROR: PWA root not found at $PWA_ROOT" >&2
  exit 1
fi

OPERATOR_DIR="$PWA_ROOT/app/(authenticated)/operador"
if [[ ! -d "$OPERATOR_DIR" ]]; then
  echo "ERROR: Operator routes dir not found: $OPERATOR_DIR" >&2
  exit 1
fi

map_status() {
  local module="$1"
  case "$module" in
    encargos)
      echo "PARTIAL|TicketList/Create/Detail + Charge/Print existentes; falta shell/tab parity"
      ;;
    dashboard)
      echo "TODO|No dashboard operador equivalente"
      ;;
    ventas)
      echo "TODO|No módulo ventas dedicado"
      ;;
    turnos)
      echo "TODO|No módulo turnos"
      ;;
    caja)
      echo "TODO|Sin flujo caja nativo"
      ;;
    agua)
      echo "TODO|Sin módulo water nativo"
      ;;
    checklist)
      echo "TODO|Sin módulo checklist nativo"
      ;;
    suministros)
      echo "TODO|Sin módulo suministros nativo"
      ;;
    incidentes)
      echo "TODO|Sin módulo incidentes nativo"
      ;;
    vacaciones)
      echo "TODO|Sin módulo vacaciones nativo"
      ;;
    calendario)
      echo "TODO|Sin módulo calendario nativo"
      ;;
    ropa-danada)
      echo "TODO|Sin módulo ropa dañada nativo"
      ;;
    practicas)
      echo "TODO|Sin módulo prácticas nativo"
      ;;
    ayuda)
      echo "TODO|Sin módulo ayuda nativo"
      ;;
    *)
      echo "TODO|Sin mapeo explícito aún"
      ;;
  esac
}

{
  echo "# Route Parity Diff (PWA -> Android)"
  echo
  echo "Generated: $NOW_HUMAN"
  echo "PWA root: '$PWA_ROOT'"
  echo "Android root: '$ANDROID_ROOT'"
  echo
  echo "## 1) Operator Module Parity"
  echo
  echo "| Module | PWA route | Android status | Notes |"
  echo "|---|---|---|---|"

  while IFS= read -r module; do
    [[ -z "$module" ]] && continue
    status_and_note="$(map_status "$module")"
    status="${status_and_note%%|*}"
    note="${status_and_note#*|}"
    echo "| $module | /operador/$module | $status | $note |"
  done < <(find "$OPERATOR_DIR" -mindepth 1 -maxdepth 1 -type d -exec basename {} \; | sort)

  echo
  echo "## 2) Bottom Navigation Parity"
  echo
  echo "PWA mobile bottom nav routes (source: 'components/bottom-navigator.tsx'):"
  echo
  echo "- /operador/dashboard"
  echo "- /operador/ventas"
  echo "- /operador/encargos"
  echo "- /operador/turnos"
  echo "- /operador/caja"
  echo
  echo "Android current route set (source: 'core/navigation/Screen.kt' + 'LcxNavHost'):"
  echo
  echo "- Login"
  echo "- TicketList / CreateTicket / TicketDetail"
  echo "- Charge / Print / Transaction"
  echo "- PaymentDiagnostics (debug)"
  echo
  echo "Parity result: **MISSING bottom-tab shell and module tabs parity**."
  echo
  echo "## 3) Immediate Porting Delta"
  echo
  echo "1. Add native bottom nav scaffold with 5 operator tabs parity."
  echo "2. Port \`agua\` and \`caja\` first (high operational impact)."
  echo "3. Port \`checklist\` (entrada/salida/historial) with operational gating semantics."
  echo "4. Keep current ticket/payment/print flow as one tab (Encargos) during wave-1."
} > "$OUT_FILE"

echo "Generated: $OUT_FILE"
