# Android Port Spec - Checklist Module

Date: 2026-03-03  
Priority: P0

## 1) Source of truth (PWA)
- Routes:
  - `/operador/checklist/entrada`
  - `/operador/checklist/salida`
  - `/operador/checklist/historial`
- Key files:
  - `app/(authenticated)/operador/checklist/layout.tsx`
  - `app/(authenticated)/operador/checklist/entrada/page.tsx`
  - `app/(authenticated)/operador/checklist/salida/page.tsx`
  - `app/(authenticated)/operador/checklist/historial/page.tsx`

## 2) Functional scope to port (Wave-1)
1. Tabs: Entrada / Salida / Historial.
2. Load today's checklist by type.
3. Toggle items (except system-locked items).
4. Complete checklist only when business rules allow.
5. Historial list with status/progress and date filters.

## 3) Cross-module operational gates
Entrada has system requirements linked to:
- Water review (`/operador/agua`)
- Cash opening (`/operador/caja/registrar`)

Android must preserve this gating behavior:
- Show pending/completed system requirement state.
- Prevent completion if requirements are not satisfied.

## 4) Navigation target
- Parent route: `Screen.Checklist`
- Tabs/segments:
  - `ChecklistEntrada`
  - `ChecklistSalida`
  - `ChecklistHistorial`

## 5) State model
- `ChecklistUiState`
  - `loading`, `syncingRequirements`, `savingItemId`, `completing`, `notes`, `error`, `items`, `status`
- `ChecklistHistoryUiState`
  - `loading`, `filter`, `rows`, `stats`, `error`

## 6) Business rules to preserve
- Cannot complete checklist until required items are complete.
- System-linked items are not manually toggleable.
- Completion writes notes and transitions status.
- Historial supports date-window filters.

## 7) Error mapping
- checklist missing -> "No se pudo cargar el checklist del día."
- save item failure -> "No se pudo actualizar la tarea."
- complete failure -> "No se pudo completar el checklist."

## 8) Definition of Done
1. Entrada/Salida/Historial available in Android with tabbed UX.
2. System requirement sync implemented.
3. Completion guard enforced with clear message.
4. Historial filtering and progress display working.
5. Tests for:
   - completion guard,
   - system-item lock,
   - progress computation.

## 9) Out of scope (this wave)
- CSV export from historial.
- Manager-only advanced analytics widgets.
