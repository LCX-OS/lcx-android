# PWA Feature Inventory for Android Porting

**Date**: 2026-03-03
**Source**: `/Users/diegolden/Code/LCX/v0-lcx-pwa` (Next.js PWA)
**Target**: `/Users/diegolden/Code/LCX/lcx-android` (Jetpack Compose Android)
**Agent**: A1 -- Feature Inventory Agent

---

## 1. Android Current State

The Android app currently implements:

| Feature | Status |
|---------|--------|
| Auth (login, session management, 401 handling) | DONE |
| Ticket list (filter, search) | DONE |
| Ticket creation (wash-fold + in-store) | DONE |
| Ticket detail (view, status badge, customer info) | DONE |
| Payments via Zettle (charge screen) | DONE |
| Printing via Brother SDK (label screen) | DONE |
| Transaction orchestration (ticket -> payment -> print) | DONE |
| Navigation (type-safe, animated) | DONE |

**Screens**: Login, TicketList, CreateTicket, TicketDetail, Charge, Print, Transaction, PaymentDiagnostics (debug only)

---

## 2. Complete Feature Inventory

### 2.1 OPERADOR Modules

| # | Module | PWA Route | Priority | Complexity | Android Status | Supabase Tables | Notes |
|---|--------|-----------|----------|------------|----------------|-----------------|-------|
| 1 | **Dashboard** | `/operador/dashboard` | P0 | M | NOT STARTED | `tickets`, `inventory`, `shift_changes`, `water_levels`, `cash_movements` | Control center with quick actions, shift status, KPIs, pending tickets, supply needs. Aggregates data from many modules. Port AFTER individual modules exist. |
| 2 | **Water Level Monitor** | `/operador/agua` | P0 | S | NOT STARTED | `water_levels`, `audit_logs` | Slider-based tank level input, save level, order water (provider selection), history tab. Branch-scoped. Critical daily check. |
| 3 | **Cash Register -- Register** | `/operador/caja/registrar` | P0 | L | NOT STARTED | `cash_movements` | Opening/closing/expense denomination-by-denomination cash counting (MXN bills + coins). Auto-detects if opening or closing needed. Discrepancy preview for closing. Day summary hero card. |
| 4 | **Cash Register -- History** | `/operador/caja/historial` | P0 | M | NOT STARTED | `cash_movements`, `profiles` | Filterable movement list (date range, type, search). Summary cards (income, expenses, net, count). CSV export. Denomination breakdown expandable. |
| 5 | **Checklist -- Entrada** | `/operador/checklist/entrada` | P0 | M | NOT STARTED | `maintenance_checklists`, `checklist_items`, `water_levels`, `cash_movements` | Daily entry checklist with system-validated items (water level + cash opening auto-sync), manual items with categories/badges, progress bar. Blocks completion until required items done. |
| 6 | **Checklist -- Salida** | `/operador/checklist/salida` | P0 | M | NOT STARTED | `maintenance_checklists`, `checklist_items`, `cash_movements` | Exit checklist mirroring entrada structure; system-validated item for cash closing. |
| 7 | **Checklist -- History** | `/operador/checklist/historial` | P1 | S | NOT STARTED | `maintenance_checklists`, `checklist_items`, `profiles` | Historical log of completed checklists with filters (date range), stats summary, progress per checklist. |
| 8 | **Encargos -- Nuevo** | `/operador/encargos/nuevo` | P0 | XL | PARTIAL (CreateTicket) | `tickets`, `services_catalog`, `add_ons_catalog`, `inventory`, `customers` | Multi-step wizard: service selection, weight input (min 3kg), bedding add-ons, extras, inventory products (barcode scanner search), pickup estimate, special items separation, customer picker (search/create), payment choice (pending/paid + method). Android has basic CreateTicket but missing: bedding/inventory add-ons, customer picker, pickup estimate, special items, payment choice. |
| 9 | **Encargos -- Detail** | `/operador/encargos/[id]` | P0 | M | PARTIAL (TicketDetail) | `tickets` | Ticket detail with customer info, service details, weight, add-ons, total, payment status/actions, label printing panel (quick-actions dialog for post-creation print+pay flow). Android has basic detail but missing: quick-actions dialog, mark-paid inline. |
| 10 | **Encargos -- Active List** | `/operador/encargos/activos` | P0 | S | PARTIAL (TicketList) | `tickets` | Filtered view: status in [received, processing], serviceType = wash-fold. Android TicketList exists but may need filter presets. |
| 11 | **Encargos -- Ready List** | `/operador/encargos/listos` | P1 | S | PARTIAL (TicketList) | `tickets` | Filtered view: status = ready. Same component with different filter. |
| 12 | **Encargos -- Completed** | `/operador/encargos/completados` | P1 | S | PARTIAL (TicketList) | `tickets` | Filtered view: status = completed/delivered. |
| 13 | **Encargos -- All** | `/operador/encargos/todos` | P1 | S | PARTIAL (TicketList) | `tickets` | Full list, all statuses. |
| 14 | **Ventas (Self-Service Sales)** | `/operador/ventas` | P0 | L | NOT STARTED | `tickets`, `services_catalog`, `add_ons_catalog`, `inventory`, `customers` | Point-of-sale for walk-in equipment usage (washers/dryers) + product sales. Cart with quantity controls, customer picker (incl. anonymous), inventory search/scan, creates multiple tickets per transaction. |
| 15 | **Incidents -- New** | `/operador/incidentes/nuevo` | P1 | M | NOT STARTED | `incidents`, `incident_evidence` | Form: type (machine/customer/safety/staff/other), severity, description, people involved, actions taken. Photo capture (up to 5), audio recording with evidence upload to Supabase Storage bucket. |
| 16 | **Incidents -- History** | `/operador/incidentes/historial` | P1 | S | NOT STARTED | `incidents`, `profiles` | List of past incidents with filters. |
| 17 | **Turnos -- Control** | `/operador/turnos/control` | P1 | M | NOT STARTED | `shift_changes`, `profiles` | Clock in/out with notes, live timer, daily progress bar, overtime alerts, weekly summary stats, active staff panel. Real-time updates. |
| 18 | **Turnos -- History** | `/operador/turnos/historial` | P2 | S | NOT STARTED | `shift_changes`, `profiles` | Past shifts log. |
| 19 | **Turnos -- Schedule** | `/operador/turnos/horario` | P2 | S | NOT STARTED | `shift_changes` | Weekly schedule view. |
| 20 | **Turnos -- Reports** | `/operador/turnos/reportes` | P2 | S | NOT STARTED | `shift_changes`, `profiles` | Shift reports. |
| 21 | **Damaged Clothing -- New** | `/operador/ropa-danada/nuevo` | P1 | M | NOT STARTED | `damaged_clothing`, `damaged_clothing_evidence`, `tickets` | Pre-existing damage documentation: ticket reference, garment type, damage type, description, photo capture, audio recording with live speech-to-text transcription (es-MX), evidence upload. |
| 22 | **Damaged Clothing -- History** | `/operador/ropa-danada/historial` | P1 | S | NOT STARTED | `damaged_clothing`, `profiles` | History of damage records. |
| 23 | **Supplies -- Inventory** | `/operador/suministros/inventario` | P1 | M | NOT STARTED | `inventory` | Supply list with search, stock status badges, add new supply form, stock adjustment dialog (in/out with reason). |
| 24 | **Supplies -- Labels** | `/operador/suministros/etiquetas` | P2 | M | NOT STARTED | `inventory` | Select supplies, choose label template, generate printable labels (browser print window). Brother printer integration notes. |
| 25 | **Supplies -- Reports** | `/operador/suministros/reportes` | P2 | S | NOT STARTED | `inventory`, `supply_requests` | Supply usage reports. |
| 26 | **Supplies -- Brother Debug** | `/operador/suministros/brother-debug` | P2 | S | NOT STARTED | -- | Printer debugging/testing page. |
| 27 | **Vacaciones** | `/operador/vacaciones` | P2 | L | NOT STARTED | `vacation_requests`, `profiles`, `audit_logs` | Calendar (monthly + annual views), date range selection, vacation request submission, request history, balance stats. Manager features: approve/reject, create on behalf, filter by employee. Shift offer panel. |
| 28 | **Calendario -- Monthly** | `/operador/calendario/mensual` | P2 | M | NOT STARTED | -- (local events, may reference shifts/vacations) | Monthly calendar grid with event types (shift, vacation, maintenance, meeting). Event creation dialog (manager only). |
| 29 | **Calendario -- Events** | `/operador/calendario/eventos` | P2 | S | NOT STARTED | -- | Event listing view. |
| 30 | **Practicas** | `/operador/practicas` | P2 | S | NOT STARTED | `best_practices`, `profiles` | Best practices reference library. CRUD for managers, read-only browsing with search/filter for operators. Categories: operations, quality, safety, customer, maintenance, emergency. |
| 31 | **Ayuda** | `/operador/ayuda` | P2 | M | NOT STARTED | `audit_logs` | Help center: FAQ (collapsible Q&A), support tickets (create/answer), search. Manager: create FAQ entries, answer tickets. |

