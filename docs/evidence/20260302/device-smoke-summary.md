# Device Smoke Summary (Physical Pixel 9)

Fecha: 2026-03-02 19:14-19:15 CST  
Fuente: `docs/evidence/20260302/device-smoke.raw.log`

## Evidencia clave (impresión real Brother)

```text
03-02 19:14:54.102 D HTTP    : [f712a2b8-ea26-447c-a0a6-e189d10e4a2e] POST http://127.0.0.1:3000/api/tickets
03-02 19:14:54.215 D HTTP    : [f712a2b8-ea26-447c-a0a6-e189d10e4a2e] 200 http://127.0.0.1:3000/api/tickets
03-02 19:15:00.904 I PrintModule: PrintModule: using BrotherPrinterManager (useRealBrother=true)
03-02 19:15:05.997 D PRINT   : Brother discovery completed: 16 printer(s)
03-02 19:15:09.033 I PRINT   : Brother connected: type=WIFI address=192.168.100.47 name=QL-810W
03-02 19:15:10.450 I PRINT   : Brother print success: ticket=T-20260303-0004 folio=4 printer=QL-810W
```

## Correlación backend

Comando:

```bash
cd /Users/diegolden/Code/LCX/v0-lcx-pwa
scripts/qa/correlation-audit-proof.sh f712a2b8-ea26-447c-a0a6-e189d10e4a2e
```

Salida:

```text
| event_type   | timestamp               | endpoint      | correlation_id                          | payload_summary         |
| ticket_create| 2026-03-03 01:14:52.733 | /api/tickets  | f712a2b8-ea26-447c-a0a6-e189d10e4a2e    | source=encargo count=1 |
```
