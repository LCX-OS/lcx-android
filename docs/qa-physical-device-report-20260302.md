# QA Physical Device Report - 2026-03-02

## 0) Actualización 2026-03-02 (Integración Brother SDK real)
- Estado USB/ADB: **PASS**.
- Preflight obligatorio ejecutado:
  - `adb devices -l` -> `Pixel_9 ... device`
  - `adb reverse tcp:3000 tcp:3000` -> OK
  - `adb reverse tcp:54321 tcp:54321` -> OK
- Instalación física:
  - `./gradlew :app:installDevDebug` -> **PASS** (`Installed on 1 device.`).
- Integración aplicada en Android:
  - Nuevo `BrotherPrinterManager` real (SDK v4) con bridge por reflexión.
  - Wiring Hilt en `PrintModule` para usar real manager cuando `USE_REAL_BROTHER=true`.
  - Flag dev configurable: `LCX_DEV_USE_REAL_BROTHER`.
  - Configuración explícita de etiqueta 209 (`DieCutW62H29` / DK-1209).
  - Solicitud runtime de permiso `BLUETOOTH_CONNECT` en pantallas de impresión/settings.
- Bloqueo actual para impresión física real:
  - Falta AAR de Brother en `feature/printing/libs/BrotherPrintLibrary.aar`.
  - Evidencia en build:
    - `Brother SDK AAR not found ... Real printing will be unavailable for this build.`
- Severidad:
  - **P0 abierto**: sin AAR no hay impresión física real (solo fallback controlado a stub).

## 1) Resumen Ejecutivo
- Fecha/hora de ejecución: 2026-03-02 17:56:25 CST (America/Mexico_City).
- Objetivo: QA P0 real en teléfono Android por USB contra entorno local.
- Resultado general: **PARCIAL EN DISPOSITIVO FÍSICO**.
  - Infra USB/ADB: resuelta (ver sección 0).
  - QA físico end-to-end con impresión real: pendiente por falta del AAR Brother.
- Cobertura alternativa ejecutada: pruebas unitarias/contrato/regresión completas en Android + validación de rutas API y correlación en backend.
- Hallazgos críticos:
  - **P1** corregido: cleanup de transacciones terminales en límite temporal (`updatedAt <= cutoff`).
  - **P0 abiertos**:
    - `QA-20260302-BROTHER-AAR` (sin AAR no hay impresión física real).

## 2) Contexto y Entorno
- Android repo: `/Users/diegolden/Code/LCX/lcx-android` (base `af294f3`, rama `main`).
- Web/backend repo: `/Users/diegolden/Code/LCX/v0-lcx-pwa` (commit `5431d23`, rama `main`).
- Stack backend local validado: Next.js API routes + Supabase local.
- Config dev Android validada en build generado:
  - `API_BASE_URL = http://127.0.0.1:3000`
  - `SUPABASE_URL = http://127.0.0.1:54321`
  - `SUPABASE_ANON_KEY = <desde supabase status -o env>`

## 3) Q1 Preflight Agent

### 3.1 Comandos obligatorios
```bash
adb devices
adb reverse tcp:3000 tcp:3000
adb reverse tcp:54321 tcp:54321
```
Resultado:
```text
List of devices attached

adb: no devices/emulators found
adb: no devices/emulators found
```
Estado inicial: **FAIL**.

Actualización posterior:
```text
List of devices attached
49281FDAQ0011J         device usb:1-1 product:tokay model:Pixel_9 device:tokay transport_id:1

UsbFfs tcp:3000 tcp:3000
UsbFfs tcp:54321 tcp:54321
```
Estado actual: **PASS**.

### 3.2 Servicios locales
- Web local (`bun run dev`): **PASS**
- Supabase local (`supabase status`): **PASS**

Evidencia:
```text
node ... TCP *:3000 (LISTEN)
Project URL: http://127.0.0.1:54321
```

### 3.3 Build/instalación dev
Comando:
```bash
LCX_DEV_API_BASE_URL=http://127.0.0.1:3000 \
LCX_DEV_SUPABASE_URL=http://127.0.0.1:54321 \
LCX_DEV_SUPABASE_ANON_KEY=<from supabase status -o env> \
./gradlew :app:installDevDebug
```
Resultado:
- Compilación: **PASS**
- Instalación en device: **FAIL** (intento inicial)
- Instalación en device: **PASS** (intento actualizado)

Evidencia:
```text
Execution failed for task ':app:installDevDebug'.
com.android.builder.testing.api.DeviceException: No connected devices!

...

Task :app:installDevDebug
Installing APK 'app-dev-debug.apk' on 'Pixel 9 - 16' for :app:dev-debug
Installed on 1 device.
```

## 4) Q2 Functional P0 Agent (Checklist)

> Nota: el bloqueo USB inicial se resolvió. Aun falta cerrar ejecución manual end-to-end con impresión real porque no está presente el AAR de Brother.