### 2.2 ADMIN Modules

| # | Module | PWA Route | Priority | Complexity | Android Status | Supabase Tables | Notes |
|---|--------|-----------|----------|------------|----------------|-----------------|-------|
| 32 | **Precios -- Servicios** | `/admin/precios/servicios` | P1 | M | NOT STARTED | `services_catalog` | Service catalog CRUD: name, price, category, description, active toggle. Search and filter. |
| 33 | **Precios -- Articulos** | `/admin/precios/articulos` | P1 | M | NOT STARTED | `add_ons_catalog`, `inventory` | Add-on and inventory item price management. |
| 34 | **Precios -- Paquetes** | `/admin/precios/paquetes` | P2 | M | NOT STARTED | `services_catalog` | Bundle/package pricing management. |
| 35 | **Precios -- Promociones** | `/admin/precios/promociones` | P2 | M | NOT STARTED | `services_catalog` | Promotions/discounts management. |
| 36 | **Precios -- Historial** | `/admin/precios/historial` | P2 | S | NOT STARTED | `audit_logs`, `services_catalog`, `inventory`, `profiles` | Price change audit log. |
| 37 | **Usuarios** | `/admin/usuarios` | P1 | L | NOT STARTED | `profiles` | User directory: create users (email, name, role, password, city, branch, shift), edit roles, activate/deactivate. Role guard for manager/superadmin. |
| 38 | **Usuarios -- Detail** | `/admin/usuarios/[id]` | P1 | M | NOT STARTED | `profiles` | Individual user profile editing. |

