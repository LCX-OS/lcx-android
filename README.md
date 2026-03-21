# CleanX Android (LCX)

Aplicacion nativa Android para operacion de sucursal (canal de campo), construida con Kotlin + Jetpack Compose + Hilt + Supabase.

## Estado del repositorio

- `README.md` (este archivo): guia practica para desarrollo local, flavors, comandos y release entrypoint.
- `docs/README.md`: indice de documentacion y mapa de archivo historico.
- `docs/android-release.md`: flujo canonico para signing y `:app:assembleProdRelease`.
- `docs/api-contract-spec.md`: contrato API para cliente Android.
- `docs/porting/README.md`: fuente actual para parity/porting y `route-registry.json`.
- `docs/evidence/`: evidencia operativa fechada (payload captures, smoke logs, notas de debugging).

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

Patron actual recomendado para features nuevas o flujos complejos:

- `data/` -> acceso a red / Supabase y modelos de transporte.
- `domain/<flow>/` -> estado, reducer y use cases puros del flujo.
- `ui/` -> Compose y `ViewModel` como capa fina de orquestacion.

Referencia viva:

- `docs/architecture/domain-first-feature-pattern.md`

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
export LCX_DEV_NOTIFICATIONS_BASE_URL="http://127.0.0.1:8080"
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
  - `USE_REAL_ZETTLE=false` y `USE_REAL_BROTHER=false` por default.
  - se puede usar con hardware real para smoke local si activas los flags manualmente.
- `prod`
  - `API_BASE_URL`, `SUPABASE_*` y flags desde env/gradle properties.
  - `USE_REAL_ZETTLE=true` y `USE_REAL_BROTHER=true` son obligatorios.
  - `verifyProdConfig` falla si faltan valores reales, si queda algun placeholder, si el `applicationId` no coincide con el aprobado por Zettle o si falta el AAR de Brother.

Variables soportadas:

- `LCX_DEV_API_BASE_URL`
- `LCX_DEV_NOTIFICATIONS_BASE_URL`
- `LCX_DEV_SUPABASE_URL`
- `LCX_DEV_SUPABASE_ANON_KEY`
- `LCX_DEV_APPLICATION_ID_SUFFIX`
- `LCX_DEV_USE_REAL_ZETTLE`
- `LCX_DEV_USE_REAL_BROTHER`
- `LCX_ANDROID_APPLICATION_ID`
- `LCX_PROD_API_BASE_URL`
- `LCX_PROD_NOTIFICATIONS_BASE_URL`
- `LCX_PROD_SUPABASE_URL`
- `LCX_PROD_SUPABASE_ANON_KEY`
- `LCX_PROD_USE_REAL_ZETTLE`
- `LCX_PROD_USE_REAL_BROTHER`
- `LCX_ZETTLE_GITHUB_TOKEN`
- `LCX_ZETTLE_CLIENT_ID`
- `LCX_ZETTLE_REDIRECT_URL`
- `LCX_ZETTLE_APPROVED_APPLICATION_ID`

## Brother SDK (impresion real)

Ruta esperada del AAR real:

- `feature/printing/libs/BrotherPrintLibrary.aar`

Uso:

1. `dev`
   - exporta `LCX_DEV_USE_REAL_BROTHER=true`
   - reinstala con `./gradlew :app:installDevDebug`
2. `prod`
   - el AAR es obligatorio; `:app:verifyProdConfig` falla si falta

Si el AAR no existe, `dev` puede seguir compilando con fallback/stub. `prod` no.

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

## Release Android firmado

- Solo existen dos ambientes: `dev` y `prod`.
- El artefacto principal de release es el APK firmado de `./gradlew :app:assembleProdRelease --console=plain`.
- La distribucion por defecto es APK firmado por canal interno controlado; Play/AAB queda como opcion secundaria.
- Valida ambiente y signing con `./gradlew :app:verifyProdConfig :app:verifyReleaseSigning --console=plain`.
- El `release` signing se configura fuera de git con `~/.gradle/gradle.properties` o `key.properties` local.
- Guia operativa y checklist detallado: `docs/android-release.md`.
- `./gradlew :app:bundleProdRelease --console=plain` puede seguir usandose si luego necesitas Play Console o Internal App Sharing, pero no es el flujo principal.
- Para `prodRelease` con Zettle real, mantén `LCX_ANDROID_APPLICATION_ID=com.cleanx.app`.
- `LCX_ZETTLE_GITHUB_TOKEN` solo hace falta cuando esta maquina necesita volver a resolver el SDK privado de Zettle desde GitHub Packages.

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

La evidencia viva queda en `docs/evidence/`; los reportes narrativos fechados quedaron archivados en `docs/archive/qa/`.

## Checklist de salida (release gate)

- [ ] `./gradlew :app:assembleDevDebug` y/o variante objetivo sin errores.
- [ ] `./gradlew test` en verde (al menos modulos tocados).
- [ ] QA fisico P0 actualizado con evidencia de logs/comandos.
- [ ] Contratos criticos de tickets sin drift contra `docs/api-contract-spec.md`.
- [ ] Verificacion de flags hardware por ambiente (`USE_REAL_*`).
- [ ] Gate de parity/porting actualizado en `docs/porting/native-feature-gate.md` y/o `docs/porting/route-registry.json`.

## Documentacion recomendada para porteo

- `docs/porting/README.md`
- `docs/porting/native-feature-gate.md`
- `scripts/porting/verify-parity.sh`
