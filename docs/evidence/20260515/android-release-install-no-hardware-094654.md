# Android Release Install + No-Hardware Smoke

Date: 2026-05-15T15:46:54Z
Result: PASS
Device: 49281FDAQ0011J Pixel 9
Final installed package: com.cleanx.app
Final installed version: versionName=1.0.0 versionCode=100
Final install time: 2026-05-15 09:46:24
Release APK: /Users/diegolden/Code/LCX-OS/lcx-android/build/release-private/v1.0.0+100/app-prod-release.apk
Release SHA256: 907862f238b43ebd2df5612a0a086ceaa89ac0920cca8474568009460a70f4a1

## Checks
- Installed the signed prod release after uninstalling the previous debug build.
- Launched the release package with `monkey -p com.cleanx.app 1`; process stayed alive and no app crash signature was found in logcat.
- Ran no-hardware ADB instrumentation against a temporary devDebug build with the same package id and hardware integrations disabled.
- Skipped Brother printer discovery/print, real Zettle charge, and ticket charge/print path.
- Ran `:feature:loyalty:testDebugUnitTest`.
- Ran local `lcx-platform` loyalty smoke:
  - `/health/liveness`: alive
  - `/v1/loyalty/rewards`: 4 rewards
  - `/v1/loyalty/accounts?limit=1`: 0 accounts

## Evidence
- No-hardware instrumentation: /Users/diegolden/Code/LCX-OS/lcx-android/docs/evidence/20260515/android-adb-no-hardware-094443.instrumentation.txt
- No-hardware summary: /Users/diegolden/Code/LCX-OS/lcx-android/docs/evidence/20260515/android-adb-no-hardware-094443.md
- No-hardware logcat: /Users/diegolden/Code/LCX-OS/lcx-android/docs/evidence/20260515/android-adb-no-hardware-094443.logcat.txt
