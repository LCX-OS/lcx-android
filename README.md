# CleanX Android (LCX)

Aplicacion nativa Android para operacion de sucursal (canal de campo), construida con Kotlin + Jetpack Compose + Hilt + Supabase.

## Estado del repositorio

- `README.md` (este archivo): guia practica para desarrollo, QA en device y release.
- `docs/api-contract-spec.md`: contrato API para cliente Android.
- `docs/qa-physical-device-report-20260302.md`: evidencia de QA fisico por USB.
- `docs/porting/`: inventario de features PWA, matriz de paridad y backlog de porteo.

## Estrategia de plataforma (alineada al PWA)

- `Web (v0-lcx-pwa)`: canal estable de escritorio (gerencia/superadmin y administracion).
- `Android (este repo)`: canal de campo para operacion en sucursal con integraciones hardware.
- Contratos criticos de tickets que Android debe preservar:
  - `POST /api/tickets`
  - `PATCH /api/tickets/:id/status`
  - `PATCH /api/tickets/:id/payment`

Referencia de contrato: `docs/api-contract-spec.md`.

## Arquitectura y modulos

- `:app` -> shell de navegacion, DI de `BuildConfig`, wiring de features.
- `:core` -> red, sesion, cache transaccional, utilidades compartidas.
- `:feature:auth`
- `:feature:tickets`
- `:feature:payments`
- `:feature:printing`
- `:feature:cash`
- `:feature:water` (en desarrollo)
- `:feature:checklist` (en desarrollo)

## Requisitos

- macOS con Android SDK + `adb`.
- JDK 21 recomendado para toolchain actual.
- Gradle wrapper (incluido).
- Backend local en repo hermano: `/Users/diegolden/Code/LCX/v0-lcx-pwa`.
- Supabase CLI para entorno local.

Verificacion rapida:

```bash
java -version
adb version
```

## Quick Start (dev local con telefono USB)

### 1) Arrancar backend local (PWA repo)

```bash
cd /Users/diegolden/Code/LCX/v0-lcx-pwa
supabase start -x studio,imgproxy,mailpit
bun run dev
```

### 2) Preflight ADB + reverse ports

```bash
adb devices -l
adb reverse tcp:3000 tcp:3000
adb reverse tcp:54321 tcp:54321
adb reverse --list
```

### 3) Resolver ANON key local

```bash
cd /Users/diegolden/Code/LCX/v0-lcx-pwa
ANON_KEY="$(supabase status -o env | awk -F= '/^ANON_KEY=/{print $2}' | sed 's/^\"//; s/\"$//')"
```

### 4) Build e instalacion `devDebug`

```bash
cd /Users/diegolden/Code/LCX/lcx-android

export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

export LCX_DEV_API_BASE_URL="http://127.0.0.1:3000"
export LCX_DEV_SUPABASE_URL="http://127.0.0.1:54321"
export LCX_DEV_SUPABASE_ANON_KEY="$ANON_KEY"
export LCX_DEV_USE_REAL_BROTHER="true"   # opcional (default false)

./gradlew :app:installDevDebug
```

Nota:
- En emulador Android usa defaults `10.0.2.2`.
- En device fisico por USB usa `127.0.0.1` + `adb reverse`.

## Configuracion por flavor

Definicion en `app/build.gradle.kts`:

- `dev`
  - `API_BASE_URL` y `SUPABASE_*` desde env/gradle property.
  - `USE_REAL_ZETTLE=false` (stub de pagos en dev).
  - `USE_REAL_BROTHER` configurable por `LCX_DEV_USE_REAL_BROTHER`.
- `staging`
  - placeholders de ejemplo, requiere valores reales antes de uso.
- `prod`
  - placeholders de ejemplo, requiere valores reales antes de release.

Variables soportadas:

- `LCX_DEV_API_BASE_URL`
- `LCX_DEV_SUPABASE_URL`
- `LCX_DEV_SUPABASE_ANON_KEY`
- `LCX_DEV_USE_REAL_BROTHER`

## Brother SDK (impresion real)

Para impresion real Brother en `dev`:

1. Coloca el AAR en:
   - `feature/printing/libs/BrotherPrintLibrary.aar`
2. Exporta:
   - `LCX_DEV_USE_REAL_BROTHER=true`
3. Reinstala:
   - `./gradlew :app:installDevDebug`

Si el AAR no existe, la app compila y usa fallback/stub (sin impresion real).

## Comandos importantes

```bash
# Build principal
./gradlew :app:assembleDevDebug

# Instalar en device conectado
./gradlew :app:installDevDebug

# Tests de modulo Caja (P0 actual)
./gradlew :feature:cash:test

# Suite de tests (todos los modulos)
./gradlew test
```

Scripts utiles:

```bash
# Preflight no destructivo (adb + backend + env)
scripts/qa/worktree-smoke-preflight.sh

# Igual que arriba, instalando devDebug
scripts/qa/worktree-smoke-preflight.sh --install

# Captura de logcat filtrado para demo/QA
scripts/qa/porting-demo-smoke.sh --install --duration 180
```

## Observabilidad y logs

Tags y patrones de seguimiento usados en QA:

- `TXN`
- `HTTP`
- `TICKET`
- `PAYMENT`
- `PRINT`
- `AUTH`
- `WATER`
- `CHECKLIST`
- `CAJA`
- `Correlation`

Comando base:

```bash
adb logcat -v threadtime | rg -i "TXN|HTTP|TICKET|PAYMENT|PRINT|AUTH|WATER|CHECKLIST|CAJA|Correlation|Session"
```

## QA minimo P0 (device fisico)

- Login valido/invalido.
- Create Ticket (`POST /api/tickets`).
- Charge:
  - success,
  - cancel (no marcar `paid`),
  - success + fallo API (retry de sync, no recobrar).
- Print:
  - success,
  - fail + retry,
  - skip.
- Resume tras kill app (`resumeTransaction`).
- Caso `OPENING_CHECKLIST_BLOCKING_OPERATION` (409) con mensaje claro.

Reporte base: `docs/qa-physical-device-report-20260302.md`.

## Checklist de salida (release gate)

- [ ] `./gradlew :app:assembleDevDebug` y/o variante objetivo sin errores.
- [ ] `./gradlew test` en verde (al menos modulos tocados).
- [ ] QA fisico P0 actualizado con evidencia de logs/comandos.
- [ ] Contratos criticos de tickets sin drift contra `docs/api-contract-spec.md`.
- [ ] Verificacion de flags hardware por ambiente (`USE_REAL_*`).
- [ ] Matriz de paridad actualizada (`docs/porting/parity-matrix-*.md`).

## Documentacion recomendada para porteo

- `docs/porting/pwa-feature-inventory-20260303.md`
- `docs/porting/parity-matrix-20260303.md`
- `docs/porting/route-subagent-backlog-20260303.md`
- `docs/porting/android-port-wave-report-20260303.md`
- `docs/porting/caja-wave-report-20260303.md`

