# Feature Parity Matrix -- PWA vs Android

**Date**: 2026-03-03
**Agent**: A7 -- QA/Parity Agent
**PWA Source**: `/Users/diegolden/Code/LCX/v0-lcx-pwa` (Next.js)
**Android Target**: `/Users/diegolden/Code/LCX/lcx-android` (Jetpack Compose)

---

## Executive Summary

| Metric | Count | % of Total |
|--------|-------|------------|
| **Total PWA features inventoried** | 58 | 100% |
| **FULL parity** | 7 | 12% |
| **PARTIAL parity** | 8 | 14% |
| **PLACEHOLDER** | 2 | 3% |
| **MISSING** | 41 | 71% |

**Bottom line**: After today's Wave 1 sprint, the Android app covers 15 of 58 features at PARTIAL or better. The core ticket-to-payment-to-print loop is solid. Water and checklist modules are substantially ported but have gaps in branch scoping and offline support. **41 features remain entirely unimplemented**, including the P0-critical cash register and self-service sales modules.

The app cannot yet support a full operator shift without PWA fallback. The two biggest blockers are: (1) cash register (required for every shift open/close), and (2) enhanced ticket creation (missing customer picker, add-ons, pickup estimate).

---

## Parity by Module

### Authentication & Session

| # | Feature | PWA Status | Android Status | Parity | Gap Description | Severity | Branch/SHA |
|---|---------|-----------|----------------|--------|-----------------|----------|------------|
| 53 | Login | LIVE | DONE | **FULL** | Email/password auth, session persistence, 401 handling all working. | -- | `main` |
| 54 | Signup | LIVE | -- | **MISSING** | Self-registration not implemented. Low priority for managed deployments where admins create accounts. | P3-LOW | -- |
| 55 | Forgot Password | LIVE | -- | **MISSING** | Email-based reset via Supabase Auth not implemented. | P3-LOW | -- |
| 56 | Reset Password | LIVE | -- | **MISSING** | Token-based password reset not implemented. | P3-LOW | -- |
| 57 | Profile | LIVE | -- | **MISSING** | View/edit own profile not implemented. Operators cannot change password or view their info. | P2-MEDIUM | -- |
| 58 | Notifications | LIVE | -- | **MISSING** | Notification center not implemented. Would require Firebase Cloud Messaging for Android push. | P3-LOW | -- |

### Navigation & Shell

| # | Feature | PWA Status | Android Status | Parity | Gap Description | Severity | Branch/SHA |
|---|---------|-----------|----------------|--------|-----------------|----------|------------|
| -- | Bottom Navigation (5 tabs) | LIVE | DONE | **FULL** | Material 3 NavigationBar with Inicio, Tickets, Agua, Checklist, Mas. State preservation via saveState/restoreState. Tab graphs properly nested. | -- | `codex/port-bottom-nav` / `79b3d36` |
| -- | Two-tier NavHost (auth + tabs) | LIVE | DONE | **FULL** | Root-level auth check routes to Login or MainScaffold. Tab NavHost nested inside scaffold. Type-safe navigation with kotlinx.serialization. | -- | `codex/port-bottom-nav` / `79b3d36` |
| -- | Role-based navigation guards | LIVE | -- | **MISSING** | PWA uses `RoleGuardByRoles` to show/hide features per role (employee/manager/superadmin). Android has no role checking whatsoever. All tabs/screens visible to all users. | P1-HIGH | -- |
| -- | "Mas" (More) menu | LIVE (full menu) | PLACEHOLDER | **PLACEHOLDER** | Shows generic placeholder icon+text. PWA "More" screen links to: Cash Register, Shifts, Incidents, Damaged Clothing, Supplies, Vacations, Calendar, Best Practices, Help. None of these are navigable. | P1-HIGH | `codex/port-bottom-nav` / `79b3d36` |

### Tickets / Encargos