### 2.3 GERENCIA Modules

| # | Module | PWA Route | Priority | Complexity | Android Status | Supabase Tables | Notes |
|---|--------|-----------|----------|------------|----------------|-----------------|-------|
| 39 | **Estadisticas -- Ventas** | `/gerencia/estadisticas/ventas` | P2 | M | NOT STARTED | `tickets` | Sales analytics with period filters (day/week/month/year), KPI cards. |
| 40 | **Estadisticas -- Empleados** | `/gerencia/estadisticas/empleados` | P2 | M | NOT STARTED | `profiles`, `shifts`, `tickets` | Employee performance stats. |
| 41 | **Estadisticas -- Equipos** | `/gerencia/estadisticas/equipos` | P2 | M | NOT STARTED | `equipment`, `recurring_maintenance_tasks`, `water_levels` | Equipment statistics and health. |
| 42 | **Estadisticas -- Productividad** | `/gerencia/estadisticas/productividad` | P2 | M | NOT STARTED | `tickets`, `profiles`, `shifts` | Productivity analytics. |
| 43 | **Inventario** | `/gerencia/inventario` | P2 | L | NOT STARTED | `inventory`, `audit_logs`, `profiles` | Full inventory management with rules, barcode support, audit history. |
| 44 | **Mantenimiento -- Dashboard** | `/gerencia/mantenimiento/dashboard` | P2 | M | NOT STARTED | `equipment`, `recurring_task_instances`, `recurring_maintenance_tasks` | Maintenance overview: stats (total/completed/pending/overdue tasks), equipment list with status and filters, alert generation. |
| 45 | **Mantenimiento -- Programado** | `/gerencia/mantenimiento/programado` | P2 | M | NOT STARTED | `recurring_task_instances`, `recurring_maintenance_tasks`, `equipment` | Scheduled maintenance tasks list. |
| 46 | **Mantenimiento -- Recurrente** | `/gerencia/mantenimiento/recurrente` | P2 | M | NOT STARTED | `recurring_maintenance_tasks`, `recurring_task_assignments`, `equipment`, `profiles` | Recurring task template management. |
| 47 | **Reportes -- Ventas** | `/gerencia/reportes/ventas` | P2 | M | NOT STARTED | `tickets` | Sales reporting with exports. |
| 48 | **Reportes -- Empleados** | `/gerencia/reportes/empleados` | P2 | M | NOT STARTED | `profiles`, `shifts`, `tickets` | Employee reporting. |
| 49 | **Reportes -- Inventario** | `/gerencia/reportes/inventario` | P2 | S | NOT STARTED | `inventory` | Inventory reporting. |
| 50 | **Reportes -- Operaciones** | `/gerencia/reportes/operaciones` | P2 | M | NOT STARTED | `tickets`, `shifts`, `water_levels` | Operations reporting. |
| 51 | **Reportes -- Productividad** | `/gerencia/reportes/productividad` | P2 | M | NOT STARTED | `tickets`, `profiles`, `shifts` | Productivity reporting. |
| 52 | **Reportes -- Incidentes** | `/gerencia/reportes/incidentes` | P2 | S | NOT STARTED | `incidents`, `profiles` | Incident reporting. |

