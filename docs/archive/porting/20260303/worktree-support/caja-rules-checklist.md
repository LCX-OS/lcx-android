# Caja (Cash Register) Module -- Contract Rules & Edge-Case Checklist

**Date**: 2026-03-03
**Agent**: C1 Contract/Rules Agent
**Module**: Caja (Cash Register) -- PWA-to-Android Port
**PWA Source Root**: `/Users/diegolden/Code/LCX/v0-lcx-pwa`
**Android Target**: `/Users/diegolden/Code/LCX/lcx-android`
**Port Status**: MISSING (P0-BLOCKER -- see parity-matrix-20260303.md)

---

## Overview

The Caja module manages daily cash register operations for laundromat operators: opening the register at the start of a shift, recording income/expense movements during the day, and closing the register at end of shift with discrepancy detection. This checklist extracts every rule, constraint, and edge case from the PWA implementation so the Android port can be verified against it.

---

## 1. Movement Types & Rules

**Source**: `types/domain/cash.ts` line 3 -- `MovementType = DbEnum<'cash_movement_type'>`
**Source**: `lib/db/cash-movements.ts` lines 140--190
**Source**: `app/(authenticated)/operador/caja/registrar/components/cash-form-sections.tsx` lines 64--96

The database enum `cash_movement_type` has exactly **four** values. The PWA `constants.ts` exposes only `opening | closing | expense` as `CashRecordType` for the UI form, but the data layer and history page also use `income`.

| MovementType | UI Label | When Valid | Required Data | Optional Data | Metadata Stored |
|---|---|---|---|---|---|
| `opening` | Apertura | Any time; no preceding opening check at form level (server-side: one per day expected) | `amount > 0`, authenticated user with EMPLOYEE role | `denominationBreakdown`, `notes` via `payments` field | `denominations` (DenominationBreakdown) |
| `closing` | Cierre | Only when `canClose = true` (has opening today AND no prior closing today) | `amount > 0`, authenticated user with EMPLOYEE role | `denominationBreakdown`, `totalSalesForDay`, `payments` (notes) | `denominations`, `payments`, `total_sales_for_day`, `expected_closing_amount`, `discrepancy_type` |
| `income` | Ingreso | Any time; no specific guard in PWA form (UI exposes only opening/closing/expense) | `amount > 0`, authenticated user with EMPLOYEE role | `notes` | none required |
| `expense` | Gasto | Any time; notes/description are REQUIRED | `amount > 0`, `notes` non-empty, authenticated user with EMPLOYEE role | none (no denomination breakdown) | none (denomination breakdown intentionally omitted) |

**Key note**: The form in `constants.ts` defines `CashRecordType = "opening" | "closing" | "expense"`. The `income` type exists in the DB schema and is used in history/reporting calculations but is **not directly creatable via the registrar form** in the current PWA implementation. The Android port must decide whether to expose `income` in the UI or keep it form-only for the registrar. History display must handle all four types.

---

## 2. Validation Rules (with PWA Line References)

### V1 -- Expense REQUIRES Non-Empty Notes/Description
**Source**: `app/(authenticated)/operador/caja/registrar/page.tsx` lines 160--167

```typescript
if (recordType === "expense" && !payments.trim()) {
  toast({ title: "Error", description: "Por favor describe el gasto realizado", variant: "destructive" })
  return
}
```

- **Trigger**: User submits form with `recordType === "expense"` and `payments` field is empty or whitespace-only.
- **Check**: `.trim()` is applied -- pure whitespace fails validation.
- **User message**: `"Por favor describe el gasto realizado"`
- **Action**: Form submission is aborted; no network call is made.
- **Android requirement**: Validate before calling API. Show inline field error or toast before submit.

### V2 -- Closing BLOCKED When `canClose = false`
**Source**: `app/(authenticated)/operador/caja/registrar/page.tsx` lines 169--176
**Source**: `lib/db/cash-movements.ts` lines 383--420 (`canCloseCashRegister` function)
**Source**: `app/(authenticated)/operador/caja/registrar/components/cash-form-sections.tsx` line 80 (button `disabled={!canClose}`)

```typescript
// UI: closing button is disabled
disabled={!canClose}

// Submit guard:
if (recordType === "closing" && !canClose) {
  toast({ title: "Error", description: "No se puede cerrar la caja en este momento", variant: "destructive" })
  return
}
```

- **`canClose` is determined by**: `canCloseCashRegister()` which queries `cash_movements` for today.
  - `hasOpening = todaysMovements.some(m => m.type === "opening")`
  - `hasClosure = todaysMovements.some(m => m.type === "closing")`
  - Returns `canClose: false` if `hasClosure` (reason: `"La caja ya fue cerrada hoy"`).
  - Returns `canClose: false` if `!hasOpening` (reason: `"No hay apertura de caja para hoy"`).
  - Returns `canClose: true` only when `hasOpening && !hasClosure`.
- **UI enforcement**: The "Cierre" pill button is visually `disabled` when `!canClose` (line 80 of cash-form-sections.tsx).
- **Submit enforcement**: A second guard at submit time prevents bypass via programmatic calls.
- **User message**: `"No se puede cerrar la caja en este momento"`
- **Android requirement**: Fetch `canClose` on screen load and after each successful submission. Disable the closing option in the type selector AND guard at submit time.