| Caso P0 | Físico USB | Cobertura alternativa local | Evidencia |
|---|---|---|---|
| Login válido/inválido | FAIL (bloqueado) | PASS | `AuthRepositoryTest` 9/9 OK |
| Crear ticket (validación + éxito) | FAIL (bloqueado) | PASS | `CreateTicketsContractTest` 16/16 OK |
| Cobro success | FAIL (bloqueado) | PASS | `TransactionOrchestratorTest` (happy path) |
| Cobro cancel (NO paid) | FAIL (bloqueado) | PASS | `TransactionOrchestratorTest` (`payment cancellation`) |
| Cobro success + fallo API (retry sync, NO recobrar) | FAIL (bloqueado) | PASS | `TransactionOrchestratorTest` (`PaymentSucceededApiFailed` + retry sync) |
| Impresión success | FAIL (bloqueado) | PASS | `TransactionOrchestratorTest` |
| Impresión fail + retry | FAIL (bloqueado) | PASS | `TransactionOrchestratorTest` (`print failure then retry succeeds`) |
| Impresión skip | FAIL (bloqueado) | PASS | `TransactionOrchestratorTest` (`print failure then skip`) |
| Reanudación tras kill app (`resumeTransaction`) | FAIL (bloqueado) | PASS | `TransactionOrchestratorTest` (bloque resume) + `TransactionPersistenceTest` |
| Opening checklist bloqueante (409) con mensaje claro | FAIL (bloqueado) | PASS | `CreateTicketsContractTest` (`409 OPENING_CHECKLIST...`) |

## 5) Q3 Observability Agent

### 5.1 Captura de logs en dispositivo
- `adb logcat` con filtros `TXN|HTTP|TICKET|PAYMENT|PRINT|Correlation`: **EJECUTABLE** (device visible). Evidencia final pendiente en corrida con AAR Brother.

### 5.2 Trazabilidad por correlación (verificación de implementación)
- Android emite/propaga correlación y logs por tags:
  - `TXN` en `TransactionOrchestrator`
  - `HTTP` en `CorrelationIdInterceptor`
  - `TICKET`, `PAYMENT`, `PRINT` en repositorios
- Backend consume `X-Correlation-Id` y registra en `audit_logs`:
  - `POST /api/tickets` -> `action: ticket_create`
  - `PATCH /api/tickets/:id/status` -> `action: status_update`
  - `PATCH /api/tickets/:id/payment` -> `action: payment_update`

Estado Q3: **PARCIAL** (implementación validada por código, evidencia runtime end-to-end pendiente de device).

## 6) Q4 Bugfix Agent

### 6.1 Bug detectado
- ID: `QA-20260302-01`
- Severidad: **P1**
- Área: persistencia de transacciones (`cleanup`)
- Síntoma: con `maxAge=0`, registros `COMPLETED/CANCELLED` en el límite temporal no se eliminaban (condición estricta `<`).
- Evidencia inicial:
```text
TransactionPersistenceTest > cleanup removes old completed records but keeps active ones FAILED
expected null, but was SavedTransaction(... phase=COMPLETED ...)
```

### 6.2 Fix aplicado
- Archivo: `app/src/main/java/com/cleanx/lcx/core/transaction/data/TransactionDao.kt`
- Cambio:
```sql
AND updatedAt < :olderThan
```
->
```sql
AND updatedAt <= :olderThan
```

Racional: hace inclusivo el corte temporal, evita dejar “zombies” terminales en el borde exacto del cutoff.

### 6.3 Regresión post-fix
- `:app:testProdReleaseUnitTest --tests "...TransactionPersistenceTest.cleanup removes old completed records but keeps active ones"` -> **PASS**
- `./gradlew test` -> **PASS**
- Suites P0 relevantes verificadas (XML):
  - `TransactionOrchestratorTest` 22/22 OK
  - `TransactionPersistenceTest` 11/11 OK
  - `CreateTicketsContractTest` 16/16 OK
  - `UpdatePaymentContractTest` 13/13 OK
  - `UpdateStatusContractTest` 13/13 OK
  - `AuthRepositoryTest` 9/9 OK

## 7) Bugs (con severidad)

| ID | Severidad | Estado | Descripción |
|---|---|---|---|
| QA-20260302-01 | P1 | FIXED | Cleanup de transacciones terminales no inclusivo en límite temporal (`<` vs `<=`). |
| QA-20260302-BT-PERM | P1 | FIXED | `BLUETOOTH_CONNECT` estaba denegado en Android 16; se añadió solicitud runtime en flujos de impresión/settings y permiso en manifest. |
| QA-20260302-BLOCKER-USB | P0 (infra) | OPEN | No hay dispositivo visible en `adb`; bloquea QA físico USB end-to-end. |
| QA-20260302-BROTHER-AAR | P0 | OPEN | No se puede activar impresión física real sin `BrotherPrintLibrary.aar`; build cae a `StubPrinterManager` por diseño. |

## 8) Commits
- `800b8ec` - `fix(android): make terminal transaction cleanup inclusive at cutoff`
- `29db1ae` - `docs(qa): add physical-device QA report for 2026-03-02`
- `1b5cf4c` - `fix(printing-ui): wire label payload and request bluetooth permission`
- `76340c4` - `feat(printing): add Brother SDK v4 manager with optional AAR wiring`
- `0b9ae0a` - `docs(qa): update physical-device report with Brother integration status`

## 9) Próximo paso operativo para cerrar QA físico
1. Conectar teléfono por USB y autorizar huella RSA (`adb devices -l` debe mostrar estado `device`).
2. Repetir:
   - `adb reverse tcp:3000 tcp:3000`
   - `adb reverse tcp:54321 tcp:54321`
   - `./gradlew :app:installDevDebug`
3. Ejecutar checklist P0 en dispositivo y adjuntar evidencia `adb logcat` + correlación en `audit_logs`.
