# Android Hardware E2E

Date: 2026-05-14T19:41:21Z
Result: FAIL / interrupted after Zettle SDK block
Device: 49281FDAQ0011J Pixel 9
Package: com.cleanx.app
Allow real charge: true
Run ticket hardware path: true
Brother printer: QL-810W
Zettle network mode: wifi
Reuse Zettle session for ticket: true

## Seed
- Branch: La Esperanza
- Operator: Operador E2E
- Ticket: T-20260514-0009

## Results
- CriticalOperatorSmokeE2eTest: PASS
- DeviceLoginE2eTest: PASS
- PreflightHardwareE2eTest: PASS
- RealBrotherPrintE2eTest: PASS, printed test label on QL-810W
- RealZettleChargeE2eTest: FAIL, ZettleActivity remained visible with blank UI and did not return a success result before the 420000 ms timeout
- TicketHardwarePathE2eTest: interrupted intentionally after it reopened Zettle login/onboarding, to avoid a second blind charge attempt while Zettle was already stuck

## Evidence
- Instrumentation: /Users/diegolden/Code/LCX-OS/lcx-android/docs/evidence/20260514/android-hardware-e2e-132913.instrumentation.txt
- Logcat: /Users/diegolden/Code/LCX-OS/lcx-android/docs/evidence/20260514/android-hardware-e2e-132913.log (3631 lines)
- Payload capture: /Users/diegolden/Code/LCX-OS/lcx-android/docs/evidence/20260514/android-hardware-e2e-132913.jsonl (30 lines)
- Seed data: /Users/diegolden/Code/LCX-OS/lcx-android/docs/evidence/20260514/android-hardware-e2e-132913.seed.json

## Notes
- Android crash buffer was empty after the observed close/blank state.
- The captured failure is not a Java crash in LCX; the foreground activity remained com.cleanx.app/com.zettle.sdk.ui.ZettleActivity until the test timeout.
