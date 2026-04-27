# feature:loyalty

Android data-layer module for Loyalty API consumption via Retrofit.

Este modulo concentra contratos de red, DTOs y `LoyaltyRepository`. No define navegacion ni pantallas; otras features consumen el repositorio por DI.

Estado 2026-04-27: este modulo es legacy respecto a `lcx-platform`. El contrato canonico de plataforma vive en `/v1/loyalty/*` y ya incluye wallet loyalty cards para Apple Wallet / Google Wallet, pero Android todavia no debe cambiar esta interfaz sin separar primero la base URL de plataforma.

## Public surface

- `LoyaltyRepository`
- modelos y mapeos de loyalty en este modulo
- integracion HTTP hacia los endpoints listados abajo

## Endpoints wired

Este modulo todavia consume el contrato legacy de la PWA (`/api/loyalty/*`). La superficie canonica nueva vive en `lcx-platform` como `/v1/loyalty/*`; no cambies el cliente Android a esa base hasta separar/configurar un Retrofit para plataforma o migrar el `API_BASE_URL` completo.

- `POST /api/loyalty/accounts`
- `GET /api/loyalty/accounts/{id}`
- `POST /api/loyalty/events/earn`
- `POST /api/loyalty/events/redeem`
- `POST /api/loyalty/wallet/issue`
- `POST /api/loyalty/wallet/resync`