### V3 -- `totalSalesForDay` Cannot Be Negative
**Source**: `app/(authenticated)/operador/caja/registrar/page.tsx` lines 178--185

```typescript
if (recordType === "closing" && parsedTotalSalesForDay !== null && parsedTotalSalesForDay < 0) {
  toast({ title: "Error", description: "Las ventas del día no pueden ser negativas", variant: "destructive" })
  return
}
```

- **Trigger**: Closing submission where `totalSalesForDay` field is non-empty AND parses to a negative number.
- **Note**: An empty `totalSalesForDay` field results in `parsedTotalSalesForDay = null` (line 129: `totalSalesForDay.trim().length > 0 ? Number.parseFloat(totalSalesForDay) || 0 : null`). A null value skips this validation -- the field is optional.
- **Server-side clamp** (defense-in-depth): `lib/db/cash-movements.ts` line 163 applies `Math.max(0, data.totalSalesForDay || 0)` when computing `explicitSales`. Also line 200: `Math.max(0, data.totalSalesForDay || 0)` when writing to metadata.
- **User message**: `"Las ventas del día no pueden ser negativas"`
- **Android requirement**: Validate client-side. Field should use `min="0"` hint on number input. Empty is valid (optional field).

### V4 -- Amount Must Be Positive (> 0)
**Source**: `lib/db/cash-movements.ts` lines 119--122

```typescript
if (data.amount <= 0) {
  return { data: null, error: "Invalid amount" }
}
```

- **Trigger**: Any movement submission where the calculated total (`calculateGrandTotal()`) is `<= 0`.
- **Note**: This check is in the data layer (`recordCashMovement`), not in the UI form. The UI does not display an explicit error message for this case -- it falls through to the generic `catch` block.
- **Android requirement**: Add a client-side guard before calling the API: `if (total <= 0) show error`. Suggested message: `"El monto debe ser mayor a cero"`. Also handle the error return from the API.

### V5 -- User Must Be Authenticated and Have EMPLOYEE Role (Minimum)
**Source**: `lib/db/cash-movements.ts` lines 114--131
**Source**: `lib/db/auth-helpers.ts` lines 7--20 (ROLES hierarchy)

```typescript
// Role check (auth-helpers.ts):
const ROLE_HIERARCHY = { employee: 1, manager: 2, superadmin: 3 }
export function hasMinimumRole(userRole, requiredRole): boolean {
  if (!userRole) return false
  return ROLE_HIERARCHY[userRole] >= ROLE_HIERARCHY[requiredRole]
}

// In recordCashMovement (cash-movements.ts lines 114-131):
if (!userRole || !hasMinimumRole(userRole, ROLES.EMPLOYEE)) {
  return { data: null, error: "Insufficient permissions" }
}
// ...
if (!user) {
  return { data: null, error: "Unauthorized" }
}
```

- **Role hierarchy**: `employee (1) < manager (2) < superadmin (3)`. Any of the three roles may record cash movements.
- **Two separate checks**: (1) role level check, (2) Supabase auth session validity check. Both must pass.
- **Error strings returned**: `"Insufficient permissions"` | `"Unauthorized"` -- these are internal; the spec maps them to Spanish UI messages.
- **Android requirement**: Pass user role from `SessionManager` to repository calls. If role check fails, show `"No tienes permisos para registrar este movimiento."`. If auth check fails, redirect to login.

---

## 3. Business Logic Rules

### B1 -- Auto-Switch to Expense After Successful Opening
**Source**: `app/(authenticated)/operador/caja/registrar/page.tsx` lines 232--234

```typescript
if (recordType === "opening") {
  setRecordType("expense")
}
```

- **Trigger**: A successful `opening` movement submission.
- **Behavior**: The UI automatically changes the selected type from "opening" to "expense".
- **Rationale**: After opening the register, the next common action is recording an expense (e.g., initial cash for change). This reduces friction.
- **Android requirement**: After a successful opening submission, auto-select the expense type in the form. Do NOT auto-switch on closing or expense submissions.

### B2 -- Auto-Select Type Based on Daily Summary
**Source**: `app/(authenticated)/operador/caja/registrar/page.tsx` lines 86--90

```typescript
if (data && movementCount === 0) {
  setRecordType("opening")
} else if (data && openingAmount > 0 && currentAmount <= 0) {
  setRecordType("closing")
}
```

- **Trigger**: On screen load, after `getCashSummary()` and `canCloseCashRegister()` complete.
- **Rule 1**: If no movements have been recorded today (`movementCount === 0`), auto-select `opening`.
- **Rule 2**: If there is an opening amount (`openingAmount > 0`) but the current amount has reached zero or below (`currentAmount <= 0`), auto-select `closing`.
- **Note on `toFiniteNumber`**: Values are pre-processed through `toFiniteNumber()` (page.tsx lines 26--39) to guard against `null`, `undefined`, `NaN`, and `Infinity` -- all fall back to `0`.
- **Android requirement**: After loading the summary, apply the same two conditions to pre-select the appropriate movement type. Use `0` as fallback for all summary fields.

### B3 -- Expected Closing Amount Calculation
**Source**: `lib/db/cash-movements.ts` lines 140--176