### 2.4 Cross-Cutting / Auth / Profile

| # | Module | PWA Route | Priority | Complexity | Android Status | Supabase Tables | Notes |
|---|--------|-----------|----------|------------|----------------|-----------------|-------|
| 53 | **Login** | `/login` | -- | -- | DONE | `profiles` | Complete. |
| 54 | **Signup** | `/signup` | P2 | S | NOT STARTED | `profiles` | Self-registration. May not be needed for field app. |
| 55 | **Forgot Password** | `/forgot-password` | P2 | S | NOT STARTED | -- | Email-based reset via Supabase Auth. |
| 56 | **Reset Password** | `/reset-password` | P2 | S | NOT STARTED | -- | Token-based password reset. |
| 57 | **Profile** | `/profile` | P1 | S | NOT STARTED | `profiles` | View/edit own profile. |
| 58 | **Notifications** | `/notificaciones` | P2 | M | NOT STARTED | -- (may use push notifications) | Notification center. |

---

## 3. Supabase Tables Reference

Complete list of Supabase tables referenced across all modules:

| Table | Used By Modules |
|-------|----------------|
| `tickets` | Dashboard, Encargos (all), Ventas, Damaged Clothing, Statistics, Reports |
| `profiles` | Auth, Dashboard, Checklists, Shifts, Incidents, Vacations, Users, Reports, Statistics |
| `water_levels` | Water Monitor, Checklist (auto-validation), Dashboard, Statistics |
| `cash_movements` | Cash Register, Checklist (auto-validation), Dashboard |
| `maintenance_checklists` | Checklist (entrada/salida/historial) |
| `checklist_items` | Checklist (entrada/salida/historial) |
| `services_catalog` | Encargos, Ventas, Pricing (admin) |
| `add_ons_catalog` | Encargos, Ventas, Pricing (admin) |
| `inventory` | Supplies, Encargos, Ventas, Dashboard, Pricing, Statistics, Reports |
| `customers` | Encargos, Ventas |
| `incidents` | Incidents, Statistics, Reports |
| `incident_evidence` | Incidents (new) |
| `shift_changes` | Shifts (control/historial), Dashboard, Statistics, Reports |
| `vacation_requests` | Vacaciones |
| `damaged_clothing` | Damaged Clothing (new/historial) |
| `damaged_clothing_evidence` | Damaged Clothing (new) |
| `equipment` | Maintenance, Statistics |
| `recurring_maintenance_tasks` | Maintenance, Statistics |
| `recurring_task_instances` | Maintenance |
| `recurring_task_assignments` | Maintenance |
| `best_practices` | Practicas |
| `supply_requests` | Supplies Reports |
| `audit_logs` | Water, Pricing, Vacations, Help Center, Inventory, Auth |

---

## 4. Recommended Porting Waves

### Wave 0 -- Foundation (already done)
- Auth (login, session, 401 handling)
- Ticket list / create / detail
- Payments (Zettle)
- Printing (Brother SDK)
- Transaction orchestration

### Wave 1 -- Daily Operator Essentials (P0)
**Goal**: Operators can complete a full shift using only the Android app.

