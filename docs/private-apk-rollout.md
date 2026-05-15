# Private APK Rollout 0->1

Decision actual: instalar APK privado firmado desde Drive corporativo. Para el 0->1 no usamos Google Play, Internal App Sharing ni AAB.

No uses APKs debug ni artefactos sueltos de `build/outputs`. El paquete operativo sale de [`android-release.md`](android-release.md):

```text
build/release-private/v1.0.0+100/
```

Sube esa carpeta completa a Drive privado como:

```text
LCX Android Releases/v1.0.0+100/
```

Permisos:

- Solo cuentas autorizadas.
- Sin enlace publico abierto.
- Compartir `SHA256SUMS.txt` junto al APK.
- Conservar `SIGNING_CERTS.txt` como evidencia de la firma usada.

Validacion pre-release:

```bash
./gradlew :app:verifyProdConfig :app:verifyReleaseSigning --console=plain
./gradlew test --console=plain
```

Si el release incluye Android operativo con hardware, tambien corre el gate por ADB antes de subir el APK:

```bash
scripts/qa/android-hardware-e2e.sh --serial 49281FDAQ0011J --allow-real-charge --run-ticket-hardware-path
```

Evidencia fisica mas reciente: `docs/evidence/20260514/android-hardware-release-gate.md`.

Validacion loyalty opcional, recomendada si el release toca `/v1/loyalty/*`:

```bash
LCX_PLATFORM_BASE_URL="$LCX_PROD_PLATFORM_BASE_URL" \
LCX_PLATFORM_BEARER_TOKEN="$LCX_PLATFORM_BEARER_TOKEN" \
scripts/qa/loyalty-platform-smoke.sh
```

## Preparacion del telefono

1. Confirmar que el telefono pertenece a la sucursal objetivo.
2. Cerrar operaciones abiertas o terminar el corte antes del cambio.
3. Si existe un build debug o viejo de `com.cleanx.app`, desinstalarlo.
4. Tener a la mano usuario real de sucursal y acceso al flujo PWA/manual como rollback.

## Instalacion por dispositivo

1. Abrir Drive o Chrome con cuenta autorizada.
2. Descargar `app-prod-release.apk` desde `LCX Android Releases/v1.0.0+100/`.
3. Permitir instalacion desde esa app solo para esta instalacion.
4. Instalar el APK.
5. Revocar el permiso de instalacion desde fuentes desconocidas si Android lo deja activo.
6. Abrir LCX e iniciar sesion con cuenta real de sucursal.

## Smoke por sucursal

Valida esto antes de pasar a la siguiente oleada:

- Login con usuario real.
- Crear ticket real o ticket de prueba operacional acordado.
- Pago Zettle exitoso.
- Cancelacion o falla controlada de pago.
- Impresion Brother exitosa.
- Retry o skip ante fallo de impresion.
- Cerrar/reabrir app y confirmar que la sesion/operacion sigue consistente.
- Confirmar en backend que ticket, pago e impresion quedan conciliables.
- Confirmar loyalty contra `lcx-platform`: rewards/listado responden por `/v1/loyalty/*`; si hay cuenta de prueba, correr `scripts/qa/loyalty-platform-smoke.sh` con `LCX_LOYALTY_SMOKE_ACCOUNT_ID`.

## Rollout

- Oleada 1: primera sucursal representativa.
- Oleada 2: siguiente grupo si no hubo P0/P1.
- Oleada 3: sucursales restantes.

Rollback inicial: volver temporalmente a PWA/proceso manual. No hay downgrade limpio si no existe un APK previo firmado con la misma keystore.
