# Private APK Rollout 0->1

Este flujo es para distribuir Android sin Google Play. El artefacto oficial es el APK firmado `prodRelease`; no uses APKs debug ni artefactos viejos de `build/outputs`.

## Release package

1. Genera y valida el release:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:verifyProdConfig :app:verifyReleaseSigning --console=plain
./gradlew test --console=plain
./gradlew :app:assembleProdRelease --console=plain
scripts/release/package-private-apk.sh
```

2. Sube a Drive privado el contenido de:

```text
build/release-private/v1.0.0+100/
```

3. La carpeta en Drive debe llamarse:

```text
LCX Android Releases/v1.0.0+100/
```

4. Permisos:

- Solo cuentas autorizadas.
- Sin enlace publico abierto.
- Compartir `SHA256SUMS.txt` junto al APK.
- Conservar `SIGNING_CERTS.txt` como evidencia de la firma usada.

## Instalacion

1. Si el telefono tiene un build debug con el mismo package, desinstalalo antes de instalar el release firmado.
2. Descarga el APK desde Drive.
3. Permite instalacion desde Drive o Chrome solo para esta instalacion.
4. Instala el APK.
5. Revoca el permiso de instalacion desde fuentes desconocidas si Android lo deja activo.
6. Abre la app e inicia sesion con la cuenta real de la sucursal.

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

## Rollout

- Oleada 1: primera sucursal representativa.
- Oleada 2: siguiente grupo si no hubo P0/P1.
- Oleada 3: sucursales restantes.

Rollback inicial: volver temporalmente a PWA/proceso manual. No hay downgrade limpio si no existe un APK previo firmado con la misma keystore.