| # | Feature | PWA Status | Android Status | Parity | Gap Description | Severity | Branch/SHA |
|---|---------|-----------|----------------|--------|-----------------|----------|------------|
| 8 | Encargos -- Nuevo (CreateTicket) | LIVE | PARTIAL | **PARTIAL** | Android has basic wash-fold + in-store creation with service type, customer name, weight, notes, and optional daily folio. **Missing**: (a) customer picker with search/create, (b) bedding add-ons (sabanas, cobijas, edredones), (c) inventory product add-ons with barcode scanner, (d) pickup date/time estimate, (e) special items separation toggle, (f) payment method choice at creation (pending vs paid + method selection), (g) service catalog lookup from DB (currently hardcoded service types). This is the single largest gap in existing functionality. | P0-BLOCKER | `main` |
| 9 | Encargos -- Detail (TicketDetail) | LIVE | PARTIAL | **PARTIAL** | Android shows ticket number, customer, service type, weight, status badge, payment status, notes. **Missing**: (a) quick-actions dialog (print + pay flow from detail), (b) mark-as-paid inline action, (c) add-on display (bedding, inventory items), (d) pickup estimate display, (e) status change actions (advance status from detail). | P1-HIGH | `main` |
| 10 | Encargos -- Active List | LIVE | PARTIAL | **PARTIAL** | Android TicketListScreen fetches all tickets with pull-to-refresh and search. **Missing**: filter presets for status=[received, processing] + serviceType=wash-fold. Currently shows all tickets in one unfiltered list. | P1-HIGH | `main` |
| 11 | Encargos -- Ready List | LIVE | PARTIAL | **PARTIAL** | Same TicketListScreen, no preset filter for status=ready. | P2-MEDIUM | `main` |
| 12 | Encargos -- Completed | LIVE | PARTIAL | **PARTIAL** | Same TicketListScreen, no preset filter for status=completed/delivered. | P2-MEDIUM | `main` |
| 13 | Encargos -- All | LIVE | PARTIAL | **PARTIAL** | TicketListScreen shows all tickets but lacks the explicit "Todos" tab/filter the PWA provides. Functionally close. | P2-MEDIUM | `main` |

### Payments

| # | Feature | PWA Status | Android Status | Parity | Gap Description | Severity | Branch/SHA |
|---|---------|-----------|----------------|--------|-----------------|----------|------------|
| -- | Zettle card payments | LIVE | DONE | **FULL** | ChargeScreen integrates with Zettle SDK. ZettlePaymentManager handles the full flow with error mapping, retry, and amount formatting. StubPaymentManager for testing. PaymentDiagnosticsScreen available in DEBUG builds. Payment status updates to Supabase. | -- | `main` |
| -- | Cash payment recording | LIVE | -- | **MISSING** | PWA allows marking tickets as paid-in-cash at creation or from detail. Android only supports Zettle card flow. No cash payment path exists. | P0-BLOCKER | -- |

### Printing

| # | Feature | PWA Status | Android Status | Parity | Gap Description | Severity | Branch/SHA |
|---|---------|-----------|----------------|--------|-----------------|----------|------------|
| -- | Brother label printing | LIVE | DONE | **FULL** | PrintScreen with BrotherPrinterManager, auto-connect, retry backoff, error mapping. LabelRenderer generates bitmap labels. PrinterSettingsScreen for printer configuration. PrinterPreferences for persisting settings. Tested on physical device. | -- | `main` |

### Transaction Orchestration

| # | Feature | PWA Status | Android Status | Parity | Gap Description | Severity | Branch/SHA |
|---|---------|-----------|----------------|--------|-----------------|----------|------------|
| -- | Ticket -> Payment -> Print flow | LIVE | DONE | **FULL** | TransactionOrchestrator manages the multi-phase flow with Room-based persistence (TransactionDao) for crash recovery. PendingTransactionDialog resumes interrupted transactions. Phase tracking via TransactionPhase sealed class. | -- | `main` |

### Water Module (NEW)

| # | Feature | PWA Status | Android Status | Parity | Gap Description | Severity | Branch/SHA |
|---|---------|-----------|----------------|--------|-----------------|----------|------------|
| 2 | Water Level Monitor | LIVE | DONE (with gaps) | **PARTIAL** | **Implemented**: WaterScreen with 2 tabs (Nivel Actual + Historial), WaterTankIndicator visualization, slider input with percentage/liters sync, provider selection dropdown (3 hardcoded providers), "order water" recording, history list with user names via PostgREST join, status color coding (critical/low/normal/optimal), error states, loading states, save confirmation. **Gaps**: (a) Branch scoping: `getCurrentWaterLevel()` and `getWaterLevelHistory()` accept optional branch param but the ViewModel never passes it -- all queries are unscoped, returning data from all branches. (b) `recordWaterLevel()` and `recordWaterOrder()` also never pass `recordedBy` (userId) or `branch` -- records are inserted without ownership or branch association. (c) No offline caching -- if network is unavailable, the screen shows an error with no cached fallback. (d) No audit log recording (PWA writes to `audit_logs` table on water actions). (e) The tank indicator in feature:water duplicates the one in core:ui (A4 branch) rather than importing it. | P1-HIGH | `codex/port-water-module` / `4f05fa0` |

### Checklist Module (NEW)

