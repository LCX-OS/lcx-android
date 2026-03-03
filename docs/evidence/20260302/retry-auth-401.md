# Retry Evidence - Auth 401 Blocker

Fecha: 2026-03-02 19:30 CST  
Dispositivo: Pixel 9 (`com.cleanx.lcx.dev`)  
Filtro: `TXN|HTTP|TICKET|PAYMENT|PRINT|Correlation`

## Evidencia de error

```text
03-02 19:30:25.778 D HTTP    : [51356494-2991-4c11-b2cb-1b8bbd5704f8] POST http://127.0.0.1:3000/api/tickets
03-02 19:30:25.782 I okhttp.OkHttpClient: X-Correlation-Id: 51356494-2991-4c11-b2cb-1b8bbd5704f8
03-02 19:30:25.834 I okhttp.OkHttpClient: <-- 401 Unauthorized http://127.0.0.1:3000/api/tickets
03-02 19:30:25.835 I okhttp.OkHttpClient: {"error":"No autenticado.","code":"NOT_AUTHENTICATED"}
03-02 19:30:25.835 D HTTP    : [51356494-2991-4c11-b2cb-1b8bbd5704f8] 401 http://127.0.0.1:3000/api/tickets

03-02 19:30:27.251 D HTTP    : [807c34a8-d022-45f9-832a-0358132f50c7] POST http://127.0.0.1:3000/api/tickets
03-02 19:30:27.282 I okhttp.OkHttpClient: <-- 401 Unauthorized http://127.0.0.1:3000/api/tickets
03-02 19:30:27.283 I okhttp.OkHttpClient: {"error":"No autenticado.","code":"NOT_AUTHENTICATED"}
03-02 19:30:27.283 D HTTP    : [807c34a8-d022-45f9-832a-0358132f50c7] 401 http://127.0.0.1:3000/api/tickets
```

## Correlación backend (sin filas por corte en auth)

Comandos:

```bash
cd /Users/diegolden/Code/LCX/v0-lcx-pwa
scripts/qa/correlation-audit-proof.sh 51356494-2991-4c11-b2cb-1b8bbd5704f8
scripts/qa/correlation-audit-proof.sh 807c34a8-d022-45f9-832a-0358132f50c7
```

Salida:

```text
(no matching audit_logs rows found)
```

## Acción aplicada

- Se agregó ruta explícita de re-login en UI: botón `Salir` en `TicketList`.
- `Salir` limpia sesión en `SessionManager` y navega a `Login`.