Formula:
```
expectedClosingAmount =
  openingAmount
  + explicitSales          (if totalSalesForDay provided, else 0)
  + additionalIncomes      (sum of all "income" movements today)
  - recordedExpenses       (sum of all "expense" movements today)
```

Where:
- `openingAmount` = amount of the most recent `opening` movement today (found by reversing the array and finding first `opening` -- line 158--162).
- `explicitSales` = `Math.max(0, data.totalSalesForDay)` if `Number.isFinite(data.totalSalesForDay)`, else `null`.
- `additionalIncomes` = sum of `amount` for all movements of type `"income"` today (lines 165--167).
- `recordedExpenses` = sum of `amount` for all movements of type `"expense"` today (lines 168--170).

**Fallback (no sales provided)**:
```
fallbackExpected = openingAmount + additionalIncomes - recordedExpenses
```
(Line 172) -- total sales contribution is omitted.

**Client-side preview** (`page.tsx` lines 128--137):
```typescript
const parsedTotalSalesForDay = totalSalesForDay.trim().length > 0
  ? Number.parseFloat(totalSalesForDay) || 0
  : null

const expectedClosingFromSales =
  recordType === "closing" && parsedTotalSalesForDay !== null && todaysSummary
    ? summaryOpeningAmount + parsedTotalSalesForDay + summaryTotalIncome - summaryTotalExpenses
    : null
```

- **Note**: The client preview uses `summaryTotalIncome` (which excludes opening/closing amounts), matching the server-side `additionalIncomes` calculation.
- **Android requirement**: Replicate both the server-side computation (for what gets stored) and the client-side live preview (for what users see while filling out the form).

### B4 -- Difference Field Calculation
**Source**: `lib/db/cash-movements.ts` lines 133--190

For each movement type, the `difference` field stored in the DB is:
- `opening`: `difference = 0` (line 187)
- `closing`: `difference = data.amount - expectedClosingAmount` (line 178)
- `expense`: `difference = -data.amount` (line 189, negative because it reduces balance)
- `income`: `difference = data.amount` (line 189, positive)

The `previous_balance` field is always set from `latestMovement?.amount || 0` (line 135).

**Android requirement**: Compute and send `difference` and `previous_balance` correctly in the API payload, or ensure the server handles this calculation (verify which approach the Android API contract uses).

### B5 -- Discrepancy Type Classification
**Source**: `lib/db/cash-movements.ts` lines 179--185

```typescript
if (difference > 0) {
  discrepancyType = "overage"      // actual > expected: cash surplus
} else if (difference < 0) {
  discrepancyType = "shortage"     // actual < expected: cash deficit
} else {
  discrepancyType = "balanced"     // actual === expected: perfect match
}
```

- **UI labels** (historial/page.tsx lines 369--371):
  - `overage` -> `"Sobrante de caja"`
  - `shortage` -> `"Faltante de caja"`
  - `balanced` -> `"Corte balanceado"`
- **UI colors** (historial/page.tsx lines 361--366):
  - `overage` -> `text-status-success` (green)
  - `shortage` -> `text-status-danger` (red)
  - `balanced` -> `text-muted-foreground` (gray)
- **Android requirement**: Store and display all three states. The discrepancy preview in the form (page.tsx lines 136--137 and cash-form-sections.tsx lines 219--256) shows a live preview using the same labels and styling.

### B6 -- `canClose` Logic
**Source**: `lib/db/cash-movements.ts` lines 383--420

```typescript
const hasOpening = todaysMovements?.some((m) => m.type === "opening")
const hasClosure = todaysMovements?.some((m) => m.type === "closing")

if (hasClosure) return { canClose: false, reason: "La caja ya fue cerrada hoy" }
if (!hasOpening) return { canClose: false, reason: "No hay apertura de caja para hoy" }
return { canClose: true }
```

- **Time boundary**: "Today" is determined as `>= today 00:00:00 local time` (line 391--393). There is no explicit `endOfDay` filter -- it uses an open `gte` without an `lte`.
- **Error fallback**: If the Supabase query fails, returns `{ canClose: false, reason: "Error al verificar movimientos" }` (line 401--403).
- **Android requirement**: The Android API call to check `canClose` should replicate the same date boundary logic. UI must call this on load and refresh it after every successful submission.

---

## 4. Denomination System

**Source**: `app/(authenticated)/operador/caja/registrar/constants.ts` lines 11--27
**Source**: `lib/db/cash-movements.ts` lines 361--378 (`calculateDenominationTotal`)
**Source**: `types/domain/cash.ts` lines 11--24 (`DenominationBreakdown`)

### Bill Denominations (6 -- MXN)

| Field Name | Value (MXN) | UI Label | UI Color |
|---|---|---|---|
| `bills_1000` | 1000 | $1,000 | `bg-purple-100` |
| `bills_500` | 500 | $500 | `bg-status-info/15` |
| `bills_200` | 200 | $200 | `bg-status-success/15` |
| `bills_100` | 100 | $100 | `bg-destructive/15` |
| `bills_50` | 50 | $50 | `bg-pink-100` |
| `bills_20` | 20 | $20 | `bg-status-warning/10` |

### Coin Denominations (6 -- MXN)

