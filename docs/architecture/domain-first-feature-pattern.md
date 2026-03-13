# Domain-First Feature Pattern

Fecha: 2026-03-13

Objetivo: reducir el acoplamiento entre UI, reglas de negocio y acceso a datos en features operativas con mucha logica.

## Estructura

- `data/`
  - repositorios, DTOs, contratos de Supabase/Retrofit y mapeos de error.
- `domain/<flow>/`
  - `State`: estado canonico del flujo.
  - `Reducer`: transiciones puras y estado derivado.
  - `UseCase`: reglas y side effects orquestados alrededor del flujo.
- `ui/`
  - `Screen`: Compose.
  - `ViewModel`: debounce, dispatch de mutaciones y coordinacion entre use cases.

## Regla practica

Si una feature necesita:

- validaciones de negocio,
- pricing o calculos,
- armado de payload,
- sincronizacion con varias fuentes,
- o estados transitorios complejos,

entonces esa logica no debe vivir directamente en Compose ni crecer dentro del `ViewModel`.

## Primer caso de referencia

`feature:tickets` ya usa este patron para `Encargos nuevo` en:

- `feature/tickets/src/main/java/com/cleanx/lcx/feature/tickets/domain/create/`

Cobertura minima esperada:

- tests puros para reducer,
- tests puros para use cases criticos,
- build del modulo / app,
- smoke manual si el flujo toca hardware o backend real.
