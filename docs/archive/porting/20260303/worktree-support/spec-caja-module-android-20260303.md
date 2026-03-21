# Android Port Spec - Caja Module

Date: 2026-03-03  
Priority: P0

## 1) Source of truth (PWA)
- Routes:
  - `/operador/caja`
  - `/operador/caja/registrar`
  - `/operador/caja/historial`
- Files:
  - `app/(authenticated)/operador/caja/registrar/page.tsx`
  - `app/(authenticated)/operador/caja/registrar/constants.ts`

## 2) Functional scope to port (Wave-1)
1. Open cash register (`opening`).
2. Record expense/income movement (`expense` / `income`).
3. Close register (`closing`) with totals and discrepancy preview.
4. Denomination breakdown (bills + coins).
5. Daily summary and movement count.

## 3) Android navigation target
- Bottom tab route: `Screen.Cash`
- Nested routes:
  - `CashOverview`
  - `CashRegisterForm`
  - `CashHistory`

## 4) Domain model
- `CashMovement`
  - `type`, `amount`, `notes`, `totalSalesForDay`, `denominationBreakdown`, `createdAt`, `createdBy`
- `CashSummary`
  - `openingAmount`, `currentAmount`, `totalIncome`, `totalExpenses`, `movementCount`, `canClose`

## 5) Validation rules to preserve
- Expense requires note/description.
- Closing blocked when `canClose=false`.
- `totalSalesForDay` cannot be negative.
- Auto-switch suggested action after opening.

## 6) UI states
- `LoadingSummary`
- `Editing` (type + denominations + notes)
- `Submitting`
- `Success`
- `Error`

## 7) Error mapping
- Forbidden/role mismatch -> "No tienes permisos para registrar este movimiento."
- Invalid movement payload -> "Los datos de caja son inválidos."
- Network/auth -> standard session/network messages.

## 8) Definition of Done
1. Opening, expense/income and closing usable end-to-end.
2. Summary refresh after each submit.
3. Denomination totals and discrepancy preview correct.
4. At least one unit test per core rule.
5. Manual QA evidence with `CAJA`/`HTTP` logs.

## 9) Out of scope (this wave)
- Printing/export receipts.
- Multi-cash-register branch admin tooling.