| Order | Module | Complexity | Rationale |
|-------|--------|------------|-----------|
| 1.1 | Water Level Monitor | S | Simplest P0 module. Single-screen CRUD with slider. Establishes Supabase query patterns for Android. |
| 1.2 | Cash Register -- Register | L | Required every shift (opening + closing). Complex denomination counting UI but self-contained. |
| 1.3 | Cash Register -- History | M | Complements register; operators need to see past records. |
| 1.4 | Checklist -- Entrada | M | Depends on water + cash being done first (auto-validates those items). Gate for starting shift. |
| 1.5 | Checklist -- Salida | M | Mirror of entrada; depends on cash closing. Gate for ending shift. |
| 1.6 | Encargos -- Nuevo (enhance CreateTicket) | XL | Extend existing CreateTicket with: customer picker, bedding/inventory add-ons, pickup estimate, special items, payment choice. Most complex single feature. |
| 1.7 | Encargos -- Detail (enhance TicketDetail) | M | Extend existing TicketDetail with: quick-actions dialog, mark-paid, full add-on display. |
| 1.8 | Encargos -- Active List | S | Filter preset on existing TicketList. |
| 1.9 | Ventas (Self-Service Sales) | L | New POS flow for walk-in customers. Reuses catalog/customer infrastructure from encargos. |
| 1.10 | Dashboard | M | Aggregates KPIs from all Wave 1 modules. Port last because it depends on everything else. |

**Estimated effort**: 4-6 weeks for one developer.

### Wave 2 -- Operational Completeness (P1)
**Goal**: Full parity with daily operator tools, plus basic admin capabilities.

| Order | Module | Complexity | Rationale |
|-------|--------|------------|-----------|
| 2.1 | Checklist -- History | S | Quick win, list view. |
| 2.2 | Encargos -- Ready/Completed/All | S each | Filter presets on TicketList. |
| 2.3 | Incidents -- New | M | Camera + audio recording adds Android-native complexity (permissions, MediaRecorder). |
| 2.4 | Incidents -- History | S | List view. |
| 2.5 | Turnos -- Control | M | Clock in/out with live timer. |
| 2.6 | Damaged Clothing -- New | M | Similar to incidents (camera + audio + speech-to-text). |
| 2.7 | Damaged Clothing -- History | S | List view. |
| 2.8 | Supplies -- Inventory | M | Supply CRUD + stock adjustments. |
| 2.9 | Profile | S | View/edit own profile. |
| 2.10 | Admin -- Precios (Servicios + Articulos) | M each | Required for price updates without PWA. |
| 2.11 | Admin -- Usuarios | L | User management. Complex forms + role guards. |

**Estimated effort**: 3-4 weeks for one developer.

### Wave 3 -- Management & Nice-to-Have (P2)
**Goal**: Full feature parity. Most of these are read-only dashboards/reports.

| Modules | Notes |
|---------|-------|
| Turnos (History, Schedule, Reports) | Low urgency read-only views |
| Supplies (Labels, Reports, Brother Debug) | Labels need Brother SDK integration (already available) |
| Vacaciones | Complex calendar UI (monthly + annual), approval workflows |
| Calendario | Event calendar, manager-only creation |
| Practicas | Best practices library, manager CRUD |
| Ayuda | FAQ + support tickets |
| All Gerencia modules (Statistics, Inventory, Maintenance, Reports) | Dashboard/analytics views, mostly read-only |
| Admin (Paquetes, Promociones, Historial) | Rarely updated, can remain PWA-only initially |
| Auth (signup, forgot-password, reset-password) | Low priority for managed deployments |
| Notifications | May need Firebase Cloud Messaging integration |

**Estimated effort**: 6-8 weeks for one developer.

---

## 5. Risk Notes

### Per-Module Risks

| Module | Risk | Mitigation |
|--------|------|------------|
| **Cash Register** | Complex denomination counting UI must be fast and error-free on small screens. MXN-specific denominations (bills $20-$1000, coins $0.50-$20). | Use LazyColumn with number input fields. Test on 5" and 6.5" screens. |
| **Checklist** | System-validated items (water + cash) depend on cross-module data. Race conditions if operator opens checklist before recording water. | Implement sync-on-load pattern like PWA; add "Verify" button. |
| **Encargos Nuevo** | Most complex feature. Multi-step wizard with pricing calculations, catalog lookups, barcode scanning, customer search/create. | Break into sub-screens. Reuse existing CreateTicket scaffolding. Port pricing logic from `useEncargoPricing`. |
| **Incidents / Damaged Clothing** | Camera capture + audio recording require Android runtime permissions (CAMERA, RECORD_AUDIO). MediaRecorder API differs from web. Evidence upload to Supabase Storage. | Use CameraX + MediaRecorder. Request permissions via Activity Result API. Test on Android 11+. |
| **Damaged Clothing -- Speech-to-Text** | PWA uses Web Speech API for live transcription (es-MX). Android equivalent is `SpeechRecognizer`. | Use Android `SpeechRecognizer` with LANGUAGE = "es-MX". Graceful fallback if unavailable. |
| **Ventas** | Creates MULTIPLE tickets per transaction (one per equipment service + one for products). | Ensure `createTickets` (batch) works atomically via Supabase. |
| **Vacaciones** | Complex calendar UI with date range selection, multiple views (monthly/annual). | Consider a calendar library (e.g., Kizitonwose Calendar). |
| **Dashboard** | Aggregates data from 5+ tables. Must be fast on first load. | Use parallel Supabase queries. Consider local caching. |
| **Branch Scoping** | PWA uses `resolveActiveBranch()` to scope queries by branch. Android must replicate this. | Port `branch-scope.ts` logic early; inject branch into all repo queries. |
| **Offline Support** | PWA has limited offline support. Android users may lose connectivity in the field. | Plan Room-based caching for critical flows (water level, cash register). |
| **Supabase Storage** | Evidence upload (photos, audio) requires Storage bucket access. Bucket names and RLS policies must match. | Verify bucket configuration. Test upload from Android with auth token. |