| # | Feature | PWA Status | Android Status | Parity | Gap Description | Severity | Branch/SHA |
|---|---------|-----------|----------------|--------|-----------------|----------|------------|
| 5 | Checklist -- Entrada | LIVE | DONE (with gaps) | **PARTIAL** | **Implemented**: ChecklistScreen with 3 tabs (Entrada/Salida/Historial). Entry tab: auto-creates today's checklist with 6 template items matching PWA (water level, cash register, cleaning, equipment check, supplies check, fire extinguishers). System-validated items for water level (entry-1) and cash register (entry-2) auto-check by querying `water_levels` and `cash_registers` tables. Manual item toggling with checkbox. Progress bar with required/total counter. Notes field. Complete button gated on required items. Status badges (pending/in-progress/completed). Category badges (cleaning/maintenance/safety/admin) with color coding. Action links ("Ir a Agua", "Ir a Caja") on system-validated items. **Gaps**: (a) Branch scoping: no branch filtering on any queries -- data leaks across branches. (b) `completedBy` (userId) is never passed to `completeChecklist()` or `updateChecklistItem()` -- records lack user attribution. (c) No offline support. (d) Uses raw OkHttp rather than the SupabaseTableClient from the A5 contract bridge branch (inconsistent data layer). (e) The `hasCashRegisterToday()` queries `cash_registers` table but the PWA uses `cash_movements` -- possible table name mismatch that would cause auto-validation to always return false. (f) No real-time update subscription (PWA may poll or use Supabase realtime). | P1-HIGH | `codex/port-ops-module-2` / `c775dca` |
| 6 | Checklist -- Salida | LIVE | DONE (with gaps) | **PARTIAL** | Exit tab with 5 template items matching PWA (cash closing, general cleaning, equipment shutdown, lock check, incident report). Same completion flow as entrada. **Same gaps as entry** plus: (a) Exit checklist does not auto-validate cash closing (entry-1 style system validation for cash closing is not implemented for exit). (b) No enforcement that entry checklist must be completed before exit can start (PWA may enforce this ordering). | P1-HIGH | `codex/port-ops-module-2` / `c775dca` |
| 7 | Checklist -- History | LIVE | DONE (with gaps) | **PARTIAL** | History tab fetches completed checklists ordered by date desc, limit 30. Shows type badge (Entrada/Salida), date, completion time, notes. **Gaps**: (a) No date range filter (PWA has date range picker). (b) No stats summary (PWA shows completion rate, average items). (c) No branch scoping. (d) Does not show individual item details per checklist -- only the header. | P2-MEDIUM | `codex/port-ops-module-2` / `c775dca` |

### Dashboard

| # | Feature | PWA Status | Android Status | Parity | Gap Description | Severity | Branch/SHA |
|---|---------|-----------|----------------|--------|-----------------|----------|------------|
| 1 | Dashboard | LIVE | PLACEHOLDER | **PLACEHOLDER** | Shows a centered icon with "Inicio" and "Panel principal (proximamente)" text. No data, no KPIs, no quick actions. PWA dashboard aggregates: shift status, pending tickets count, water level alert, supply needs, daily revenue, recent activity. The dashboard is the landing screen after login and currently provides zero operational value. | P1-HIGH | `codex/port-bottom-nav` / `79b3d36` |

### Cash Register

| # | Feature | PWA Status | Android Status | Parity | Gap Description | Severity | Branch/SHA |
|---|---------|-----------|----------------|--------|-----------------|----------|------------|
| 3 | Cash Register -- Register | LIVE | -- | **MISSING** | Entirely missing. PWA provides denomination-by-denomination cash counting for MXN (bills $20-$1000, coins $0.50-$20), auto-detect opening vs closing, discrepancy preview, day summary hero card. This is required for EVERY shift (opening and closing). Without it, operators cannot complete entry or exit checklists (the cash register system-validated items will never auto-validate). | P0-BLOCKER | -- |
| 4 | Cash Register -- History | LIVE | -- | **MISSING** | Entirely missing. Filterable movement list with summary cards and CSV export. | P1-HIGH | -- |

### Self-Service Sales (Ventas)

| # | Feature | PWA Status | Android Status | Parity | Gap Description | Severity | Branch/SHA |
|---|---------|-----------|----------------|--------|-----------------|----------|------------|
| 14 | Ventas | LIVE | -- | **MISSING** | Entirely missing. Point-of-sale for walk-in customers: equipment usage (washers/dryers) + product sales, cart with quantity controls, customer picker (including anonymous), inventory search/scan, creates multiple tickets per transaction. Major P0 feature for daily revenue. | P0-BLOCKER | -- |

### Incidents

| # | Feature | PWA Status | Android Status | Parity | Gap Description | Severity | Branch/SHA |
|---|---------|-----------|----------------|--------|-----------------|----------|------------|
| 15 | Incidents -- New | LIVE | -- | **MISSING** | Form with type/severity/description, photo capture (up to 5), audio recording with evidence upload to Supabase Storage. Requires Android CAMERA + RECORD_AUDIO permissions. | P1-HIGH | -- |
| 16 | Incidents -- History | LIVE | -- | **MISSING** | List view with filters. | P2-MEDIUM | -- |