| Field Name | Value (MXN) | UI Label | UI Color |
|---|---|---|---|
| `coins_20` | 20 | $20 | `bg-muted` |
| `coins_10` | 10 | $10 | `bg-muted` |
| `coins_5` | 5 | $5 | `bg-muted` |
| `coins_2` | 2 | $2 | `bg-muted` |
| `coins_1` | 1 | $1 | `bg-muted` |
| `coins_50c` | 0.5 | $0.50 | `bg-muted` |

### Total Calculation Formula
**Source**: `lib/db/cash-movements.ts` lines 364--377

```typescript
function calculateDenominationTotal(breakdown: DenominationBreakdown): number {
  return (
    breakdown.bills_1000 * 1000 +
    breakdown.bills_500  * 500  +
    breakdown.bills_200  * 200  +
    breakdown.bills_100  * 100  +
    breakdown.bills_50   * 50   +
    breakdown.bills_20   * 20   +
    breakdown.coins_20   * 20   +
    breakdown.coins_10   * 10   +
    breakdown.coins_5    * 5    +
    breakdown.coins_2    * 2    +
    breakdown.coins_1    * 1    +
    breakdown.coins_50c  * 0.5
  )
}
```

This is also broken into:
- `calculateBillsTotal()` (page.tsx lines 112--115): sum of all bill denominations
- `calculateCoinsTotal()` (page.tsx lines 118--121): sum of all coin denominations
- `calculateGrandTotal()` (page.tsx lines 123--125): bills + coins

### Expense Type -- No Denomination Breakdown
**Source**: `app/(authenticated)/operador/caja/registrar/page.tsx` line 213
**Source**: `app/(authenticated)/operador/caja/registrar/components/cash-form-sections.tsx` lines 99--188

```typescript
denominationBreakdown: recordType !== "expense" ? denominationBreakdown : undefined
```

- For `expense`, no denomination grid is shown; instead a single numeric input captures the total amount.
- The denomination breakdown object is explicitly NOT included in the API payload for expenses.
- **UI behavior for expense amount input** (page.tsx lines 144--148): When expense amount is entered as a number, it is decomposed into `bills_100` (floor division) and `coins_1` (remainder) for internal state, but this breakdown is never submitted.
- **Android requirement**: Show denomination grid only for `opening` and `closing`. For `expense`, show a single amount input field. Do not send `denominationBreakdown` for expenses.

### Default State
**Source**: `app/(authenticated)/operador/caja/registrar/constants.ts` lines 29--34

```typescript
function createEmptyCounts(denominations): DenominationCount {
  return denominations.reduce((counts, denomination) => {
    counts[denomination.value] = 0
    return counts
  }, {})
}
```

All denomination counts start at 0. After a successful submission, the form resets to all zeros.

---

## 5. Summary Calculation

**Source**: `lib/db/cash-movements.ts` lines 245--319 (`getCashSummary`)

### No Movements Case (lines 270--286)
When no movements exist for the queried day, the function falls back to the **last known movement** across all time:
```typescript
const { data: lastMovement } = await getLatestCashMovement()
return {
  data: {
    openingAmount: lastMovement?.amount || 0,
    currentAmount: lastMovement?.amount || 0,
    totalIncome: 0,
    totalExpenses: 0,
    netChange: 0,
    movementCount: 0,
    lastMovement: lastMovement ? new Date(lastMovement.created_at) : null,
  }
}
```

**Critical note for Android**: When `movementCount === 0`, both `openingAmount` and `currentAmount` reflect the last historical balance, not zero. This is used in B2 auto-select logic.

### Movements Exist (lines 288--314)

| Field | Calculation | Source Line |
|---|---|---|
| `openingAmount` | `opening?.amount \|\| 0` -- first `opening` type movement found | 289, 306 |
| `currentAmount` | `closing?.amount \|\| lastMovement?.amount \|\| opening?.amount \|\| 0` -- priority: closing > last movement > opening | 300--302 |
| `totalIncome` | `sum(m.amount for m.type === "income")` | 292--295 |
| `totalExpenses` | `sum(m.amount for m.type === "expense")` | 296--299 |
| `netChange` | `totalIncome - totalExpenses` | 310 |
| `movementCount` | `movements.length` (all movements, not just income/expense) | 311 |
| `lastMovement` | `movements[movements.length - 1]` (last by ascending `created_at`) | 300, 312 |

**`opening` selection**: Uses `.find()` which returns the FIRST match in the array sorted ascending by `created_at`. On days with multiple openings, this is the earliest one.

**`closing` selection**: Also `.find()` -- the first closing in ascending order.

**Android requirement**: Replicate this exact priority chain for `currentAmount`. A day with a closing should show the closing amount, not the last movement's amount.

---

## 6. Error Mappings

**Source**: `app/(authenticated)/operador/caja/registrar/page.tsx` (submit handler and toasts)
**Source**: `lib/db/cash-movements.ts` (error returns)
**Source**: `docs/porting/worktree-support/spec-caja-module-android-20260303.md` section 7

