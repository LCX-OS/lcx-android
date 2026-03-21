# feature:loyalty

Android data-layer module for Loyalty API consumption via Retrofit.

Este modulo concentra contratos de red, DTOs y `LoyaltyRepository`. No define navegacion ni pantallas; otras features consumen el repositorio por DI.

## Public surface

- `LoyaltyRepository`
- modelos y mapeos de loyalty en este modulo
- integracion HTTP hacia los endpoints listados abajo

## Endpoints wired

- `POST /api/loyalty/accounts`
- `GET /api/loyalty/accounts/{id}`
- `POST /api/loyalty/events/earn`
- `POST /api/loyalty/events/redeem`
- `POST /api/loyalty/wallet/issue`
- `POST /api/loyalty/wallet/resync`
