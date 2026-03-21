# PWA Feature Inventory (Porting Baseline)

Date: 2026-03-03  
Source repo: `/Users/diegolden/Code/LCX/v0-lcx-pwa`  
Target repo: `/Users/diegolden/Code/LCX/lcx-android`

## 1) Scope
This inventory is a non-invasive baseline to accelerate Android porting waves.
It lists route-level features, priority, and first-pass Android mapping.

## 2) Extraction Method
Commands used:

```bash
cd /Users/diegolden/Code/LCX/v0-lcx-pwa
find app/(authenticated)/operador -mindepth 1 -maxdepth 1 -type d
nl -ba components/bottom-navigator.tsx
nl -ba app/(authenticated)/operador/agua/page.tsx
```

## 3) Operator Modules Found (PWA)
Top-level operator modules:

- `dashboard`
- `ventas`
- `encargos`
- `turnos`
- `caja`
- `agua`
- `checklist`
- `suministros`
- `incidentes`
- `ropa-danada`
- `calendario`
- `vacaciones`
- `practicas`
- `ayuda`

## 4) Bottom Navigation Source of Truth
PWA bottom navigator currently exposes 5 tabs (mobile):

- `Inicio` -> `/operador/dashboard`
- `Ventas` -> `/operador/ventas`
- `Encargos` -> `/operador/encargos`
- `Turnos` -> `/operador/turnos`
- `Caja` -> `/operador/caja`

Source: `components/bottom-navigator.tsx`

## 5) Critical Module Snapshot: Agua
Route: `/operador/agua`

Observed behavior (PWA):

- Current water level monitor with percentage/liters (`TANK_CAPACITY_LITERS = 10000`)
- Persist level record (`recordWaterLevel`)
- Load latest level (`getCurrentWaterLevel`)
- History (`getWaterLevelHistoryWithUser`)
- Water order action (`recordWaterOrder`)
- Threshold alerts (`critical`, `low`, `normal`, `optimal`)

Technical note:

- Current PWA module uses direct Supabase client functions in `lib/db/water.ts`.
- No dedicated `/api/water/*` route found in this pass.
- Android port needs explicit decision:
  - Option A: direct Supabase data path in Android
  - Option B (preferred for contract governance): new API routes for water, then Android consumes REST.

## 6) Proposed Porting Waves (Feature-first)

### Wave 1 (high impact, low ambiguity)
- Bottom navigation shell (5 tabs parity)
- Dashboard operator summary
- Encargos index/new/detail parity baseline
- Caja overview/registrar baseline
- Agua module baseline (current + save + history)

### Wave 2 (operational controls)
- Checklist (`entrada/salida/historial`)
- Suministros (`inventario/reportes/etiquetas`)
- Incidentes (`nuevo/historial`)

### Wave 3 (workforce & support)
- Turnos
- Vacaciones
- Calendario
- Ropa dañada
- Ventas advanced and analytics screens

## 7) Immediate Android Targets for Today
1. Introduce `BottomNav` container and route graph equivalent to PWA 5 tabs.
2. Add new Android feature module scaffold `feature/water`.
3. Implement water domain model/repository/viewmodel/UI for:
   - get current level,
   - save level,
   - history list,
   - threshold state.
4. Keep payment/printing flow integrated but isolated from new navigation graph.

## 8) Acceptance for This Inventory
- Route catalog extracted from real files.
- Bottom nav source captured.
- Agua module dependencies identified.
- Porting waves defined and actionable.
