# Route Porting Backlog (Subagents) -- 2026-03-03

## Objetivo
Acelerar el port de rutas del PWA a Android con cambios no interferentes: primero navegación y shells por módulo, luego wiring incremental de features.

## Estado base
- Repo: `/Users/diegolden/Code/LCX/lcx-android`
- Branch base sugerida: `main`
- Compila al cierre de esta mise-en-place:
  - `./gradlew :feature:cash:test`
  - `./gradlew :app:assembleDevDebug`

## Regla de oro para subagentes
Cada subagente debe tocar un scope mínimo y exclusivo (sin editar archivos compartidos fuera de su ownership), y entregar:
1. commit único,
2. evidencia de compilación/test,
3. nota de rutas agregadas.

## Wave sugerido (rutas primero)

### R1 -- Navigation Contract Expansion (P0)
**Scope**
- Expandir `Screen` con rutas typed para módulos operador faltantes:
  - `Sales` (`/operador/ventas`)
  - `IncidentsNew`, `IncidentsHistory`
  - `ShiftsControl`, `ShiftsHistory`, `ShiftsSchedule`, `ShiftsReports`
  - `DamagedClothingNew`, `DamagedClothingHistory`
  - `SuppliesInventory`, `SuppliesLabels`, `SuppliesReports`, `SuppliesBrotherDebug`
  - `Vacations`
  - `CalendarMonthly`, `CalendarEvents`
  - `BestPractices`, `Help`
- Agregar graph markers por dominio en `Screen`.

**Ownership files**
- `core/src/main/java/com/cleanx/lcx/core/navigation/Screen.kt`

**DoD**
- No lógica de UI, solo contrato de rutas typed.
- `:app:assembleDevDebug` verde.

---

### R2 -- More Hub Real Navigation (P0)
**Scope**
- Reemplazar placeholder de `MoreScreen` por un hub real con lista de accesos.
- Cada acceso navega a la ruta typed correspondiente (aunque sea pantalla shell).
- Mantener "Caja" en bottom tab; en More solo accesos restantes.

**Ownership files**
- `app/src/main/java/com/cleanx/lcx/ui/placeholder/MoreScreen.kt` (o nuevo paquete `feature/more`)
- `app/src/main/java/com/cleanx/lcx/ui/shell/MainScaffold.kt`

**DoD**
- Desde app se puede abrir cada ruta de operador desde More.
- Sin crashes de navegación.

---

### R3 -- Operator Route Shells (P0)
**Scope**
- Crear shells Compose mínimas para rutas operador faltantes (título, descripción, CTA principal).
- No integrar APIs todavía.
- Agrupar por módulo (`feature/sales`, `feature/incidents`, etc.) o `feature/ops-shells` según convenga.

**Ownership files**
- Nuevos archivos de UI por ruta faltante.
- Sin cambios al contrato de rutas (depende de R1 ya mergeado).

**DoD**
- Todas las rutas P0/P1 de operador abren pantalla funcional (aunque placeholder estructurado).
- `:app:assembleDevDebug` verde.

---

### R4 -- Ticket Route Presets (P0)
**Scope**
- Portear rutas equivalentes de encargos listados:
  - Activos, Listos, Completados, Todos.
- Reusar `TicketListScreen` con preset de filtros por ruta.
- Mantener compatibilidad con la ruta actual de tickets.

**Ownership files**
- `feature/tickets` (list VM/screen)
- `MainScaffold` (wiring de rutas)

**DoD**
- Navegación entre presets sin duplicar pantalla.
- Filtros aplicados por route argument/typed route.

---

### R5 -- Role Guards in Navigation (P1)
**Scope**
- Introducir guard de navegación por rol (employee/manager/superadmin) para rutas sensibles.
- Fallback UX: mensaje claro + regreso seguro.

**Ownership files**
- `core/navigation` + `app/shell`
- Leer rol desde sesión/perfil existente.

**DoD**
- Rutas manager/admin no visibles ni navegables por employee.
- Tests unitarios básicos de matrix rol-ruta.

---

### R6 -- Route Registry + Parity Tracking (P1)
**Scope**
- Crear registro único `PwaRoute -> Android Screen` para evitar drift.
- Actualizar matriz de paridad automáticamente (script simple o markdown asistido).

**Ownership files**
- `docs/porting` + opcional `scripts/qa`

**DoD**
- Archivo único con mapeo verificable.
- Se puede auditar qué rutas ya están navegables.

## Orden de ejecución recomendado
1. R1
2. R2
3. R3 y R4 en paralelo
4. R5
5. R6

## Prompt listo para el agente que orquesta subagentes

```text
Eres Tech Lead Orchestrator en /Users/diegolden/Code/LCX/lcx-android.

Objetivo de esta corrida: portear rutas del PWA a Android (navegación + shells), sin mezclar todavía lógica compleja de negocio.

Reglas:
1) Trabaja con worktrees aislados por subagente.
2) Un commit por subagente, mensaje claro por scope.
3) No tocar impresión/pagos/caja salvo wiring de navegación.
4) Cada subagente debe devolver: SHA, archivos, DoD, blockers, comandos ejecutados.
5) Merge final limpio en `main` con cherry-pick ordenado y resolución explícita de conflictos.

Lanza 4 subagentes en paralelo:

A) Nav Contract Agent (R1)
- Expandir Screen.kt con rutas typed de módulos operador faltantes y graph markers.
- DoD: app assemble green.

B) More Hub Agent (R2)
- Reemplazar More placeholder por hub navegable a rutas typed nuevas.
- DoD: navegación funcional sin crashes.

C) Operator Shells Agent (R3)
- Crear pantallas shell Compose para rutas operador faltantes (sin API).
- DoD: rutas abren UI mínima consistente.

D) Ticket Presets Agent (R4)
- Crear rutas Activos/Listos/Completados/Todos reutilizando TicketListScreen con presets.
- DoD: filtros por ruta funcionando.

Después de completarse:
- Integrar en `main` con cherry-pick.
- Ejecutar:
  - ./gradlew :app:assembleDevDebug
  - ./gradlew :feature:tickets:test (si aplica)
- Generar reporte final en docs/porting/route-wave-report-YYYYMMDD.md con:
  - tabla por subagente,
  - commits integrados,
  - cobertura de rutas PWA->Android,
  - riesgos y próximos pasos.
```