### Shifts (Turnos)

| # | Feature | PWA Status | Android Status | Parity | Gap Description | Severity | Branch/SHA |
|---|---------|-----------|----------------|--------|-----------------|----------|------------|
| 17 | Turnos -- Control | LIVE | -- | **MISSING** | Clock in/out with live timer, daily progress, overtime alerts, active staff. | P1-HIGH | -- |
| 18 | Turnos -- History | LIVE | -- | **MISSING** | Past shifts log. | P2-MEDIUM | -- |
| 19 | Turnos -- Schedule | LIVE | -- | **MISSING** | Weekly schedule view. | P2-MEDIUM | -- |
| 20 | Turnos -- Reports | LIVE | -- | **MISSING** | Shift reports. | P2-MEDIUM | -- |

### Damaged Clothing

| # | Feature | PWA Status | Android Status | Parity | Gap Description | Severity | Branch/SHA |
|---|---------|-----------|----------------|--------|-----------------|----------|------------|
| 21 | Damaged Clothing -- New | LIVE | -- | **MISSING** | Pre-existing damage documentation with photo + audio + speech-to-text (es-MX). Requires SpeechRecognizer on Android. | P1-HIGH | -- |
| 22 | Damaged Clothing -- History | LIVE | -- | **MISSING** | History of damage records. | P2-MEDIUM | -- |

### Supplies (Suministros)

| # | Feature | PWA Status | Android Status | Parity | Gap Description | Severity | Branch/SHA |
|---|---------|-----------|----------------|--------|-----------------|----------|------------|
| 23 | Supplies -- Inventory | LIVE | -- | **MISSING** | Supply list with search, stock status, add new, stock adjustment. | P1-HIGH | -- |
| 24 | Supplies -- Labels | LIVE | -- | **MISSING** | Label template generation for supplies. Brother printer integration available in Android already. | P2-MEDIUM | -- |
| 25 | Supplies -- Reports | LIVE | -- | **MISSING** | Supply usage reports. | P2-MEDIUM | -- |
| 26 | Supplies -- Brother Debug | LIVE | -- | **MISSING** | Printer debugging page. Android already has PaymentDiagnosticsScreen; could add printer diag similarly. | P3-LOW | -- |

### Vacaciones

| # | Feature | PWA Status | Android Status | Parity | Gap Description | Severity | Branch/SHA |
|---|---------|-----------|----------------|--------|-----------------|----------|------------|
| 27 | Vacaciones | LIVE | -- | **MISSING** | Calendar views, date range selection, request/approval flows. Complex UI. | P2-MEDIUM | -- |

### Calendario

| # | Feature | PWA Status | Android Status | Parity | Gap Description | Severity | Branch/SHA |
|---|---------|-----------|----------------|--------|-----------------|----------|------------|
| 28 | Calendario -- Monthly | LIVE | -- | **MISSING** | Monthly calendar grid with events. | P2-MEDIUM | -- |
| 29 | Calendario -- Events | LIVE | -- | **MISSING** | Event listing. | P2-MEDIUM | -- |

### Practicas & Ayuda

| # | Feature | PWA Status | Android Status | Parity | Gap Description | Severity | Branch/SHA |
|---|---------|-----------|----------------|--------|-----------------|----------|------------|
| 30 | Practicas | LIVE | -- | **MISSING** | Best practices library. | P2-MEDIUM | -- |
| 31 | Ayuda | LIVE | -- | **MISSING** | Help center with FAQ + support tickets. | P2-MEDIUM | -- |

### Admin Modules

| # | Feature | PWA Status | Android Status | Parity | Gap Description | Severity | Branch/SHA |
|---|---------|-----------|----------------|--------|-----------------|----------|------------|
| 32 | Precios -- Servicios | LIVE | -- | **MISSING** | Service catalog CRUD. | P1-HIGH | -- |
| 33 | Precios -- Articulos | LIVE | -- | **MISSING** | Add-on/inventory pricing. | P1-HIGH | -- |
| 34 | Precios -- Paquetes | LIVE | -- | **MISSING** | Bundle pricing. | P2-MEDIUM | -- |
| 35 | Precios -- Promociones | LIVE | -- | **MISSING** | Promotions/discounts. | P2-MEDIUM | -- |
| 36 | Precios -- Historial | LIVE | -- | **MISSING** | Price change audit log. | P2-MEDIUM | -- |
| 37 | Usuarios | LIVE | -- | **MISSING** | User directory + CRUD. | P1-HIGH | -- |
| 38 | Usuarios -- Detail | LIVE | -- | **MISSING** | Individual user profile editing. | P1-HIGH | -- |

