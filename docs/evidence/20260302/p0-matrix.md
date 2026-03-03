# P0 Manual Execution Matrix

**App:** LCX Android (La Clinica del Vestido)
**Date:** 2026-03-02
**Build variant:** `stagingDebug` (recommended for testing)
**Tester:** _______________
**Device:** _______________
**OS Version:** _______________

---

## Test Matrix

| # | Case | Preconditions | Steps | Expected | Status | Evidence | Residual Risk |
|---|------|--------------|-------|----------|--------|----------|---------------|
| 1 | Login valido | 1. App installed and launched. 2. Valid Supabase credentials available. 3. Network connection active. | 1. Launch app; verify `LoginScreen` appears with "LCX" branding and "Iniciar sesion" heading. 2. Enter valid email in "Correo electronico" field. 3. Enter valid password in "Contrasena" field. 4. Tap "Ingresar" button. | 1. Button text changes to "Ingresando..." and fields become disabled (`LoginPhase.Loading`). 2. On success, `LoginPhase.Success` is emitted and `onAuthenticated()` callback fires. 3. Token is persisted via `SessionManager.saveAccessToken()`. 4. Navigation proceeds to `TicketList` screen (via `LcxNavHost`). | PENDING | device-smoke.log | Token expiry not tested; `AuthInterceptor` attaches token but refresh flow not implemented yet. |
| 2 | Login invalido | 1. App installed and launched. 2. Network connection active. | 1. Launch app; `LoginScreen` appears. 2. Enter valid email. 3. Enter incorrect password. 4. Tap "Ingresar". | 1. Button shows "Ingresando..." briefly. 2. `AuthResult.Error` returned with message parsed from `AuthErrorResponse` (field `errorDescription`, `message`, or `error`). 3. Fallback message: "Credenciales invalidas." 4. Error displayed in password field via `LcxTextField(error = state.error)`. 5. Phase returns to `LoginPhase.Idle`; fields re-enabled. 6. **No crash.** | PENDING | device-smoke.log | If server returns unexpected JSON shape, the catch block returns a generic message -- verify no crash on malformed response. |
| 3 | Login -- campos vacios | 1. App launched on login screen. | 1. Leave email blank, tap "Ingresar". 2. Fill email, leave password blank, tap "Ingresar". | 1. Step 1: Error "El correo es obligatorio." shown. 2. Step 2: Error "La contrasena es obligatoria." shown. 3. No network call made (client-side validation in `LoginViewModel.signIn()`). | PENDING | - | - |
| 4 | Crear ticket -- validacion + exito | 1. Logged in (token in `DataStoreSessionStore`). 2. Network active. 3. Opening checklist completed for current store day. | 1. Navigate to `CreateTicket` screen. 2. Fill "Nombre del cliente", "Telefono", "Servicio" (required fields per `CreateTicketViewModel.submit()` validation). 3. Optionally fill "Peso" and "Monto total". 4. Tap submit. | 1. `isSubmitting = true` shown (loading state). 2. `TicketRepository.createTickets(source="encargo")` calls `POST /api/tickets`. 3. On `ApiResult.Success`, `createdTicket` is set in UI state. 4. Ticket `id`, `ticketNumber`, and `dailyFolio` visible. 5. Model fields populated: `customerName`, `customerPhone`, `serviceType`, `service`, etc. | PENDING | - | Validation does not enforce phone format or amount minimum. Weight accepts any double. |
| 5 | Crear ticket -- validacion campos obligatorios | 1. Logged in. | 1. Navigate to CreateTicket. 2. Leave "Nombre del cliente" blank, tap submit. 3. Fill name, leave "Telefono" blank, tap submit. 4. Fill phone, leave "Servicio" blank, tap submit. 5. Fill service, enter "abc" in Peso field, tap submit. | 1. Step 2: "El nombre del cliente es obligatorio." 2. Step 4: "El telefono del cliente es obligatorio." 3. Step 4b: "El servicio es obligatorio." 4. Step 5: "El peso debe ser un numero valido." 5. No API call on validation failure. | PENDING | - | - |
| 6 | Cobro success (card via Zettle) | 1. Ticket created with `totalAmount > 0`. 2. Zettle SDK initialized (or `StubPaymentManager` in staging). 3. Card reader paired (or stub scenario = `AlwaysSuccess`). | 1. From ticket detail or transaction flow, initiate charge. 2. `ChargeViewModel.startCharge()` calls `PaymentRepository.charge(ticketId, amount)`. 3. `PaymentManager.requestPayment()` launches Zettle reader UI. 4. Complete card payment on reader. | 1. `ChargePhase.Processing` shown while reader active. 2. `PaymentResult.Success` returned with `transactionId`. 3. `PaymentRepository` calls `TicketRepository.updatePayment(paymentStatus=PAID, paymentMethod=CARD, paidAmount)`. 4. Backend PATCH to `PATCH /api/tickets/{id}/payment` succeeds. 5. `ChargePhase.Success` with `transactionId` displayed. 6. Ticket `paymentStatus` = `PAID`. | PENDING | - | `StubPaymentManager` default success rate is 80%; set `scenario = AlwaysSuccess` for deterministic test. Amount sent as `Double` (e.g. 150.00 MXN). |
| 7 | Cobro cancel (NO paid) | 1. Ticket created with `totalAmount > 0`. 2. Card reader available. | 1. Initiate charge. 2. On Zettle reader screen, dismiss/cancel the payment. | 1. `PaymentResult.Cancelled` returned. 2. `ChargeResult.Cancelled(reference=ticketId)` propagated. 3. `ChargePhase.Cancelled` shown in UI. 4. Ticket `paymentStatus` remains `PENDING` (no PATCH call made). 5. Retry button available; cancel returns to previous screen. | PENDING | - | Verify no partial state left in Room persistence if cancel happens mid-flow in `TransactionOrchestrator` (phase persisted as `PAYMENT_CANCELLED`). |
| 8 | Cobro fail -- lector de tarjetas | 1. Ticket created. 2. Card reader disconnected or card declined. | 1. Initiate charge. 2. Simulate reader failure (e.g., disconnect reader, or in stub set `scenario = AlwaysFailed`). | 1. `PaymentResult.Failed(errorCode, message)` returned. 2. `ChargeResult.ReaderFailed` propagated. 3. `ChargePhase.Failed` shown with `errorMessage`. 4. Error mapped by `ZettleErrorMapper`: e.g. `CARD_DECLINED` -> "Tarjeta rechazada", `READER_DISCONNECTED` -> "Lector desconectado". 5. Retry button available. 6. No charge was made to the card. | PENDING | - | Stub returns generic "Error simulado del lector de tarjetas." Real SDK errors need field validation with actual hardware. |
| 9 | Cobro success + fallo API (retry sync only) | 1. Payment processed successfully via card reader. 2. Network killed or backend returns error during PATCH. | 1. Initiate charge flow. 2. Allow card payment to succeed. 3. Kill network (airplane mode) or mock backend error before PATCH completes. | 1. `PaymentResult.Success` received (card charged). 2. `TicketRepository.updatePayment()` fails -> `ApiResult.Error`. 3. `ChargeResult.PaymentSucceededButApiCallFailed` returned. 4. `ChargePhase.PaymentSucceededApiCallFailed` shown -- **CRITICAL state**. 5. UI displays: "Cobro realizado - Error de sincronizacion" + `transactionId`. 6. `TransactionState.PaymentSucceededApiFailed` persisted to Room. 7. Retry calls `PaymentRepository.syncPaymentToBackend()` (only API call, no re-charge). 8. On app kill+restart, `PendingTransactionDialog` shows with `isCritical = true` and red warning banner. | PENDING | - | **HIGH RISK:** Data loss if Room DB is cleared before sync completes. No background retry worker exists -- retry is manual only. Operator must be trained on this state. |
| 10 | Impresion success (Brother) | 1. Ticket paid. 2. Brother printer connected (WiFi or Bluetooth). 3. `BLUETOOTH_CONNECT` permission granted (for BT printers). 4. Brother SDK AAR available (`BrotherSdkBridge.loadOrNull()` returns non-null). | 1. From print screen, tap discover printers. 2. `PrintRepository.discoverPrinters()` scans WiFi and Bluetooth. 3. Select a discovered printer. 4. `PrintRepository.connectToSelected()` opens channel. 5. `PrintRepository.printWithRetry(label)` sends bitmap to printer. | 1. `PrintPhase.DISCOVERING` -> `SELECTING` (printers listed). 2. On selection: `CONNECTING` -> `PRINTING`. 3. `LabelRenderer.render(label, STANDARD)` generates 696x342px bitmap (DK-1209 62x29mm). 4. Label contains: ticketNumber, dailyFolio, customerName, serviceType, date. 5. `PrintResult.Success` -> `PrintPhase.SUCCESS`. 6. Printer auto-cut enabled. 7. Selected printer saved to `PrinterPreferences` for auto-reconnect. | PENDING | - | Without physical Brother AAR, `BrotherSdkBridge.loadOrNull()` returns null and discovery returns empty list. Test requires real hardware or `StubPrinterManager`. |
| 11 | Impresion fail + retry | 1. Ticket paid. 2. Printer powered off, out of paper, or disconnected. | 1. Attempt print. 2. `PrinterManager.print(label)` returns `PrintResult.Error`. | 1. `PrintRepository.printWithRetry()` retries up to `MAX_RETRY_ATTEMPTS = 3` times. 2. After 3 failures, `PrintResult.Error(code, message)` returned. 3. `PrintPhase.ERROR` shown with error message. 4. Error mapped by `BrotherErrorMapper.mapSdkV4Error()`: e.g. `COVER_OPEN` -> "Tapa de impresora abierta", `NO_PAPER` -> "Sin papel/etiquetas", `COMMUNICATION_ERROR` -> "Error de comunicacion". 5. Retry button available (`PrintViewModel.retry()`). 6. Skip button available. 7. Print failure does NOT affect ticket or payment state. | PENDING | - | No auto-reconnect on `COMMUNICATION_ERROR`. Operator must manually retry. `MAX_RETRY_ATTEMPTS` is not configurable at runtime. |
| 12 | Impresion skip | 1. Ticket paid. 2. Print screen shown (either after success or after error). | 1. Tap "Skip" / "Omitir" button. 2. `PrintViewModel.skip()` called. | 1. `PrintRepository.disconnect()` called. 2. `PrintPhase.IDLE` with `finished = true`. 3. In `TransactionOrchestrator`, if print was skipped from `PrintFailed` state, `skipPrint()` calls `finalize(ticket)`. 4. `TransactionState.Completed` emitted. 5. Ticket remains valid, payment confirmed. 6. No label printed (acceptable per business rules -- print is non-blocking). | PENDING | - | No audit trail of skip decision. Future: log skip event for manager review. |
| 13 | Resume tras kill app (pre-payment) | 1. Transaction started. 2. Ticket created but payment not yet completed. 3. Force-kill app (Recent Apps -> swipe away). | 1. Force-kill app. 2. Reopen app. 3. `TransactionViewModel.checkForPendingTransaction()` calls `TransactionPersistence.loadActiveTransaction()`. 4. `PendingTransactionDialog` shown. | 1. Dialog title: "Se encontro una transaccion pendiente". 2. Shows ticket info: `ticketNumber - customerName - $amount`. 3. Shows "Ultimo paso: [phaseLabel]" (e.g., "Ticket creado"). 4. "Continuar" button resumes from saved phase. 5. "Cancelar" button marks transaction as `CANCELLED` in Room. 6. `TransactionOrchestrator.resumeTransaction()` inspects `SavedTransaction.phase` and resumes correctly. 7. Phases `TICKET_CREATED`, `CHARGING_PAYMENT`, `PAYMENT_FAILED`, `PAYMENT_CANCELLED` resume at `executeChargePayment()`. | PENDING | - | If Room DB corrupted, `loadActiveTransaction()` returns null and no dialog shown -- transaction silently lost. |
| 14 | Resume tras kill app (post-payment, critical state) | 1. Card charged but API sync failed (`PAYMENT_SUCCEEDED_API_FAILED`). 2. State persisted to Room. 3. Force-kill app. | 1. Reopen app. 2. `PendingTransactionDialog` appears. | 1. `isCritical = true` -> red warning banner: "ATENCION: Se registro un cobro con tarjeta que no se pudo sincronizar con el servidor. Se recomienda continuar para completar la sincronizacion." 2. "Cancelar" button is **hidden** (critical state cannot be dismissed). 3. Only "Continuar" available (red-colored button). 4. Resume calls `retryApiSync()` -- only PATCH, never re-charges card. 5. `CorrelationContext` restored for tracing. | PENDING | - | **HIGH RISK:** If user clears app data before resuming, payment record in Room is lost. Money was charged but backend never updated. Needs server-side reconciliation. |
| 15 | Resume tras kill app (post-payment success, pre-print) | 1. Payment charged and API synced (`PAYMENT_CHARGED`). 2. Print not started or failed. 3. Force-kill app. | 1. Reopen app. 2. Dialog shown. 3. Tap "Continuar". | 1. `resumeTransaction()` detects phase `PAYMENT_CHARGED` / `PRINTING_LABEL` / `PRINT_FAILED`. 2. Resumes at `executePrintLabel(ticket)`. 3. Never re-charges payment. 4. If printer unavailable, user can skip print and finalize. | PENDING | - | - |
| 16 | Opening checklist 409 -- mensaje claro | 1. Logged in. 2. Opening checklist NOT completed for today, OR already submitted (409 scenario). | 1. Attempt to create a ticket (via `CreateTicketViewModel.submit()` or `TransactionOrchestrator.executeCreateTicket()`). 2. Server returns HTTP 409 with body: `{"error":"Opening checklist must be completed","code":"OPENING_CHECKLIST_BLOCKING_OPERATION"}`. | 1. `TicketRepository.createTickets()` calls `response.parseError()`. 2. `ApiResult.Error(code="OPENING_CHECKLIST_BLOCKING_OPERATION", httpStatus=409)` returned. 3. `ErrorMessages.forCode("OPENING_CHECKLIST_BLOCKING_OPERATION", ...)` maps to: **"Debes completar el checklist de apertura antes de crear tickets."** 4. Error displayed in UI. 5. **No crash**, no unhandled exception. 6. If in `TransactionOrchestrator`, `TransactionState.TicketCreationFailed(message=..., code="OPENING_CHECKLIST_BLOCKING_OPERATION")` emitted. 7. Retry available but will fail again until checklist completed. | PENDING | - | 409 is also returned for `TICKET_NUMBER_CONFLICT` -> "Conflicto al generar numero de ticket. Intenta de nuevo." Verify both 409 codes produce correct messages. |
| 17 | Ticket number conflict 409 | 1. Logged in. 2. Server returns 409 with `code="TICKET_NUMBER_CONFLICT"`. | 1. Create ticket. 2. Server returns 409 with conflict body. | 1. `ErrorMessages.forCode("TICKET_NUMBER_CONFLICT", ...)` maps to: "Conflicto al generar numero de ticket. Intenta de nuevo." 2. Error shown. 3. Retry should succeed (server generates new number). 4. No crash. | PENDING | - | Race condition if multiple devices create tickets simultaneously. |
| 18 | Transaction orchestrator -- full E2E happy path | 1. Logged in. 2. Checklist done. 3. Printer connected. 4. Card reader available. | 1. Start transaction via `TransactionViewModel.start(draft)`. 2. Observe state flow through all phases. | 1. `Idle` -> `CreatingTicket` -> `TicketCreated` (auto-advance) -> `ChargingPayment` -> `PaymentCharged` (auto-advance) -> `PrintingLabel` -> `LabelPrinted` (auto-advance) -> `Completed`. 2. Step progress: 1/4 -> 2/4 -> 3/4 -> 4/4. 3. Room persistence updated at each transition. 4. `correlationId` set in `CorrelationContext` for all HTTP requests. 5. Final ticket has `paymentStatus=PAID`, label printed. | PENDING | - | Full E2E requires Zettle + Brother hardware or stubs configured. |
| 19 | Double-submit prevention | 1. Transaction in progress. | 1. Tap submit/start again while `running = true`. | 1. `TransactionOrchestrator.startTransaction()` returns immediately (no-op). 2. Log: "startTransaction ignored -- already running". 3. `Mutex` prevents concurrent execution. 4. No duplicate ticket or double charge. | PENDING | - | - |
| 20 | Network loss during ticket creation | 1. Logged in. 2. Network off (airplane mode). | 1. Attempt to create ticket. | 1. `api.createTickets()` throws exception (no network). 2. `TicketRepository` catches exception -> `ApiResult.Error(message = e.message ?: "Error de conexion.", httpStatus = 0)`. 3. Error shown in UI. 4. No crash. 5. Retry available. | PENDING | - | No offline queue for ticket creation. Tickets can only be created online. |
| 21 | Session expiry mid-flow | 1. Token expired or invalidated server-side. | 1. Attempt any API call. 2. Server returns 401 with `code="NOT_AUTHENTICATED"`. | 1. `ErrorMessages.forCode("NOT_AUTHENTICATED", ...)` maps to: "Tu sesion ha expirado. Inicia sesion de nuevo." 2. Error shown. 3. No crash. 4. User should be redirected to login (verify navigation). | PENDING | - | `AuthInterceptor` attaches token but there is no token refresh mechanism. Manual re-login required. |

