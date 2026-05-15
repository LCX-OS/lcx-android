# Android Release Signing y Build

Esta guia deja operativo el release Android con solo dos ambientes: `dev` y `prod`.
El artefacto principal es el APK firmado generado por `:app:assembleProdRelease`.
La instalacion y rollout en dispositivos vive en [`private-apk-rollout.md`](private-apk-rollout.md).

## 1. Modelo de ambientes

- `dev`
  - pensado para desarrollo local
  - usa backend local por default
  - `USE_REAL_ZETTLE` y `USE_REAL_BROTHER` quedan opt-in
- `prod`
  - build operacional real
  - `USE_REAL_ZETTLE=true` y `USE_REAL_BROTHER=true` son obligatorios
  - no acepta placeholders ni valores dummy
  - sale con `usesCleartextTraffic=false` y `allowBackup=false`
  - mantiene `applicationId=com.cleanx.app` para Zettle real

## 2. Configuracion real de `prod` fuera de git

Configura los valores reales en `~/.gradle/gradle.properties` o como variables de entorno:

```properties
LCX_PROD_API_BASE_URL=https://...
LCX_PROD_PLATFORM_BASE_URL=https://...
LCX_PROD_NOTIFICATIONS_BASE_URL=https://...
LCX_PROD_SUPABASE_URL=https://<project>.supabase.co
LCX_PROD_SUPABASE_ANON_KEY=...
LCX_PROD_DEVICE_AUTH_BOOTSTRAP_TOKEN=...
LCX_PROD_USE_REAL_ZETTLE=true
LCX_PROD_USE_REAL_BROTHER=true

LCX_ANDROID_APPLICATION_ID=com.cleanx.app
LCX_ZETTLE_APPROVED_APPLICATION_ID=com.cleanx.app
```

Notas:

- `LCX_PROD_NOTIFICATIONS_BASE_URL` puede omitirse si usa la misma base que `LCX_PROD_API_BASE_URL`.
- `LCX_PROD_PLATFORM_BASE_URL` es obligatorio y debe apuntar a `lcx-platform`; loyalty usa `/v1/loyalty/*` desde esa base.
- `LCX_PROD_DEVICE_AUTH_BOOTSTRAP_TOKEN` debe coincidir con `LCX_DEVICE_AUTH_BOOTSTRAP_TOKEN` en el PWA/API para cargar operadores Android.
- `:app:verifyProdConfig` falla de forma explicita si falta algun valor real, si queda un placeholder, si alguno de los flags `LCX_PROD_USE_REAL_*` se apaga, si el `applicationId` no coincide con el aprobado por Zettle o si falta el AAR de Brother.
- El AAR esperado para impresion real es `feature/printing/libs/BrotherPrintLibrary.aar`.
- `LCX_ZETTLE_GITHUB_TOKEN` no se trata como placeholder obligatorio por si solo. Solo hace falta cuando esta maquina necesita volver a resolver el SDK privado de Zettle desde GitHub Packages, por ejemplo en una cache nueva o despues de limpiar artefactos.

## 3. Signing local fuera de git

### Opcion recomendada: `~/.gradle/gradle.properties`

Agrega:

```properties
LCX_RELEASE_STORE_FILE=/absolute/path/to/lcx-upload.jks
LCX_RELEASE_STORE_PASSWORD=...
LCX_RELEASE_KEY_ALIAS=...
LCX_RELEASE_KEY_PASSWORD=...
```

Notas:

- `LCX_RELEASE_STORE_FILE` puede ser absoluto o relativo al root del repo.
- `:app:verifyReleaseSigning` falla de forma explicita si falta alguna propiedad o si la keystore no existe.
- Si `keytool` genera un store PKCS12, usa la misma contraseña para `LCX_RELEASE_STORE_PASSWORD` y `LCX_RELEASE_KEY_PASSWORD`.

### Opcion alternativa: `key.properties` local

1. Copia `key.properties.example` a `key.properties`.
2. Llena los cuatro valores de signing.
3. Mantén la keystore fuera del repo.

El build acepta tanto las claves `LCX_RELEASE_*` como los aliases Android estandar `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.

### Si aun no existe una keystore formal

```bash
mkdir -p "$HOME/secure/lcx"
keytool -genkeypair \
  -v \
  -keystore "$HOME/secure/lcx/lcx-upload.jks" \
  -alias lcxupload \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

## 4. Validacion local

