# Android Parity Matrix Base (PWA -> Native)

Date: 2026-03-03  
Owner: worktree support lane (non-interfering baseline)

Legend:

- `PASS`: parity achieved / usable equivalent in Android
- `PARTIAL`: implemented but incomplete vs PWA behavior
- `TODO`: not started in Android
- `BLOCKED`: depends on missing backend/API/SDK decision

## 1) Navigation and Shell

| Capability | PWA reference | Android reference | Status | Notes |
|---|---|---|---|---|
| Authenticated shell | `components/authenticated-shell.tsx` | `LcxNavHost` + `MainActivity` | PARTIAL | No bottom tab shell parity yet. |
| Bottom navigation (5 tabs) | `components/bottom-navigator.tsx` | N/A | TODO | Required for operator parity. |
| Role-aware section entry | `/operador/*` routes | N/A | TODO | Need operator module landing model. |

## 2) Core Operational Flow

| Feature | PWA route | Android status | Notes |
|---|---|---|---|
| Login/auth | `/login` | PASS | Implemented with session handling. |
| Encargos create | `/operador/encargos/nuevo` | PASS/PARTIAL | Functional create exists; full UI parity pending. |
| Encargos list/detail | `/operador/encargos`, `/operador/encargos/[id]` | PARTIAL | Ticket list/detail exists; filter states differ. |
| Payment charge | `/operador/encargos/*` flow | PARTIAL | Active hardening/debug in progress. |
| Printing labels | `/operador/suministros/etiquetas` + ticket label flow | PARTIAL | Physical print proven; still regression-prone in some runs. |

## 3) Module Porting Targets

| Module | PWA route(s) | Android status | Priority | Gap |
|---|---|---|---|---|
| Dashboard | `/operador/dashboard` | TODO | P0 | Missing operator home with quick actions. |
| Ventas | `/operador/ventas` | TODO | P1 | No dedicated sales UI module. |
| Turnos | `/operador/turnos/*` | TODO | P1 | No shift module in native yet. |
| Caja | `/operador/caja/*` | TODO | P0 | Required for operator daily control. |
| Agua | `/operador/agua` | TODO | P0 | Must port core monitor/history/order workflow. |
| Checklist | `/operador/checklist/*` | TODO | P0 | Operational gating dependency. |
| Suministros | `/operador/suministros/*` | TODO | P1 | Includes inventory/reportes/etiquetas context. |
| Incidentes | `/operador/incidentes/*` | TODO | P1 | Needed for issue reporting parity. |
| Ropa dañada | `/operador/ropa-danada/*` | TODO | P2 | Later wave. |
| Calendario | `/operador/calendario/*` | TODO | P2 | Later wave. |
| Vacaciones | `/operador/vacaciones` | TODO | P2 | Later wave. |
| Ayuda/Prácticas | `/operador/ayuda`, `/operador/practicas/*` | TODO | P3 | Training/support wave. |

## 4) Water Module Contract Notes

Current PWA data path:

- `lib/db/water.ts` (browser Supabase client)
- Reads/writes table `water_levels`
- Uses role/branch scoping helpers

Parity risk:

- Android currently has no `water` feature module and no explicit `water` API route contract.
- Requires immediate decision: direct Supabase from Android vs backend API facade.

## 5) Suggested Definition of Done for Next Port Wave

1. Bottom nav parity with 5 operator tabs in Android.
2. Dashboard + Agua + Caja at least baseline workflows functional.
3. Each new module has:
   - feature package/module,
   - ViewModel + state model,
   - repository contract,
   - error mapping,
   - at least one unit/integration test.
4. Updated matrix from `TODO/PARTIAL` to concrete PASS/FAIL with evidence.
