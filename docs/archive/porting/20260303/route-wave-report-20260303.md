# Route Porting Wave Report — 2026-03-03

## Summary
Navigation contract expansion + operator route shells + ticket presets.
All routes navigable from the "Mas" bottom tab. Build green.

## Commits Integrated

| Order | SHA | Scope | Description |
|-------|-----|-------|-------------|
| 1 | `fa091e9` | R1 | Expand Screen.kt with 19 operator routes + 6 domain graph markers |
| 2 | `68fff0f` | R3 | Add 19 structured shell screens in OperatorShellScreens.kt |
| 3 | `1008cd5` | R3 | Fix: use AutoMirrored.Filled.Label (deprecation) |
| 4 | `4c4e0fb` | R2 | Replace More placeholder with navigable operator hub |
| 5 | `1e7635b` | R4 | Add preset-filtered ticket list routes |
| 6 | `3a7941d` | Int | Wire R3 shells into MainScaffold (replace inline placeholders) |

## Files Changed (9 files, +1151 / -15)

| File | Change |
|------|--------|
| `core/.../navigation/Screen.kt` | +45 — 19 operator routes, TicketPreset, 6 graph markers |
| `app/.../navigation/BottomNavItem.kt` | +12 — MAS tab added (6th bottom nav) |
| `app/.../ui/more/MoreScreen.kt` | +181 — New file: hub with 8 sections, 18 operator accesses |
| `app/.../ui/ops/OperatorShellScreens.kt` | +336 — New file: reusable shell + 19 named composables |
| `app/.../ui/placeholder/PlaceholderScreens.kt` | -10 — Removed old MoreScreen placeholder |
| `app/.../ui/shell/MainScaffold.kt` | +79/-15 — Wired More hub, 18 operator routes, ticket presets |
| `feature/tickets/.../TicketPresetScreen.kt` | +177 — New file: filtered ticket view by preset |
| `feature/tickets/.../TicketPresetsHub.kt` | +114 — New file: hub for preset selection |

## PWA -> Android Route Coverage

| PWA Route | Android Screen | Status |
|-----------|---------------|--------|
| `/operador/ventas` | `Screen.Sales` | Shell |
| `/operador/incidencias/nueva` | `Screen.IncidentsNew` | Shell |
| `/operador/incidencias/historial` | `Screen.IncidentsHistory` | Shell |
| `/operador/turnos/control` | `Screen.ShiftsControl` | Shell |
| `/operador/turnos/historial` | `Screen.ShiftsHistory` | Shell |
| `/operador/turnos/horarios` | `Screen.ShiftsSchedule` | Shell |
| `/operador/turnos/reportes` | `Screen.ShiftsReports` | Shell |
| `/operador/ropa-danada/nueva` | `Screen.DamagedClothingNew` | Shell |
| `/operador/ropa-danada/historial` | `Screen.DamagedClothingHistory` | Shell |
| `/operador/insumos/inventario` | `Screen.SuppliesInventory` | Shell |
| `/operador/insumos/etiquetas` | `Screen.SuppliesLabels` | Shell |
| `/operador/insumos/reportes` | `Screen.SuppliesReports` | Shell |
| `/operador/insumos/brother-debug` | `Screen.SuppliesBrotherDebug` | Shell |
| `/operador/vacaciones` | `Screen.Vacations` | Shell |
| `/operador/calendario/mensual` | `Screen.CalendarMonthly` | Shell |
| `/operador/calendario/eventos` | `Screen.CalendarEvents` | Shell |
| `/info/mejores-practicas` | `Screen.BestPractices` | Shell |
| `/info/ayuda` | `Screen.Help` | Shell |
| `/tickets` | `Screen.TicketList` | Full |
| `/tickets/:id` | `Screen.TicketDetail` | Full |
| `/tickets/crear` | `Screen.CreateTicket` | Full |
| `/tickets/activos` | `Screen.TicketPreset("active")` | Filter |
| `/tickets/listos` | `Screen.TicketPreset("ready")` | Filter |
| `/tickets/completados` | `Screen.TicketPreset("completed")` | Filter |
| `/tickets/todos` | `Screen.TicketPreset("all")` | Filter |
| `/caja` | `Screen.Cash` | Full |
| `/pagos/:id` | `Screen.Charge` | Full |
| `/imprimir/:id` | `Screen.Print` | Full |

## Build Verification

```
./gradlew :app:assembleDevDebug
BUILD SUCCESSFUL in 1s
180 actionable tasks: 6 executed, 174 up-to-date
```

## Risks & Next Steps

### Risks
- 6 bottom nav tabs may be crowded on small screens — consider dynamic visibility or collapsing
- Ticket preset filtering is client-side only (no server filter endpoint yet)
- Shell screens have no API integration — users see "Proximamente" badge

### Next Steps (R5/R6)
- **R5**: Role guards — restrict routes by UserRole (employee/manager/superadmin)
- **R6**: Route registry + parity tracking script
- **Feature wiring**: Incrementally replace shells with real feature implementations
- **TicketPresetsHub**: Wire into More or Tickets tab for user access to preset views