---

## Legend

| Status | Meaning |
|--------|---------|
| **PENDING** | Test not yet executed. |
| **PASS** | Test executed, expected behavior confirmed. |
| **FAIL** | Test executed, behavior deviates from expected. Attach defect link. |
| **BLOCKED** | Cannot execute due to environment, dependency, or configuration issue. Note blocker in Evidence column. |

---

## Instructions for QA Engineer

### Setup

1. **Build variant:** Use `stagingDebug` for stub-based tests (StubPaymentManager, StubPrinterManager). Use `devDebug` or `prodDebug` for real hardware tests.
2. **Device:** Physical Android device with Bluetooth and WiFi. Minimum API 26.
3. **Peripherals:**
   - **Zettle card reader:** Required for cases 6-9 with real SDK. Use `StubPaymentManager` with `scenario = AlwaysSuccess` / `AlwaysCancelled` / `AlwaysFailed` for staging.
   - **Brother printer (QL-820NWB recommended):** Required for cases 10-12. Without `BrotherPrintLibrary.aar`, discovery returns empty. Use `StubPrinterManager` for staging.
4. **Backend:** Ensure staging API is running and accessible. Verify opening checklist state for the current date.
5. **Credentials:** Obtain valid Supabase credentials for the staging environment.

### Execution Procedure