### Architectural Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| **Supabase client for Android** | PWA uses `@supabase/supabase-js`. Android needs `io.github.jan-tennert.supabase:*` Kotlin library or raw REST. | Evaluate supabase-kt library. If not mature enough, use Retrofit with Supabase REST API + auth headers. |
| **Auth token forwarding** | All Supabase queries require the user's JWT. PWA handles this via browser client. | SessionManager already exists in Android. Ensure token refresh and inject into Supabase client. |
| **Pricing calculation parity** | PWA calculates pricing client-side in `useEncargoPricing` hook. Android must produce identical results. | Port pricing logic as a pure Kotlin function. Write unit tests comparing PWA and Android outputs. |
| **Role-based access** | PWA uses `profile.role` to show/hide features and guard API calls. Roles: employee, manager, superadmin. | Implement role-based navigation guards in LcxNavHost. Replicate `RoleGuardByRoles` pattern. |

---

## 6. Summary Statistics

| Category | Count |
|----------|-------|
| Total features inventoried | 58 |
| P0 (critical for daily ops) | 10 |
| P1 (important, next wave) | 15 |
| P2 (nice to have) | 33 |
| Already done in Android | 7 (auth + tickets + payments + printing + transaction) |
| Partially done (need enhancement) | 4 (CreateTicket, TicketDetail, TicketList filters x2) |
| Not started | 47 |
| Unique Supabase tables referenced | 23 |

---

## 7. Appendix: PWA DB Layer Files

For porting reference, the PWA database access layer is organized in `/lib/db/`:

| File | Purpose |
|------|---------|
| `water.ts` | Water level CRUD, history, order recording |
| `cash-movements.ts` | Cash register movements, summary, discrepancy detection |
| `checklists.ts` | Checklist CRUD, item toggling, auto-validation status |
| `tickets.ts` | Ticket CRUD, status updates, payment updates |
| `ticket-builders.ts` | Ticket construction helpers (wash-fold, sales) |
| `ticket-customer.ts` | Customer resolution for ticket creation |
| `customers.ts` | Customer CRUD, search |
| `catalogs.ts` | Services catalog, add-ons catalog, pricing lookups |
| `incidents.ts` | Incident CRUD, evidence upload |
| `damaged-clothes.ts` | Damaged clothing CRUD, evidence upload |
| `shifts.ts` | Clock in/out, shift history, statistics |
| `vacations.ts` | Vacation requests, approvals, stats, shift offers |
| `supplies.ts` | Supply CRUD, stock adjustments, label templates |
| `supply-requests.ts` | Supply request management |
| `pricing.ts` | Service/add-on pricing management, audit log |
| `statistics.ts` | Analytics queries (sales, employees, equipment, productivity) |
| `maintenance.ts` | Recurring tasks, instances, equipment maintenance |
| `equipment.ts` | Equipment CRUD |
| `employees.ts` | Employee/profile queries |
| `best-practices.ts` | Best practices CRUD |
| `help-center.ts` | FAQ and support ticket management |
| `dashboard.ts` | Dashboard aggregate queries |
| `inventory.ts` | Full inventory management with rules |
| `inventory-rules.ts` | Inventory business rules |
| `branch-scope.ts` | Branch-scoped query resolution |
| `auth-helpers.ts` | Auth-related profile operations |
| `base.ts` | Shared error handling utilities |