| Condition | Internal Error String | Spanish User Message | Source |
|---|---|---|---|
| Role missing or below EMPLOYEE | `"Insufficient permissions"` | `"No tienes permisos para registrar este movimiento."` | cash-movements.ts:116, spec section 7 |
| No authenticated session | `"Unauthorized"` | `"No tienes permisos para registrar este movimiento."` (or redirect to login) | cash-movements.ts:130, spec section 7 |
| Invalid/malformed payload | Supabase error | `"Los datos de caja son inválidos."` | spec section 7 |
| Network failure | `catch (error)` | Standard session/network message (spec section 7) | cash-movements.ts:236--239 |
| canClose = false at submit | internal guard | `"No se puede cerrar la caja en este momento"` | page.tsx:170--174 |
| totalSalesForDay is negative | client guard | `"Las ventas del día no pueden ser negativas"` | page.tsx:179--184 |
| Expense without notes | client guard | `"Por favor describe el gasto realizado"` | page.tsx:162--166 |
| amount <= 0 | data layer guard | `"El monto debe ser mayor a cero"` (suggested; PWA has no specific UI message) | cash-movements.ts:121 |
| Profile missing at submit | UI guard (`!profile`) | `"No se pudo identificar el usuario"` | page.tsx:152--158 |
| Supabase query error on canClose | internal | `canClose: false` (form disables closing silently) | cash-movements.ts:401 |
| Error loading history | `catch` | `"No se pudo cargar el historial de caja"` with retry action | historial/page.tsx:74--84 |
| CSV export with no data | guard | `"No hay movimientos para exportar"` (toast.warning) | historial/page.tsx:143--145 |
| CSV export failure | `catch` | `"No se pudo exportar el historial"` | historial/page.tsx:178--181 |

---

## 7. Metadata Structure

**Source**: `types/domain/cash.ts` lines 26--32

```typescript
interface CashMovementMetadata {
  denominations?: DenominationBreakdown    // opening and closing only
  payments?: string                        // closing (shift notes), also used for expense description via notes field
  total_sales_for_day?: number             // closing only
  expected_closing_amount?: number         // closing only
  discrepancy_type?: "shortage" | "overage" | "balanced"  // closing only
}
```

### Storage Rules (lib/db/cash-movements.ts lines 192--209)

```typescript
const metadataPayload: CashMovementMetadata = {}
if (data.denominationBreakdown) {
  metadataPayload.denominations = data.denominationBreakdown      // any type with breakdown
}
if (data.payments) {
  metadataPayload.payments = data.payments                        // shift notes string
}
if (data.type === "closing" && Number.isFinite(data.totalSalesForDay)) {
  metadataPayload.total_sales_for_day = Math.max(0, data.totalSalesForDay || 0)
}
if (data.type === "closing" && Number.isFinite(expectedClosingAmount)) {
  metadataPayload.expected_closing_amount = expectedClosingAmount
}
if (data.type === "closing" && discrepancyType) {
  metadataPayload.discrepancy_type = discrepancyType
}

// Only write non-empty metadata; otherwise store null
const metadata = Object.keys(metadataPayload).length > 0 ? metadataPayload as Json : null
```

### Per-Type Metadata Summary

| Field | opening | closing | income | expense |
|---|---|---|---|---|
| `denominations` | YES (if counts entered) | YES (if counts entered) | N/A | NEVER |
| `payments` | Optional (shift notes) | Optional (shift notes) | N/A | NEVER -- description goes in `notes` column instead |
| `total_sales_for_day` | NEVER | Only if `Number.isFinite(totalSalesForDay)` | N/A | NEVER |
| `expected_closing_amount` | NEVER | Only for closing | N/A | NEVER |
| `discrepancy_type` | NEVER | Always on closing | N/A | NEVER |

### Notes vs Payments Field Distinction
**Source**: `app/(authenticated)/operador/caja/registrar/page.tsx` lines 208--215

```typescript
notes: recordType === "expense" ? payments : undefined,
payments: recordType === "closing" && payments ? payments : undefined,
```

- For `expense`: the text goes into the top-level `notes` column (not metadata).
- For `closing`: the text goes into `metadata.payments` AND the top-level `notes` is `undefined`.
- For `opening`: both `notes` and `payments` are omitted from the payload.
- **Historial display** (historial/page.tsx line 387): Labels the notes as `"Pagos realizados:"` for closing movements and `"Notas:"` for all others.

---

## 8. Edge Cases Checklist

Status key: `[ ]` = Not yet covered in Android | `[x]` = Covered

### EC1 -- No Movements Today
- [ ] `getCashSummary` returns `movementCount=0` with `openingAmount` and `currentAmount` set from the **last historical movement**, not from 0.
- [ ] UI shows warning: `"No hay movimientos registrados hoy. Debes realizar la apertura de caja."` (page.tsx line 272).
- [ ] Auto-selects `opening` type (B2 Rule 1).
- [ ] `canClose` returns `false` with reason `"No hay apertura de caja para hoy"`.

### EC2 -- Opening Amount Is Zero (0.00)
- [ ] V4 blocks submission: `amount <= 0` returns error at data layer.
- [ ] UI must show an error before calling the API. An all-zero denomination grid means `calculateGrandTotal() === 0`.
- [ ] B2 Rule 2 (`openingAmount > 0`) will never trigger for a zero opening, so auto-closing suggestion will not occur.