### Management (Gerencia) Modules

| # | Feature | PWA Status | Android Status | Parity | Gap Description | Severity | Branch/SHA |
|---|---------|-----------|----------------|--------|-----------------|----------|------------|
| 39 | Estadisticas -- Ventas | LIVE | -- | **MISSING** | Sales analytics. | P2-MEDIUM | -- |
| 40 | Estadisticas -- Empleados | LIVE | -- | **MISSING** | Employee performance stats. | P2-MEDIUM | -- |
| 41 | Estadisticas -- Equipos | LIVE | -- | **MISSING** | Equipment health stats. | P2-MEDIUM | -- |
| 42 | Estadisticas -- Productividad | LIVE | -- | **MISSING** | Productivity analytics. | P2-MEDIUM | -- |
| 43 | Inventario | LIVE | -- | **MISSING** | Full inventory management. | P2-MEDIUM | -- |
| 44 | Mantenimiento -- Dashboard | LIVE | -- | **MISSING** | Maintenance overview. | P2-MEDIUM | -- |
| 45 | Mantenimiento -- Programado | LIVE | -- | **MISSING** | Scheduled maintenance. | P2-MEDIUM | -- |
| 46 | Mantenimiento -- Recurrente | LIVE | -- | **MISSING** | Recurring task templates. | P2-MEDIUM | -- |
| 47 | Reportes -- Ventas | LIVE | -- | **MISSING** | Sales reporting. | P2-MEDIUM | -- |
| 48 | Reportes -- Empleados | LIVE | -- | **MISSING** | Employee reporting. | P2-MEDIUM | -- |
| 49 | Reportes -- Inventario | LIVE | -- | **MISSING** | Inventory reporting. | P2-MEDIUM | -- |
| 50 | Reportes -- Operaciones | LIVE | -- | **MISSING** | Operations reporting. | P2-MEDIUM | -- |
| 51 | Reportes -- Productividad | LIVE | -- | **MISSING** | Productivity reporting. | P2-MEDIUM | -- |
| 52 | Reportes -- Incidentes | LIVE | -- | **MISSING** | Incident reporting. | P2-MEDIUM | -- |

### Infrastructure (NEW today, not feature-counted)

| Component | Android Status | Notes | Branch/SHA |
|-----------|----------------|-------|------------|
| Supabase Kotlin SDK integration | DONE | SupabaseTableClient with typed CRUD, Result<T> error handling, SupabaseError sealed hierarchy (Unauthorized/BadRequest/NotFound/ServerError/NetworkError/Unknown). Auth token pass-through from SessionManager. | `codex/port-contract-bridge` / `694de2f` |
| Data models (WaterLevel, Checklist, ChecklistItem) | DONE | Core module models with kotlinx.serialization. | `codex/port-contract-bridge` / `694de2f` |
| 8 shared UI components | DONE | LcxProgressIndicator, WaterTankIndicator, LcxSlider, ChecklistItem, CategoryBadge, QuickActionCard, SectionHeader, StatusBanner. All with Material 3 theming and previews. | `codex/port-ui-foundation` / `fb816a7` |

---

## Gap Analysis by Severity

### P0 Blockers (4 gaps)

These must be resolved before any field testing. Without them, an operator cannot complete a shift.

