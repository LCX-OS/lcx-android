# Android Release Signing y Distribucion

Esta guia deja operativo el release Android con solo dos ambientes: `dev` y `prod`.
El artefacto principal es el APK firmado generado por `:app:assembleProdRelease` para distribucion interna controlada.
Para el 0->1 no se usa Google Play, Internal App Sharing ni AAB. `bundleProdRelease` puede seguir existiendo, pero es secundario.

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
LCX_PROD_NOTIFICATIONS_BASE_URL=https://...
LCX_PROD_SUPABASE_URL=https://<project>.supabase.co
LCX_PROD_SUPABASE_ANON_KEY=...
LCX_PROD_USE_REAL_ZETTLE=true
LCX_PROD_USE_REAL_BROTHER=true

LCX_ANDROID_APPLICATION_ID=com.cleanx.app
LCX_ZETTLE_APPROVED_APPLICATION_ID=com.cleanx.app
```

Notas:

- `LCX_PROD_NOTIFICATIONS_BASE_URL` puede omitirse si usa la misma base que `LCX_PROD_API_BASE_URL`.
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

## 5. Artefactos

### Artefacto principal

```bash
./gradlew :app:assembleProdRelease --console=plain
```

Salida esperada:

- `app/build/outputs/apk/prod/release/app-prod-release.apk`

Este es el canal default para este repo: APK firmado y distribuido por un medio interno controlado como MDM, drive corporativo o enlace privado.

Para empaquetarlo para Drive privado:

```bash
scripts/release/package-private-apk.sh
```

Salida esperada:

- `build/release-private/v1.0.0+100/app-prod-release.apk`
- `build/release-private/v1.0.0+100/SHA256SUMS.txt`
- `build/release-private/v1.0.0+100/SIGNING_CERTS.txt`
- `build/release-private/v1.0.0+100/RELEASE_NOTES.md`
- `build/release-private/v1.0.0+100/INSTALL_CHECKLIST.md`

Sube el contenido de esa carpeta a Drive privado en `LCX Android Releases/v1.0.0+100/`.

### Artefacto opcional

```bash
./gradlew :app:bundleProdRelease --console=plain
```

Salida esperada:

- `app/build/outputs/bundle/prodRelease/app-prod-release.aab`

Usalo solo si luego quieres pasar por Play Console o Internal App Sharing. No es un requisito para el flujo normal de release interno.

## 6. Fallas explicitas esperadas

Los bloqueos reales que el build debe reportar de forma clara son:

- faltan variables reales de `prod`
- `LCX_PROD_USE_REAL_ZETTLE=false`
- `LCX_PROD_USE_REAL_BROTHER=false`
- `LCX_ANDROID_APPLICATION_ID` no coincide con el app id aprobado por Zettle
- falta `feature/printing/libs/BrotherPrintLibrary.aar`
- falta la keystore o alguna propiedad de signing