### EC3 -- Multiple Openings on Same Day
- [ ] The PWA has no server-side guard preventing a second `opening` on the same day at the data layer level. `canCloseCashRegister` only checks `hasOpening` (boolean, not count).
- [ ] `getCashSummary` uses `.find()` (first match ascending), so `openingAmount` = amount of the **earliest** opening.
- [ ] `recordCashMovement` for closing uses `.reverse().find()` to get the **latest** opening (line 158--162) for expected amount calculation. This means the server and client summary use different opening references when there are multiple openings.
- [ ] The Android port should enforce single-opening-per-day either at the UI level (disable opening button after first opening) or at the server level.

### EC4 -- Close Without Prior Opening
- [ ] `canCloseCashRegister` returns `{ canClose: false, reason: "No hay apertura de caja para hoy" }`.
- [ ] The closing type selector button is `disabled` in the UI.
- [ ] If a closing is submitted anyway (e.g., via API directly), `expectedClosingAmount` calculation uses `openingAmount = 0` (line 162: `latestOpening?.amount || 0`).

### EC5 -- Register Already Closed Today (Double Close Attempt)
- [ ] `canCloseCashRegister` returns `{ canClose: false, reason: "La caja ya fue cerrada hoy" }`.
- [ ] Closing button is disabled in the UI.
- [ ] B2 Rule 2 may incorrectly trigger (`openingAmount > 0 && currentAmount <= 0`) when `currentAmount` is the closing amount -- this would auto-select closing but the button is disabled. Edge case where UI auto-selects a disabled action.

### EC6 -- Negative Amount Submission
- [ ] V4 (data layer): `amount <= 0` guard blocks negative numbers. Internal error `"Invalid amount"`.
- [ ] UI form uses `type="number" min="0"` hints on denomination inputs (cash-form-sections.tsx lines 116, 150, 179).
- [ ] `calculateGrandTotal()` is always non-negative if all denomination counts are non-negative integers; but if a denomination count is somehow negative (parsing edge case), the total could go negative.
- [ ] `handleBillCountChange` and `handleCoinCountChange` parse via `parseInt(value) || 0` -- a negative input (`-5`) would be parsed as `-5`, not 0. The `min="0"` is a browser hint, not enforced in the JS.

### EC7 -- Very Large Denomination Counts
- [ ] No explicit max count per denomination in the PWA. Counts are stored as integers in the metadata JSON.
- [ ] Supabase stores the metadata as `jsonb`. No overflow protection at the application level.
- [ ] Example: 10,000 bills of $1,000 = $10,000,000. The `amount` column in `cash_movements` must support this (likely a `numeric` or `float8` DB type).
- [ ] Android should validate reasonable limits (e.g., `count <= 9999` per denomination) to prevent accidental large values from keyboard input.

### EC8 -- Concurrent Submissions (Race Condition)
- [ ] The PWA has no optimistic locking or idempotency key on `recordCashMovement`.
- [ ] Two rapid taps on "Guardar" could result in two records being inserted.
- [ ] PWA mitigation: `isLoading` state (`setIsLoading(true)` on submit, reset in `finally`). Button is disabled while loading.
- [ ] No server-side deduplication.
- [ ] Android requirement: Disable the submit button immediately on first tap. Use a loading state. Consider a short debounce or single-shot coroutine job.

### EC9 -- `totalSalesForDay` Provided as Non-Numeric String
- [ ] `Number.parseFloat("abc")` returns `NaN`. `NaN || 0` resolves to `0` in the parser (page.tsx line 129).
- [ ] `Number.isFinite(NaN)` is `false`, so `explicitSales` would be `null` at the data layer (cash-movements.ts line 163).
- [ ] Result: closing falls back to `fallbackExpected` (no sales contribution). No error shown.
- [ ] Android requirement: Use a numeric-only keyboard for this field. Parse and validate before submitting.

### EC10 -- Network Loss During Submission
- [ ] The PWA catches the error in `handleSubmit`'s `catch` block (page.tsx lines 236--238) but shows no user-facing message other than `console.error`.
- [ ] The form data is NOT cleared on error (only cleared on success at lines 228--230).
- [ ] Android requirement: Show an error toast on network failure. Retain form state so the user can retry. Consider offline queue for cash movements (lower priority per spec -- out of scope Wave 1).

### EC11 -- `toFiniteNumber` Guard on Summary Values
- [ ] Summary values pass through `toFiniteNumber()` (page.tsx lines 61--65) which handles `null | undefined | NaN | Infinity` -> `0`.
- [ ] This is critical for B2 auto-select logic and discrepancy preview calculations.
- [ ] Android requirement: Apply equivalent null/NaN guards on all numeric summary fields received from the API.

### EC12 -- History Page: `income` Type Display
- [ ] The `income` type appears in history (historial/page.tsx lines 97--98, 115, 128).
- [ ] The registrar form UI has only 3 type options (`opening | closing | expense`). Income movements can only arrive from DB records created by other means.
- [ ] History must handle all 4 types gracefully with appropriate colors and labels.

---

## 9. Android Port Status Table