1. Execute tests **in order** (1 through 21). Cases build on prior state (e.g., case 6 requires a ticket from case 4).
2. For each test:
   - Verify all preconditions are met before starting.
   - Follow steps exactly as written.
   - Compare actual behavior against the Expected column.
   - Mark Status as PASS, FAIL, or BLOCKED.
   - If FAIL: note actual behavior in the Evidence column and file a defect.
   - If BLOCKED: note the specific blocker in the Evidence column.
3. Use `adb logcat -s "TXN" "TICKET" "PAYMENT" "PRINT" "BROTHER" "STUB-ZETTLE"` to capture relevant log output.
4. For crash detection, also monitor `adb logcat *:E` for unhandled exceptions.

### How to Fill in Evidence References

The **Evidence** column should contain one or more of:

- **Log file reference:** e.g., `device-smoke.log:L42` -- line number in captured logcat output.
- **Screenshot file:** e.g., `screenshots/case-06-payment-success.png` -- saved in `docs/evidence/20260302/screenshots/`.
- **Screen recording:** e.g., `recordings/case-14-resume-critical.mp4`.
- **Defect link:** e.g., `JIRA-1234` or GitHub issue URL if the test fails.
- **Correlation ID:** from Timber logs (`[correlation-id]` prefix in TXN tag), useful for backend log correlation.
- **Room DB dump:** `adb shell run-as com.cleanx.lcx cat databases/lcx_database` for transaction persistence verification (cases 13-15).

