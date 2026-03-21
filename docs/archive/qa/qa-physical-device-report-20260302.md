# QA Physical Device Report - 2026-03-02

## 0) Estado actual (actualizado 2026-03-02 19:32 CST)
- USB/ADB: **PASS**.
- Web local + Supabase local: **PASS**.
- Build/instalación dev en Pixel 9: **PASS**.
- Impresión Brother real (etiqueta 209): **PASS** (evidencia física en logcat).
- Hallazgo crítico del retry: `401 NOT_AUTHENTICATED` al crear ticket con sesión expirada.
- Fix aplicado en Android para re-login manual desde flujo operativo: botón `Salir` en `TicketList` que limpia sesión y vuelve a `Login`.

## 1) Contexto y entorno
- Android repo: `/Users/diegolden/Code/LCX/lcx-android`.
- Backend repo: `/Users/diegolden/Code/LCX/v0-lcx-pwa`.
- Device físico: Pixel 9 (Android 16), serial `49281FDAQ0011J`.
- Contratos backend validados contra local:
  - `POST /api/tickets`
  - `PATCH /api/tickets/:id/status`
  - `PATCH /api/tickets/:id/payment`

Variables dev usadas en instalación:

```bash
LCX_DEV_API_BASE_URL=http://127.0.0.1:3000
LCX_DEV_SUPABASE_URL=http://127.0.0.1:54321
LCX_DEV_SUPABASE_ANON_KEY=<ANON_KEY de supabase status -o env>
```

## 2) Q1 Preflight Agent

Comandos ejecutados:

```bash
/Users/diegolden/Library/Android/sdk/platform-tools/adb devices -l
/Users/diegolden/Library/Android/sdk/platform-tools/adb reverse tcp:3000 tcp:3000
/Users/diegolden/Library/Android/sdk/platform-tools/adb reverse tcp:54321 tcp:54321
```

Salida:

```text
List of devices attached
49281FDAQ0011J device usb:1-1 product:tokay model:Pixel_9 device:tokay transport_id:1

UsbFfs tcp:3000 tcp:3000
UsbFfs tcp:54321 tcp:54321
```

Servicios locales:

```text
node ... TCP *:3000 (LISTEN)
supabase local development setup is running.
Project URL: http://127.0.0.1:54321
```

Instalación dev:

```bash
./gradlew :app:installDevDebug
```

Resultado:

```text
Task :app:installDevDebug
Installing APK 'app-dev-debug.apk' on 'Pixel 9 - 16' for :app:dev-debug
Installed on 1 device.
```

## 3) Q2 Functional P0 Agent (checklist mínimo)

| Caso P0 | Resultado físico | Evidencia |
|---|---|---|
| Login válido/inválido | **FAIL (retry)** | `401 NOT_AUTHENTICATED` en `POST /api/tickets` por sesión expirada. Ver `docs/evidence/20260302/retry-auth-401.md` |
| Crear ticket (validación + éxito) | **PASS** | `ticket_create` 200 + correlación `f712a2b8-ea26-447c-a0a6-e189d10e4a2e` |
| Cobro success | **PASS** | `PAYMENT Charge result: Success` + `PATCH /payment` 200 (correlations `922314e0...`, `4ebb80b9...`) |
| Cobro cancel (NO paid) | **PENDING** | Sin corrida física dedicada en esta sesión |
| Cobro success + fallo API (retry sync, NO recobrar) | **PENDING** | Cubierto por tests, falta corrida física dedicada |
| Impresión success | **PASS** | `BrotherPrinterManager`, discovery 16, `Brother print success` |
| Impresión fail + retry | **PENDING** | Sin corrida física dedicada en esta sesión |
| Impresión skip | **PENDING** | Sin corrida física dedicada en esta sesión |
| Reanudación tras kill app (`resumeTransaction`) | **PENDING** | Sin corrida física dedicada en esta sesión |
| Opening checklist bloqueante (409) con mensaje claro | **PENDING (físico)** | Cobertura de contrato PASS (`CreateTicketsContractTest`) |

## 4) Q3 Observability Agent

### 4.1 Evidencia física de impresión real
Archivo: `docs/evidence/20260302/device-smoke-summary.md`