Antes de un release interno:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:verifyProdConfig --console=plain
./gradlew :app:verifyReleaseSigning --console=plain
./gradlew test --console=plain
./gradlew :app:assembleProdRelease --console=plain
```

Opcional:

```bash
./gradlew :app:bundleProdRelease --console=plain
./gradlew :app:signingReport --console=plain
```

Si el release toca loyalty y tienes token operativo de plataforma:

```bash
LCX_PLATFORM_BASE_URL="$LCX_PROD_PLATFORM_BASE_URL" \
LCX_PLATFORM_BEARER_TOKEN="$LCX_PLATFORM_BEARER_TOKEN" \
scripts/qa/loyalty-platform-smoke.sh
```

### Hardware E2E release gate

Antes de liberar un APK que toca pagos, tickets o impresion, valida el hardware real con el suite instrumentado por ADB:

```bash
set -a
source /Users/diegolden/Code/LCX-OS/lcx-pwa/.env.local
set +a

LCX_ANDROID_APPLICATION_ID=com.cleanx.app \
LCX_DEV_APPLICATION_ID_SUFFIX= \
LCX_DEV_API_BASE_URL=http://127.0.0.1:3000 \
LCX_DEV_SUPABASE_URL=http://127.0.0.1:54321 \
LCX_DEV_PLATFORM_BASE_URL=http://127.0.0.1:8080 \
LCX_DEV_DEVICE_AUTH_BOOTSTRAP_TOKEN=local-device-auth \
LCX_DEV_USE_REAL_ZETTLE=true \
LCX_DEV_USE_REAL_BROTHER=true \
LCX_E2E_ZETTLE_NETWORK_MODE=wifi \
scripts/qa/android-hardware-e2e.sh --serial 49281FDAQ0011J --allow-real-charge --run-ticket-hardware-path
```

Reglas del gate:

- El telefono debe estar en una sola red estable preparada por QA; el runner no cambia `svc wifi/data`.
- `pm clear com.cleanx.app` ocurre solo al inicio del suite para no borrar tokens OAuth de Zettle entre clases.
- Si `RealZettleChargeE2eTest` falla, `TicketHardwarePathE2eTest` se omite para evitar doble cargo ciego.
- `TicketHardwarePathE2eTest` requiere `--allow-real-charge --run-ticket-hardware-path` y ejecuta un cargo real.

Estado validado el 2026-05-14:

- Device: Pixel 9 `49281FDAQ0011J`, package `com.cleanx.app`.
- Red: Wi-Fi `Rapips`, IP `192.168.100.14`, reverses `3000/54321/8080`.
- Zettle: cargo diagnostico real `$1.00` PASS, `transactionId=03562d88-4fd3-11f1-a749-d60d5194c6b5`.
- Brother: `QL-810W` descubierta en `192.168.100.47`; prueba aislada PASS.
- Ticket path: `T-20260514-0011` PASS, cargo real `$1.00` con `transactionId=88b0628a-4fd6-11f1-ba99-9789d5b9f344`, backend `payment_status=paid`, impresion Brother exitosa.
- Rollup de evidencia: `docs/evidence/20260514/android-hardware-release-gate.md`.

Nota: la corrida completa `android-hardware-e2e-142158` fallo antes de los fixes finales de seleccion Brother y boton de cobro de ticket. Los escenarios bloqueantes quedaron verdes con reruns enfocados para evitar cargos extra; para firma formal se puede repetir el comando completo una sola vez.

## 5. Artefacto

Genera el APK firmado:

```bash
./gradlew :app:assembleProdRelease --console=plain
```

Salida esperada:

- `app/build/outputs/apk/prod/release/app-prod-release.apk`

Empaqueta los archivos operativos para instalacion privada:

```bash
scripts/release/package-private-apk.sh
```

Salida esperada en `build/release-private/v1.0.0+100/`:

- `app-prod-release.apk`
- `SHA256SUMS.txt`
- `SIGNING_CERTS.txt`
- `RELEASE_NOTES.md`
- `INSTALL_CHECKLIST.md`

No publiques desde esta guia. Para permisos, instalacion y oleadas usa [`private-apk-rollout.md`](private-apk-rollout.md).

## 6. Artefacto opcional

```bash
./gradlew :app:bundleProdRelease --console=plain
```

Salida esperada:

- `app/build/outputs/bundle/prodRelease/app-prod-release.aab`

Usalo solo si luego quieres pasar por Play Console o Internal App Sharing. No es un requisito para el flujo normal de release interno.

## 7. Fallas explicitas esperadas

Los bloqueos reales que el build debe reportar de forma clara son:

- faltan variables reales de `prod`
- falta `LCX_PROD_DEVICE_AUTH_BOOTSTRAP_TOKEN`
- `LCX_PROD_USE_REAL_ZETTLE=false`
- `LCX_PROD_USE_REAL_BROTHER=false`
- `LCX_ANDROID_APPLICATION_ID` no coincide con el app id aprobado por Zettle
- falta `feature/printing/libs/BrotherPrintLibrary.aar`
- falta la keystore o alguna propiedad de signing