| Gap | Feature | Impact | Effort Estimate |
|-----|---------|--------|-----------------|
| **Cash Register -- Register** (#3) | MISSING | Operators cannot open or close the cash register. The checklist entry-2 and exit-1 system-validated items depend on this module. Without it, checklists can never be fully completed through the intended auto-validation flow. Every shift requires a cash count. | L (2-3 weeks) |
| **Ticket Creation -- Full Wizard** (#8) | PARTIAL (major gaps) | Missing customer picker, add-ons (bedding/inventory), pickup estimate, special items, payment method choice. Operators creating wash-fold tickets cannot add extras or set accurate pricing. | XL (2-3 weeks) |
| **Self-Service Sales / Ventas** (#14) | MISSING | Walk-in customers using washers/dryers cannot be billed through Android. This is a primary revenue stream. | L (2-3 weeks) |
| **Cash Payment Recording** (within Payments) | MISSING | No way to record cash payments on tickets. Only Zettle card payments work. Many customers pay cash. | M (3-5 days) |

### P1 High Priority (15 gaps)

Needed for daily operations. Operators can work around these with the PWA, but the Android app is not standalone without them.

| Gap | Feature | Impact |
|-----|---------|--------|
| **Branch scoping** (Water + Checklist) | All water and checklist queries are unscoped -- data from all branches appears everywhere. Multi-branch operators see wrong data. |
| **User attribution** (Water + Checklist) | Records lack `recorded_by`/`completed_by` fields. Audit trail is broken. |
| **Role-based navigation guards** | All screens visible to all users regardless of role. Employees can see admin/manager areas. |
| **Dashboard** (#1) | Landing screen is a placeholder. No operational KPIs or quick actions. |
| **Ticket Detail enhancements** (#9) | Missing quick-actions, mark-paid, add-on display, status advancement. |
| **Ticket List filter presets** (#10) | Active tickets not filterable by status preset. |
| **Cash Register -- History** (#4) | No way to review past cash movements. |
| **Checklist table name mismatch** | ChecklistRepository queries `cash_registers` but PWA uses `cash_movements`. Auto-validation for entry-2 likely broken. |
| **Incidents -- New** (#15) | Cannot document incidents (camera + audio required). |
| **Turnos -- Control** (#17) | No clock in/out tracking. |
| **Damaged Clothing -- New** (#21) | Cannot document pre-existing damage on garments. |
| **Supplies -- Inventory** (#23) | Cannot check or adjust supply stock. |
| **Admin -- Precios** (#32, #33) | Cannot update service/item prices without PWA. |
| **Admin -- Usuarios** (#37, #38) | Cannot manage users without PWA. |
| **"Mas" menu** | More tab is a dead placeholder. Should link to available operator modules. |

### P2 Medium (22 gaps)

Important but workarounds exist (use PWA for these features).

| Gap | Feature | Notes |
|-----|---------|-------|
| Ticket list -- Ready/Completed/All filter presets (#11, #12, #13) | Add filter chips or tabs to existing TicketListScreen. |
| Checklist history -- date filters + stats (#7 gaps) | Currently loads last 30 with no filtering. |
| Water module -- offline caching | Network-dependent; error state on disconnect. |
| Water module -- audit log recording | Actions not logged to `audit_logs` table. |
| Checklist -- entrada/salida ordering enforcement | No guard preventing exit before entry completion. |
| Profile (#57) | View/edit own profile. |
| Incidents -- History (#16) | List view. |
| Damaged Clothing -- History (#22) | List view. |
| Shifts -- History/Schedule/Reports (#18-20) | Read-only views. |
| Supplies -- Labels/Reports (#24, #25) | Brother printer is already integrated, just needs label templates for supplies. |
| Vacaciones (#27) | Complex calendar UI. |
| Calendario (#28, #29) | Calendar views. |
| Practicas (#30) | Best practices library. |
| Ayuda (#31) | Help center. |
| Precios -- Paquetes/Promociones/Historial (#34-36) | Admin pricing features. |
| All Gerencia modules (#39-52) | 14 management/reporting screens. Mostly read-only dashboards. |
| Notifications (#58) | Would need FCM integration. |

### P3 Low (4 gaps)

Nice to have. Can remain PWA-only indefinitely for managed deployments.

| Gap | Feature | Notes |
|-----|---------|-------|
| Signup (#54) | Self-registration. Not needed when admins create accounts. |
| Forgot Password (#55) | Password reset. Low frequency use case. |
| Reset Password (#56) | Token-based reset. |
| Supplies -- Brother Debug (#26) | Printer debugging page. |

---

## Cross-Cutting Concerns

### Offline Support

| Area | PWA | Android | Gap |
|------|-----|---------|-----|
| Ticket list | Limited (cached in memory) | In-memory only | No Room/local DB cache for tickets |
| Transaction recovery | N/A (browser-based) | Room-based crash recovery (TransactionDao) | **Android is ahead** -- interrupted ticket->pay->print transactions survive app kill |
| Water level | No offline | No offline | Both fail on network loss. Android should cache last known level in DataStore. |
| Checklist | No offline | No offline | Both fail on network loss. Today's checklist could be cached locally. |
| Cash register | No offline | N/A (not implemented) | -- |

**Recommendation**: Add Room-based caching for water levels and checklists as a Wave 1.5 task. The transaction persistence pattern (already in TransactionPersistence.kt) provides a good template.

### Branch Scoping

| Area | PWA | Android | Gap |
|------|-----|---------|-----|
| Tickets | `resolveActiveBranch()` applied to all queries | Assumed to be handled by Retrofit/TicketApi headers | Needs verification |
| Water | Branch param on all queries | Branch param exists in WaterRepository but **never passed by WaterViewModel** | **Broken** -- unscoped queries |
| Checklist | Branch scoping on queries | No branch param at all in ChecklistRepository | **Broken** -- unscoped queries |
| Dashboard | Branch-scoped aggregation | N/A (placeholder) | -- |

**Recommendation**: Implement a `BranchProvider` (similar to `TokenProvider`) that resolves the current branch from the user's profile or BuildConfig, and inject it into all repositories. This is a prerequisite for multi-branch deployments.

### Error Handling Patterns

| Area | Pattern | Quality |
|------|---------|---------|
| Tickets (Retrofit) | OkHttp interceptors + try/catch in repository | Good -- consistent with AuthInterceptor and SessionExpiredInterceptor |
| Water (SupabaseTableClient) | `Result<T>` with `SupabaseError` sealed hierarchy | Good -- typed errors, Timber logging |
| Checklist (raw OkHttp) | try/catch with `PostgRestException` | Inconsistent -- does not use SupabaseTableClient from A5 branch. Should be migrated to match water module pattern. |
| Payments (Zettle) | `ZettleErrorMapper` with typed error categories | Good -- includes user-facing Spanish error messages |
| Printing (Brother) | `BrotherErrorMapper` | Good -- maps SDK error codes to human-readable messages |

**Recommendation**: Migrate ChecklistRepository to use `SupabaseTableClient` when the A5 branch is merged. This unifies error handling and auth token management.

### Accessibility

| Area | Implementation | Quality |
|------|---------------|---------|
| Heading semantics | `Modifier.semantics { heading() }` on screen titles | Good -- present on WaterScreen, ChecklistScreen, TicketListScreen, CreateTicketScreen |
| Content descriptions | Icons have contentDescription params | Good -- BottomNavItem icons have labels as descriptions |
| Touch targets | Material 3 components provide 48dp minimum | Standard via M3 defaults |
| Color contrast | Material 3 dynamic theming | Relies on M3 defaults; no custom contrast testing done |
| Screen reader navigation | Not tested | **Unknown** -- no TalkBack testing documented |
| RTL support | Not applicable (Spanish only) | N/A |

**Recommendation**: Add TalkBack testing pass before field deployment. The semantic annotations are present but untested.

### Data Layer Consistency

There are now **two data layer approaches** in the Android codebase:

1. **Retrofit + OkHttp** (main branch, ticket/payment features): Uses `TicketApi` Retrofit interface with OkHttp interceptors for auth, correlation IDs, and 401 handling.
2. **Supabase Kotlin SDK** (A5 branch, water module): Uses `SupabaseTableClient` wrapper with native Supabase SDK calls.
3. **Raw OkHttp** (A6 branch, checklist module): Uses OkHttp directly with manual URL construction and JSON parsing.

Pattern #3 (raw OkHttp) should be eliminated. The checklist module should migrate to either pattern #1 or #2. Pattern #2 (SupabaseTableClient) is the intended go-forward approach for new modules.

---

## Merge Readiness of Today's Branches

| Branch | SHA | Merge-Ready? | Blocking Issues |
|--------|-----|-------------|-----------------|
| `codex/port-bottom-nav` (A2) | `79b3d36` | YES with caveat | Water/Checklist tabs route to placeholder screens, not to the real modules from A3/A6. After merge, need to update imports to point at real screens once those branches land. |
| `codex/port-ui-foundation` (A4) | `fb816a7` | YES | No blockers. Pure additive -- 8 new components, no existing code changed. |
| `codex/port-contract-bridge` (A5) | `694de2f` | YES | No blockers. Adds SupabaseModule, SupabaseTableClient, data models. Core-only changes. |
| `codex/port-water-module` (A3) | `4f05fa0` | YES with caveats | Branch scoping and user attribution missing (P1). Duplicates WaterTankIndicator from A4. Functional for single-branch testing. |
| `codex/port-ops-module-2` (A6) | `c775dca` | YES with caveats | Branch scoping and user attribution missing (P1). Uses raw OkHttp instead of SupabaseTableClient (P2). Possible table name mismatch in `hasCashRegisterToday()` (P1). Functional for single-branch testing. |

**Recommended merge order**: A5 (contract bridge) -> A4 (UI components) -> A2 (bottom nav) -> A3 (water) -> A6 (checklist). Each depends on the prior for clean compilation.

---

## Recommendations for Next Wave

### Immediate (pre-field-testing, 1-2 weeks)

These items should be completed before any operator uses the Android app without PWA fallback:

1. **Cash Register -- Register** (#3, P0-BLOCKER): Denomination counting UI for MXN bills/coins, opening/closing detection, discrepancy preview. Without this, checklists cannot auto-validate and operators cannot manage cash.

2. **Cash Payment Recording** (P0-BLOCKER): Add a cash payment path to the charge flow. Minimum viable: a "Mark as Paid (Cash)" button on TicketDetail that updates payment status without going through Zettle.

3. **Branch scoping fix** (P1): Create a `BranchProvider` injected into WaterRepository and ChecklistRepository. Pass branch to all queries. Estimate: 1-2 days.

4. **User attribution fix** (P1): Pass `SessionManager.userId` to `recordWaterLevel()`, `recordWaterOrder()`, `completeChecklist()`, `updateChecklistItem()`. Estimate: half a day.

5. **Table name fix in ChecklistRepository** (P1): Verify whether the correct table is `cash_registers` or `cash_movements` for `hasCashRegisterToday()`. Fix to match PWA. Estimate: 1 hour.

### Wave 1 Completion (2-4 weeks)

6. **Enhanced CreateTicket wizard** (#8, P0): Customer picker, add-ons, pickup estimate, payment choice. This is the largest single work item.

7. **Self-Service Sales / Ventas** (#14, P0): New POS screen for walk-in customers.

8. **Dashboard** (#1, P1): Replace placeholder with real KPIs. Can aggregate from water, checklist, and ticket data.

9. **Ticket list filter presets** (#10-13, P1): Add status filter chips or tabs. Reuse existing TicketListScreen.

10. **Ticket detail enhancements** (#9, P1): Quick-actions dialog, mark-paid, status advancement.

11. **Migrate ChecklistRepository to SupabaseTableClient** (P2): Unify data layer. Estimate: 1-2 days.

### Wave 2 Priorities (after field testing begins)

12. Role-based navigation guards
13. Incidents module (requires camera permissions)
14. Shifts -- Control (clock in/out)
15. Damaged Clothing module (requires camera + speech recognition)
16. Supplies -- Inventory
17. Cash Register -- History
18. Admin -- Precios and Usuarios

---

## Appendix: Branch File Inventory

### `codex/port-bottom-nav` (A2) -- 7 files, +463/-145 lines
- `app/build.gradle.kts` (dependency addition)
- `core/navigation/BottomNavItem.kt` (NEW)
- `core/navigation/LcxNavHost.kt` (MODIFIED -- simplified, delegates to MainScaffold)
- `core/navigation/Screen.kt` (MODIFIED -- added tab graph routes)
- `ui/placeholder/PlaceholderScreens.kt` (NEW)
- `ui/shell/MainScaffold.kt` (NEW)
- `gradle/libs.versions.toml` (dependency version)

### `codex/port-ui-foundation` (A4) -- 8 files, +959 lines
- `core/ui/CategoryBadge.kt` (NEW)
- `core/ui/ChecklistItem.kt` (NEW)
- `core/ui/LcxProgressIndicator.kt` (NEW)
- `core/ui/LcxSlider.kt` (NEW)
- `core/ui/QuickActionCard.kt` (NEW)
- `core/ui/SectionHeader.kt` (NEW)
- `core/ui/StatusBanner.kt` (NEW)
- `core/ui/WaterTankIndicator.kt` (NEW)

### `codex/port-contract-bridge` (A5) -- 6 files, +447 lines
- `core/build.gradle.kts` (dependency additions)
- `core/di/SupabaseModule.kt` (NEW)
- `core/model/Checklist.kt` (NEW)
- `core/model/ChecklistItem.kt` (NEW)
- `core/model/WaterLevel.kt` (NEW)
- `core/network/SupabaseTableClient.kt` (NEW)

### `codex/port-water-module` (A3) -- 16 files, +1953 lines (includes A5 changes)
- `feature/water/build.gradle.kts` (NEW)
- `feature/water/data/WaterProvider.kt` (NEW)
- `feature/water/data/WaterRepository.kt` (NEW)
- `feature/water/ui/WaterHistoryTab.kt` (NEW)
- `feature/water/ui/WaterLevelTab.kt` (NEW)
- `feature/water/ui/WaterScreen.kt` (NEW)
- `feature/water/ui/WaterTankIndicator.kt` (NEW -- duplicates core/ui version)
- `feature/water/ui/WaterViewModel.kt` (NEW)

### `codex/port-ops-module-2` (A6) -- 10 files, +1968 lines
- `feature/checklist/build.gradle.kts` (NEW)
- `feature/checklist/AndroidManifest.xml` (NEW)
- `feature/checklist/data/ChecklistModels.kt` (NEW)
- `feature/checklist/data/ChecklistRepository.kt` (NEW)
- `feature/checklist/di/ChecklistModule.kt` (NEW)
- `feature/checklist/ui/ChecklistItemRow.kt` (NEW)
- `feature/checklist/ui/ChecklistScreen.kt` (NEW)
- `feature/checklist/ui/ChecklistViewModel.kt` (NEW)
- `feature/checklist/ui/EntryChecklistContent.kt` (NEW)
- `settings.gradle.kts` (MODIFIED -- added feature:checklist module)
