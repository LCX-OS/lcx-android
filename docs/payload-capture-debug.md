# Payload Capture (Debug)

Este flujo captura payloads HTTP reales (request/response) de:

- `channel=auth` (login Supabase)
- `channel=api` (rutas Next.js API usadas por Android)

Se guarda en el dispositivo en:

- `/sdcard/Android/data/<dev applicationId>/files/payload-capture/payload-capture.jsonl`

Package default si no sobreescribes `LCX_ANDROID_APPLICATION_ID` ni `LCX_DEV_APPLICATION_ID_SUFFIX`:

- `com.cleanx.lcx.dev`

## Uso rapido

1. Instala/abre `devDebug` y ejecuta el flujo que quieras inspeccionar (login, ticket, payment, etc.).
2. Extrae el archivo:

```bash
./scripts/qa/pull-payload-captures.sh <dev applicationId>
```

Ejemplo con el default actual:

```bash
./scripts/qa/pull-payload-captures.sh com.cleanx.lcx.dev
```

3. Se guardara en:

- `docs/evidence/YYYYMMDD/payload-capture-YYYYMMDD-HHMMSS.jsonl`

## Formato por linea (JSONL)

Campos principales:

- `timestamp`
- `channel` (`auth` | `api`)
- `method`, `url`, `path`
- `correlationId`
- `requestHeaders`, `requestBody`
- `responseCode`, `responseHeaders`, `responseBody`
- `elapsedMs`
- `error` (si fallo de red/cliente)

Notas:

- Cabeceras sensibles se redactan (`authorization`, `apikey`, `cookie`, etc.).
- Payloads binarios se omiten.
- Captura solo en `debug`.
