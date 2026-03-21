# QA Physical Device Report (2026-03-20)

Updated: 2026-03-20
Device: Pixel 9 (Android 16 / API 36)
Build: devDebug
ApplicationId: com.cleanx.app (LCX_ANDROID_APPLICATION_ID, sin suffix)
Zettle: USE_REAL_ZETTLE=true
Brother: USE_REAL_BROTHER=true

## Backend
- API base: http://127.0.0.1:3000
- Supabase: http://127.0.0.1:54321

## Flujo E2E validado
1. Login en dispositivo.
2. Cobro real con Zettle (SDK real).
3. Retorno a ticket y refresh automatico del estado.
4. Impresion de etiqueta con Brother.

Resultado: OK (E2E real confirmado en dispositivo).

## Evidencia
- Logcat: /Users/diegolden/Code/LCX/lcx-android/docs/evidence/20260320/porting-demo-smoke-165105.log

## Build prod-like
- `./gradlew :app:assembleProdDebug` (OK)
