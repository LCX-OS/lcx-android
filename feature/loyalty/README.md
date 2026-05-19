# feature:loyalty

Android data-layer module for Loyalty API consumption via Retrofit.

Este modulo concentra contratos de red, DTOs y `LoyaltyRepository`. No define navegacion ni pantallas; otras features consumen el repositorio por DI.

Estado 2026-05-13: loyalty usa el contrato canonico de `lcx-platform` en `/v1/loyalty/*` mediante el Retrofit calificado con `PLATFORM_BASE_URL`. La PWA conserva rutas `/api/loyalty/*` solo como compatibilidad legacy y no es dependencia de Android para esta superficie.

Nota 2026-05-19: `createAccount` acepta `displayName` sin `customerId`/`loyaltyId`; `lcx-platform` genera el ID `CLNX-*` para alta operativa desde mostrador.

## Public surface

- `LoyaltyRepository`
- modelos y mapeos de loyalty en este modulo
- integracion HTTP hacia los endpoints listados abajo

## Endpoints wired

- `GET /v1/loyalty/accounts`
- `POST /v1/loyalty/accounts`
- `GET /v1/loyalty/accounts/{id}`
- `POST /v1/loyalty/events/earn`
- `POST /v1/loyalty/events/redeem`
- `GET /v1/loyalty/rewards`
- `POST /v1/loyalty/wallet/issue`
- `POST /v1/loyalty/wallet/resync`
