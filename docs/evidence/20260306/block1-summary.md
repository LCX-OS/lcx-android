# QA Physical Device - Block 1 Summary (2026-03-06)

## Scope
- Login invalido y valido.
- Crear ticket.
- Actualizar estado.
- Actualizar pago.
- Intento de cobro con stub.

## Device/App Context
- Device: Pixel 9 (`49281FDAQ0011J`)
- Package: `com.cleanx.lcx.dev`
- Local API: `http://127.0.0.1:3000` (via `adb reverse`)
- Local Supabase: `http://127.0.0.1:54321` (via `adb reverse`)

## Result by case
- Login invalido: **PASS**
  - `POST /auth/v1/token?grant_type=password -> 400` (14:34:09)
- Login valido: **PASS**
  - `POST /auth/v1/token?grant_type=password -> 200` (14:34:12)
- Crear ticket: **PASS**
  - Correlation: `f18b76bc-b6a0-46be-abdc-72e51d2d21b7`
  - `POST /api/tickets -> 200`
  - Ticket ID: `761669ac-77b0-487a-ac92-b13da6017bf1`
- Status updates (processing, ready): **PASS**
  - Correlations:
    - `73bbf232-e568-4687-9abc-1866e18d2379` (`PATCH .../status -> 200`, `processing`)
    - `8702c250-55b5-49b3-b643-35d70c42cf06` (`PATCH .../status -> 200`, `ready`)
- Payment update API (cash): **PASS**
  - Correlation: `6b122908-26bc-44df-8b23-5662ed047340`
  - `PATCH /api/tickets/{id}/payment -> 200`
  - Ticket persisted with `payment_status=paid`, `payment_method=cash`
- Card charge attempt (stub zettle): **FAILED (expected simulated failure)**
  - `Starting charge ...`
  - `[STUB-ZETTLE] result=Failed(errorCode=STUB_ERROR, ...)`

## Correlation proof (mobile -> API -> audit_logs)
- Verified in `audit_logs`:
  - `ticket_create` -> `f18b76bc-b6a0-46be-abdc-72e51d2d21b7`
  - `status_update` -> `73bbf232-e568-4687-9abc-1866e18d2379`
  - `status_update` -> `8702c250-55b5-49b3-b643-35d70c42cf06`
  - `payment_update` -> `6b122908-26bc-44df-8b23-5662ed047340`

## Notes / risk
- During this block the payment path persisted as `cash` before the stub card failure.
- This run is **not** yet a valid proof for "cancel/no mark paid" in card flow.

## Evidence files
- Device logs: `docs/evidence/20260306/device-live.log`
