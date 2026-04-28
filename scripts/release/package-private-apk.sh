#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

VERSION_NAME="1.0.0"
VERSION_CODE="100"
PACKAGE_DIR="$ANDROID_ROOT/build/release-private/v${VERSION_NAME}+${VERSION_CODE}"
APK_SOURCE="$ANDROID_ROOT/app/build/outputs/apk/prod/release/app-prod-release.apk"
APK_DEST="$PACKAGE_DIR/app-prod-release.apk"
APKSIGNER="${APKSIGNER:-}"

if [[ ! -f "$APK_SOURCE" ]]; then
  echo "ERROR: APK not found: $APK_SOURCE"
  echo "Run ./gradlew :app:assembleProdRelease --console=plain first."
  exit 1
fi

if [[ -z "$APKSIGNER" ]]; then
  APKSIGNER="$(find "$HOME/Library/Android/sdk/build-tools" -maxdepth 2 -name apksigner -type f 2>/dev/null | sort | tail -1 || true)"
fi

if [[ -z "$APKSIGNER" || ! -x "$APKSIGNER" ]]; then
  echo "ERROR: apksigner not found. Set APKSIGNER=/path/to/apksigner."
  exit 1
fi

CERTS_OUTPUT="$("$APKSIGNER" verify --print-certs "$APK_SOURCE")"
if echo "$CERTS_OUTPUT" | grep -q "CN=Android Debug"; then
  echo "ERROR: refusing to package APK signed with Android Debug certificate."
  echo "Regenerate prodRelease with LCX release signing configured."
  exit 1
fi

rm -rf "$PACKAGE_DIR"
mkdir -p "$PACKAGE_DIR"
cp "$APK_SOURCE" "$APK_DEST"
printf "%s\n" "$CERTS_OUTPUT" > "$PACKAGE_DIR/SIGNING_CERTS.txt"

(
  cd "$PACKAGE_DIR"
  shasum -a 256 app-prod-release.apk > SHA256SUMS.txt
)

cat > "$PACKAGE_DIR/RELEASE_NOTES.md" <<'EOF'
# LCX Android v1.0.0+100

Canal: APK privado firmado, sin Google Play.

## Contenido

- Build productivo `prodRelease`.
- `applicationId`: `com.cleanx.app`.
- Zettle real habilitado.
- Brother real habilitado.
- Cleartext traffic deshabilitado en `prod`.
- Backup Android deshabilitado en `prod`.

## Artefactos

- `app-prod-release.apk`
- `SHA256SUMS.txt`
- `SIGNING_CERTS.txt`
- `INSTALL_CHECKLIST.md`
EOF

cat > "$PACKAGE_DIR/INSTALL_CHECKLIST.md" <<'EOF'
# Install Checklist

1. Confirmar que este paquete viene de `LCX Android Releases/v1.0.0+100/`.
2. Si existe un build debug instalado con el mismo package, desinstalarlo.
3. Descargar `app-prod-release.apk` desde Drive privado.
4. Permitir instalacion desde Drive/Chrome solo durante la instalacion.
5. Instalar el APK.
6. Revocar permiso de instalacion desde fuentes desconocidas si quedo activo.
7. Iniciar sesion con cuenta real de sucursal.
8. Validar Zettle, Brother y primer ticket real o prueba operacional acordada.
9. Reportar version instalada: `v1.0.0+100`.
EOF

echo "Private APK package ready:"
echo "$PACKAGE_DIR"