| Rule ID | Description | PWA Source File | PWA Lines | Android Status | Notes |
|---|---|---|---|---|---|
| **Validation** | | | | | |
| V1 | Expense requires non-empty notes | registrar/page.tsx | 160--167 | NOT STARTED | Module does not exist yet |
| V2 | Closing blocked when canClose=false (button + submit guard) | page.tsx + cash-form-sections.tsx + cash-movements.ts | 169--176, 80, 383--420 | NOT STARTED | Requires `canClose` API call on load |
| V3 | totalSalesForDay cannot be negative | registrar/page.tsx | 178--185 | NOT STARTED | Optional field; only validate when non-empty |
| V4 | Amount must be > 0 | lib/db/cash-movements.ts | 119--122 | NOT STARTED | Add client-side guard; data layer also guards |
| V5 | EMPLOYEE role minimum required | lib/db/cash-movements.ts + auth-helpers.ts | 114--131, 7--20 | NOT STARTED | Role comes from SessionManager |
| **Business Logic** | | | | | |
| B1 | Auto-switch to expense after opening | registrar/page.tsx | 232--234 | NOT STARTED | Post-submit state update |
| B2 | Auto-select type on load (movementCount=0 -> opening, openingAmount>0 && currentAmount<=0 -> closing) | registrar/page.tsx | 86--90 | NOT STARTED | Requires summary load before form render |
| B3 | expectedClosing = opening + sales + incomes - expenses | lib/db/cash-movements.ts | 140--176 | NOT STARTED | Both server-side storage and client-side preview |
| B4 | difference field per movement type | lib/db/cash-movements.ts | 133--190 | NOT STARTED | Stored in DB; verify if Android API computes this server-side |
| B5 | discrepancyType: overage/shortage/balanced | lib/db/cash-movements.ts | 179--185 | NOT STARTED | Three-state classification |
| B6 | canClose: hasOpening AND NOT hasClosure for today | lib/db/cash-movements.ts | 383--420 | NOT STARTED | Refreshed on load and after each submit |
| **Denomination System** | | | | | |
| D1 | 6 bill denominations: 1000/500/200/100/50/20 MXN | registrar/constants.ts | 11--17 | NOT STARTED | Exact field names must match DenominationBreakdown interface |
| D2 | 6 coin denominations: 20/10/5/2/1/0.50 MXN | registrar/constants.ts | 20--27 | NOT STARTED | `coins_50c` maps to value 0.5 |
| D3 | Total = bills_total + coins_total | cash-movements.ts | 361--378 | NOT STARTED | Floating-point: 0.50 coins need careful arithmetic |
| D4 | Expense: no denomination grid, single amount input | page.tsx + cash-form-sections.tsx | 213, 99--188 | NOT STARTED | Denomination breakdown excluded from expense payload |
| D5 | Form resets all counts to 0 after success | registrar/page.tsx | 228, 139--142 | NOT STARTED | All bill/coin counts back to zero |
| **Summary** | | | | | |
| S1 | openingAmount: first opening movement of day | lib/db/cash-movements.ts | 289, 306 | NOT STARTED | Uses .find() ascending |
| S2 | currentAmount: closing > lastMovement > opening | lib/db/cash-movements.ts | 300--302 | NOT STARTED | Priority chain |
| S3 | totalIncome: sum of income type amounts | lib/db/cash-movements.ts | 292--295 | NOT STARTED | |
| S4 | totalExpenses: sum of expense type amounts | lib/db/cash-movements.ts | 296--299 | NOT STARTED | |
| S5 | netChange: totalIncome - totalExpenses | lib/db/cash-movements.ts | 310 | NOT STARTED | |
| S6 | movementCount: all movements (not just income/expense) | lib/db/cash-movements.ts | 311 | NOT STARTED | Includes openings and closings |
| S7 | No-movements fallback: use last historical movement | lib/db/cash-movements.ts | 270--286 | NOT STARTED | movementCount=0, openingAmount/currentAmount from history |
| **Error Mapping** | | | | | |
| E1 | Forbidden/role -> "No tienes permisos para registrar este movimiento." | cash-movements.ts | 116 | NOT STARTED | Map "Insufficient permissions" internal error |
| E2 | Invalid payload -> "Los datos de caja son inválidos." | spec section 7 | -- | NOT STARTED | Map 400/422 HTTP responses |
| E3 | Network/auth -> standard session messages | spec section 7 | -- | NOT STARTED | Reuse existing session error handling |
| E4 | canClose=false -> "No se puede cerrar la caja en este momento" | page.tsx | 170--174 | NOT STARTED | UI-level; form guard |
| E5 | Negative sales -> "Las ventas del día no pueden ser negativas" | page.tsx | 179--184 | NOT STARTED | Client validation before API call |
| E6 | Expense without notes -> "Por favor describe el gasto realizado" | page.tsx | 162--166 | NOT STARTED | Client validation before API call |
| E7 | History load failure -> "No se pudo cargar el historial de caja" with retry | historial/page.tsx | 74--84 | NOT STARTED | Toast with retry action |
| **Edge Cases** | | | | | |
| EC1 | No movements today: summary shows last historical balance | cash-movements.ts | 270--286 | NOT STARTED | |
| EC2 | Opening amount of 0 blocked by V4 | cash-movements.ts | 119--122 | NOT STARTED | |
| EC3 | Multiple openings: closing uses latest, summary uses earliest | cash-movements.ts | 158--162, 289 | NOT STARTED | Mismatch risk; consider UI guard |
| EC4 | Close without opening: canClose=false | cash-movements.ts | 410--413 | NOT STARTED | |
| EC5 | Double close: canClose=false (already closed today) | cash-movements.ts | 407--409 | NOT STARTED | |
| EC6 | Negative denomination count from keyboard | page.tsx | 98--106 | NOT STARTED | `parseInt(value) || 0` does NOT prevent negatives |
| EC7 | Unreasonably large counts | -- | -- | NOT STARTED | Add max-count validation in Android |
| EC8 | Concurrent submissions | page.tsx | 186--187 | NOT STARTED | Disable button on first tap |
| EC9 | Non-numeric totalSalesForDay | page.tsx | 128--129 | NOT STARTED | Falls back silently; use numeric keyboard |
| EC10 | Network loss during submission | page.tsx | 236--238 | NOT STARTED | Show error; retain form state |
| EC11 | Null/NaN/Infinity in summary values | page.tsx | 61--65 | NOT STARTED | toFiniteNumber guard |
| EC12 | `income` type in history display | historial/page.tsx | 97--98, 115 | NOT STARTED | Not creatable via form; must display correctly in history |