### Key Source Files Reference

| Area | File |
|------|------|
| Login UI | `feature/auth/src/main/java/.../ui/LoginScreen.kt`, `LoginViewModel.kt` |
| Login data | `feature/auth/src/main/java/.../data/AuthRepository.kt`, `AuthApi.kt` |
| Session | `core/src/main/java/.../session/SessionManager.kt`, `DataStoreSessionStore.kt` |
| Ticket creation | `feature/tickets/src/main/java/.../ui/create/CreateTicketViewModel.kt` |
| Ticket API | `core/src/main/java/.../network/TicketApi.kt` |
| Ticket repo | `feature/tickets/src/main/java/.../data/TicketRepository.kt` |
| Error mapping | `feature/tickets/src/main/java/.../data/ErrorMessages.kt` |
| Payment VM | `feature/payments/src/main/java/.../ui/ChargeViewModel.kt` |
| Payment repo | `feature/payments/src/main/java/.../data/PaymentRepository.kt` |
| Payment SDK | `feature/payments/src/main/java/.../data/PaymentManager.kt`, `ZettlePaymentManager.kt`, `StubPaymentManager.kt` |
| Payment errors | `feature/payments/src/main/java/.../data/ZettleErrorMapper.kt` |
| Print VM | `feature/printing/src/main/java/.../ui/PrintViewModel.kt` |
| Print repo | `feature/printing/src/main/java/.../data/PrintRepository.kt` |
| Printer SDK | `feature/printing/src/main/java/.../data/BrotherPrinterManager.kt`, `StubPrinterManager.kt` |
| Printer errors | `feature/printing/src/main/java/.../data/BrotherErrorMapper.kt` |
| Label render | `feature/printing/src/main/java/.../data/LabelRenderer.kt` |
| Orchestrator | `app/src/main/java/.../transaction/TransactionOrchestrator.kt` |
| Transaction state | `app/src/main/java/.../transaction/TransactionState.kt`, `TransactionPhase.kt` |
| Transaction VM | `app/src/main/java/.../transaction/ui/TransactionViewModel.kt` |
| Persistence | `app/src/main/java/.../transaction/data/TransactionPersistence.kt` |
| Recovery dialog | `app/src/main/java/.../transaction/ui/PendingTransactionDialog.kt` |
| Navigation | `core/src/main/java/.../navigation/Screen.kt`, `app/src/.../navigation/LcxNavHost.kt` |
| Model | `core/src/main/java/.../model/Ticket.kt`, `Enums.kt` |
