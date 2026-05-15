# Android Hardware Release Gate - 2026-05-14

## Scope

Release hardware evidence for the native Android E2E suite over ADB using the connected QA device, real Zettle reader, and real Brother printer.

This record covers the required scenarios after the Zettle/Brother hardware gate fixes. The full runner pass at `14:21:58` found two issues that were fixed afterward; the blocking Brother and ticket-path scenarios were then rerun in isolation to avoid extra real charges.

## Connected Setup

- Device: Pixel 9, ADB serial `49281FDAQ0011J`, package `com.cleanx.app`.
- Network: Wi-Fi `Rapips`, device IP `192.168.100.14`; active default network was Wi-Fi.
- ADB reverse: `tcp:3000`, `tcp:54321`, `tcp:8080`.
- Backend: local PWA and local Supabase through `127.0.0.1`.
- Build config: `devDebug`, `LCX_ANDROID_APPLICATION_ID=com.cleanx.app`, empty dev suffix, `LCX_DEV_USE_REAL_ZETTLE=true`, `LCX_DEV_USE_REAL_BROTHER=true`.
- Branch/operator: `La Esperanza`, `Operador E2E`, PIN `1234`.
- Printer: Brother `QL-810W`, Wi-Fi address `192.168.100.47`.
- Zettle: real SDK and real reader available; real `$1.00` charges were allowed for this gate.

## Scenario Matrix

| Scenario | Result | Evidence | Notes |
| --- | --- | --- | --- |
| `PreflightHardwareE2eTest` | PASS | `android-hardware-e2e-142158.instrumentation.txt` | Asserted package `com.cleanx.app`, real Zettle/Brother flags, and diagnostics. |
| `DeviceLoginE2eTest` | PASS | `android-hardware-e2e-142158.instrumentation.txt` | Seeded branch/operator/PIN login landed on dashboard. |
| `CriticalOperatorSmokeE2eTest` | PASS | `android-hardware-e2e-142158.instrumentation.txt` | Dashboard, Agua, Caja, Checklist, Encargos, diagnostics opened without runtime crash. |
| `RealZettleChargeE2eTest` | PASS | `android-hardware-e2e-142158.instrumentation.txt`, `android-hardware-e2e-142158.log` | Real `$1.00` diagnostic charge succeeded with transaction `03562d88-4fd3-11f1-a749-d60d5194c6b5`. |
| `RealBrotherPrintE2eTest` | PASS | `android-hardware-e2e-brother-rerun-144655.instrumentation.txt`, `android-hardware-e2e-brother-rerun-144655.log` | Isolated rerun passed and discovered `QL-810W` on Wi-Fi. |
| `TicketHardwarePathE2eTest` | PASS | `android-hardware-e2e-ticket-rerun-145010.instrumentation.txt`, `android-hardware-e2e-ticket-rerun-145010.log`, `android-hardware-e2e-ticket-rerun-145010.payload.jsonl` | Ticket `T-20260514-0011` charged, backend marked paid, and Brother printed the label. |

## Zettle Evidence

- Diagnostic charge: transaction `03562d88-4fd3-11f1-a749-d60d5194c6b5`, amount `$1.00`.
- Ticket charge: transaction `88b0628a-4fd6-11f1-ba99-9789d5b9f344`, reference `23ad2ae2-f24f-4f36-a3e8-dc411fdae2fe`.
- Zettle session was preserved after the runner change; no app-data clear happened between diagnostic and ticket-path execution in the full runner attempt.
- No Java crash was recorded during the successful ticket rerun.

## Brother Evidence

- `RealBrotherPrintE2eTest` passed in 11.278s.
- Logcat recorded `PrintModule: using BrotherPrinterManager (useRealBrother=true)`.
- Discovery recorded `Brother discovered printer: name=QL-810W type=WIFI address=192.168.100.47`.
- Ticket path recorded `Brother print success: ticket=T-20260514-0011 bag=1/1 copy=1 printer=QL-810W`.

## Backend Evidence

- Ticket id: `23ad2ae2-f24f-4f36-a3e8-dc411fdae2fe`.
- Ticket number: `T-20260514-0011`.
- Payment PATCH succeeded against local PWA: `payment_status=paid`, `payment_method=card`, `paid_amount=1`, `paid_at=2026-05-14T20:50:33.28Z`.

## Evidence Files

- Full-suite attempt: `android-hardware-e2e-142158.md`, `.instrumentation.txt`, `.log`, `.jsonl`, `.seed.json`.
- Brother rerun: `android-hardware-e2e-brother-rerun-144655.md`, `.instrumentation.txt`, `.log`.
- Ticket-path rerun: `android-hardware-e2e-ticket-rerun-145010.md`, `.instrumentation.txt`, `.log`, `.network.txt`, `.payload.jsonl`.

## Release Read

Hardware scenarios required by the release gate have green evidence on the connected Pixel 9, real Zettle, and Brother `QL-810W`.

For a strict single-command signoff, rerun the canonical suite once after these fixes. That will create additional real `$1.00` charges for the diagnostic and ticket-path checks.
