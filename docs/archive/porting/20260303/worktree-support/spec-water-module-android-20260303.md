# Android Port Spec - Water Module

Date: 2026-03-03  
Priority: P0 (operator daily operation)

## 1) Source of truth (PWA)
- Route: `/operador/agua`
- Files:
  - `app/(authenticated)/operador/agua/page.tsx`
  - `lib/db/water.ts`

## 2) Functional scope to port (Wave-1)
1. View current level (`%` + liters over 10,000L tank).
2. Update and save current level.
3. Show threshold status (`critical`, `low`, `normal`, `optimal`).
4. Show history of recent records.
5. Trigger/record water order with provider selection.

## 3) Android navigation target
- Bottom tab parent: `Operador`
- Route: `Screen.Water` (new)
- Entry points:
  - dashboard quick action "Nivel de agua"
  - bottom tab (if selected for wave layout)

## 4) Domain model (Android)
- `WaterLevelRecord`
  - `id`, `levelPercentage`, `liters`, `tankCapacity`, `status`, `action`, `notes`, `providerId`, `providerName`, `branch`, `recordedBy`, `createdAt`
- `WaterProvider`
  - `id`, `name`, `phone`, `price`, `deliveryTime`, `rating`
- `WaterStatus`
  - `CRITICAL`, `LOW`, `NORMAL`, `OPTIMAL`

## 5) State machine (UI)
- `Loading`
- `Loaded`
  - `currentLevel`, `history`, `selectedProvider`, `isSaving`, `isOrdering`
- `Error`

Core intents:
- `LoadCurrent`
- `LoadHistory`
- `SetLevel`
- `SaveLevel`
- `SelectProvider`
- `OrderWater`

## 6) Data access decision
Current PWA is direct Supabase. For Android, choose one:

- Option A: direct Supabase adapter (fastest for parity).
- Option B: API facade (`/api/water/*`) for stronger contract governance.

Recommended for velocity now: Option A with repository abstraction, so migration to Option B later is low-cost.

## 7) Error handling
Map to operator-safe messages:
- auth/session -> "Tu sesión expiró. Inicia sesión nuevamente."
- network -> "Sin conexión. Reintenta en unos minutos."
- permission/data-scope -> "No tienes permisos para esta sucursal."
- generic -> "No se pudo completar la operación."

## 8) Definition of Done
1. Screen renders current level + liters and status color.
2. Save level persists and refreshes history.
3. Order water persists order event.
4. Handles loading/error states without crashes.
5. Unit tests:
   - liters/percentage conversion
   - status threshold mapping
   - repository success/failure mapping
6. Manual QA evidence in device log with `WATER` tag.

## 9) Out of scope (this wave)
- Push notifications UX polish.
- Advanced analytics/charts.
- Multi-branch supervisor dashboards.
