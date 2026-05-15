# Android Hardware E2E Brother Rerun

Date: 2026-05-14T20:47:11Z
Result: PASS
Device: 49281FDAQ0011J Pixel 9
Package: com.cleanx.app
Printer selector: QL-810W
Force stop before run: true
Real Brother build flag: true

## Evidence
- Instrumentation: /Users/diegolden/Code/LCX-OS/lcx-android/docs/evidence/20260514/android-hardware-e2e-brother-rerun-144655.instrumentation.txt
- Logcat: /Users/diegolden/Code/LCX-OS/lcx-android/docs/evidence/20260514/android-hardware-e2e-brother-rerun-144655.log

## Highlights

- `RealBrotherPrintE2eTest` passed in instrumentation (`OK (1 test)`).
- Real Brother mode was active: `PrintModule: using BrotherPrinterManager (useRealBrother=true)`.
- Discovery found `QL-810W` as Wi-Fi printer at `192.168.100.47`.
- The ticket hardware rerun captured the print-success log for the same printer and ticket `T-20260514-0011`.