Líneas clave:

```text
[f712a2b8-ea26-447c-a0a6-e189d10e4a2e] POST http://127.0.0.1:3000/api/tickets
[f712a2b8-ea26-447c-a0a6-e189d10e4a2e] 200 http://127.0.0.1:3000/api/tickets
PrintModule: using BrotherPrinterManager (useRealBrother=true)
Brother discovery completed: 16 printer(s)
Brother connected: type=WIFI address=192.168.100.47 name=QL-810W
Brother print success: ticket=T-20260303-0004 folio=4 printer=QL-810W
```

### 4.2 Correlación móvil -> backend
Comando:

```bash
cd /Users/diegolden/Code/LCX/v0-lcx-pwa
scripts/qa/correlation-audit-proof.sh f712a2b8-ea26-447c-a0a6-e189d10e4a2e
```

Resultado:

```text
| ticket_create | 2026-03-03 01:14:52.733 | /api/tickets | f712a2b8-ea26-447c-a0a6-e189d10e4a2e | source=encargo count=1 |
```

### 4.3 Retry con 401 (sin auditoría)
Archivo: `docs/evidence/20260302/retry-auth-401.md`

Correlations:
- `51356494-2991-4c11-b2cb-1b8bbd5704f8`
- `807c34a8-d022-45f9-832a-0358132f50c7`

Ambas sin filas en `audit_logs` porque el request falla en auth antes de persistencia.

## 5) Q4 Bugfix Agent

### 5.1 Bugs detectados y severidad

| ID | Severidad | Estado | Descripción |
|---|---|---|---|
| QA-20260302-01 | P1 | FIXED | Cleanup de transacciones terminales (`updatedAt <= cutoff`). |
| QA-20260302-BT-PERM | P1 | FIXED | Permiso `BLUETOOTH_CONNECT` solicitado en runtime. |
| QA-20260302-BLOCKER-USB | P0 (infra) | RESOLVED | `adb` y reverse ports funcionando en Pixel 9. |
| QA-20260302-BROTHER-AAR | P0 | RESOLVED (local) | AAR cargado desde `bpsdka4130.zip` y build instalado con impresión real. |
| QA-20260302-RELOGIN-ROUTE | P0 | FIXED (pendiente retest UX) | No había ruta visible para re-login cuando expira sesión; se agregó `Salir` en `TicketList` para limpiar sesión y volver a `Login`. |

### 5.2 Fix aplicado en esta corrida
Archivos modificados:
- `app/src/main/java/com/cleanx/lcx/core/navigation/LcxNavHost.kt`
- `feature/tickets/src/main/java/com/cleanx/lcx/feature/tickets/ui/list/TicketListScreen.kt`
- `feature/tickets/src/main/java/com/cleanx/lcx/feature/tickets/ui/list/TicketListViewModel.kt`

Cambio funcional:
- Acción `Salir` en top bar de `TicketList`.
- `Salir` ejecuta `SessionManager.clearSession()`.
- Navegación explícita a `Screen.Login` (re-login disponible sin matar app).

### 5.3 Regresión ejecutada

```bash
./gradlew :app:installDevDebug
```

Resultado: **PASS** (`Installed on 1 device.`).

## 6) Commits relevantes
Histórico base:
- `983fd9f`
- `c7722f8`
- `af294f3`
- `800b8ec`
- `1b5cf4c`
- `76340c4`
- `0b9ae0a`
- `b103c04`

Corrida actual:
- `c40a5d0` - `fix(auth): add explicit relogin path from ticket list`
- `96741fd` - `docs(qa): update physical-device report with print pass and 401 retry evidence`

## 7) Estado final de cierre P0
- Bloqueantes de infraestructura (USB/ADB): **cerrados**.
- Bloqueante de impresión real Brother: **cerrado localmente** con AAR presente.
- Flujo real con impresión física: **demostrado**.
- Bloqueo operativo nuevo (sesión expirada sin ruta de re-login): **fix aplicado**, pendiente confirmar UX final en dispositivo con prueba rápida (`Salir -> Login -> Login válido -> Crear ticket`).
