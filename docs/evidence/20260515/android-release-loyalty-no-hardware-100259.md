# Android Release Loyalty No-Hardware Gate

Date: 2026-05-15T16:02:59Z
Result: PASS
Device: 49281FDAQ0011J Pixel 9
Final installed package: com.cleanx.app
Final installed version: versionName=1.0.0 versionCode=100
Final install time: 2026-05-15 10:02:59
Release launch: PASS, process stayed alive after `monkey -p com.cleanx.app 1`
Release APK: /Users/diegolden/Code/LCX-OS/lcx-android/build/release-private/v1.0.0+100/app-prod-release.apk
Release SHA256: a073df9fe729cba26e923bc1d20bf324a1adc180f324ab9abb45f2993a1d4057
Signing cert SHA-256: f056c34e86a1f73a41ca9d4160e655c7a1ca70c6403a1e4af4c0db5cfd6226c5

## Checks
- `:app:verifyProdConfig`
- `:app:verifyReleaseSigning`
- `:feature:loyalty:testDebugUnitTest`
- `:app:testDevDebugUnitTest --tests com.cleanx.lcx.core.network.contract.LoyaltyPlatformContractTest`
- `:app:assembleProdRelease`
- `scripts/release/package-private-apk.sh`

## ADB no-hardware loyalty smoke
- Build under test: temporary `devDebug` with `applicationId=com.cleanx.app`, `PLATFORM_BASE_URL=http://127.0.0.1:8080`, `USE_REAL_ZETTLE=false`, `USE_REAL_BROTHER=false`.
- Hardware skipped: Brother discovery/print, real Zettle charge, ticket charge/print path.
- Device-validated calls: `LoyaltyRepository.getRewards`, `createAccount`, `getAccountDetail`, `listAccounts`, `issueWalletCard`.
- Instrumentation: /Users/diegolden/Code/LCX-OS/lcx-android/docs/evidence/20260515/android-adb-loyalty-platform-100111.instrumentation.txt
- Summary: /Users/diegolden/Code/LCX-OS/lcx-android/docs/evidence/20260515/android-adb-loyalty-platform-100111.md

## Notes
- Android has no `prodReleaseAndroidTest` variant, so instrumentation was run with a temporary no-hardware debug build and the signed release APK was reinstalled afterward.
- Final device state is the signed prod release, not the debug build.