---

## 10. Additional Implementation Notes

### Table Name Note (Checklist Integration)
**Source**: parity-matrix-20260303.md P1 gap entry

The existing `ChecklistRepository.hasCashRegisterToday()` currently queries `cash_registers`, but the PWA uses the table `cash_movements`. This mismatch means the checklist's system-validated "cash register" item (entry-2) will never auto-validate as true. When the Caja module is built, this table reference in `ChecklistRepository` must be corrected to `cash_movements`.

### Audit Logging
**Source**: `lib/db/cash-movements.ts` lines 232--233
**Source**: `lib/db/auth-helpers.ts` lines 148--168

Every successful `recordCashMovement` call writes an audit log entry to the `audit_logs` table via `logAuditAction("cash_movements", movement.id, "create", movementData)`. The Android port must replicate this audit trail.

### Date/Time Boundaries
**Source**: `lib/db/cash-movements.ts` lines 141--144, 249--256, 391--393

- "Today" is always computed in **local time** (device timezone) via `new Date()` with `setHours(0,0,0,0)`.
- For `getCashSummary()`, end-of-day is `setHours(23,59,59,999)` (line 254--255).
- For `canCloseCashRegister()`, only a start-of-day `gte` is used (no end-of-day cap -- line 398).
- Android must use the device's local timezone consistently, not UTC. This is especially important for multi-timezone deployments.

### History Filters Available
**Source**: `app/(authenticated)/operador/caja/historial/page.tsx` lines 31--50

The history page supports these date range presets:
- `"today"`: current day from 00:00:00 local
- `"week"`: last 7 days (`subDays(new Date(), 7)`)
- `"month"`: start of current calendar month (`startOfMonth(new Date())`)
- `"all"` (default): last 30 days (`subDays(new Date(), 30)`)

Search filters: free-text on `notes` and `profiles.full_name` (case-insensitive, client-side).
Type filter: `all | opening | closing | income | expense` (client-side, post-fetch).

### CSV Export Format
**Source**: `app/(authenticated)/operador/caja/historial/page.tsx` lines 141--183

Export columns: `Fecha, Hora, Tipo, Monto, Notas, Usuario`
Filename: `caja_historial_YYYYMMDD.csv`
Out of scope for Wave 1 per spec section 9.

### History Join: Profiles Table
**Source**: `lib/db/cash-movements.ts` lines 39--47

History query includes a PostgREST join: `profiles (full_name)`. The Android API call must request this join to display the operator name. Fallback display: `"Usuario desconocido"` (historial/page.tsx line 381).

---

## References

| File | Purpose |
|---|---|
| `/Users/diegolden/Code/LCX/v0-lcx-pwa/lib/db/cash-movements.ts` | Core business logic, all server-side rules |
| `/Users/diegolden/Code/LCX/v0-lcx-pwa/types/domain/cash.ts` | Domain type definitions |
| `/Users/diegolden/Code/LCX/v0-lcx-pwa/app/(authenticated)/operador/caja/registrar/page.tsx` | Register page, UI validation, submit logic |
| `/Users/diegolden/Code/LCX/v0-lcx-pwa/app/(authenticated)/operador/caja/registrar/constants.ts` | Denomination definitions |
| `/Users/diegolden/Code/LCX/v0-lcx-pwa/app/(authenticated)/operador/caja/registrar/components/cash-form-sections.tsx` | Form UI, denomination grid |
| `/Users/diegolden/Code/LCX/v0-lcx-pwa/app/(authenticated)/operador/caja/historial/page.tsx` | History page, filtering, CSV export |
| `/Users/diegolden/Code/LCX/v0-lcx-pwa/lib/db/auth-helpers.ts` | Role hierarchy, permission checks |
| `/Users/diegolden/Code/LCX/lcx-android/docs/porting/worktree-support/spec-caja-module-android-20260303.md` | Android port specification |
| `/Users/diegolden/Code/LCX/lcx-android/docs/porting/parity-matrix-20260303.md` | Full feature parity analysis |
