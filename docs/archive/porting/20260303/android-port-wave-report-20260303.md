# Android Port Wave 1 Report

## Date: 2026-03-03

**Project**: LCX PWA-to-Android Migration
**PWA Source**: `/Users/diegolden/Code/LCX/v0-lcx-pwa` (Next.js)
**Android Target**: `/Users/diegolden/Code/LCX/lcx-android` (Jetpack Compose)
**Main Branch Base**: `3cef189` (main)

---

## Agent Execution Summary

| Agent | Role | Branch | SHA | Files Changed | Lines (+/-) | Status |
|-------|------|--------|-----|---------------|-------------|--------|
| A1 | Feature Inventory | N/A (docs only) | N/A | 1 doc | -- | DONE |
| A2 | Bottom Navigation | `codex/port-bottom-nav` | `79b3d36` | 7 | +463 / -145 | DONE |
| A3 | Water Module | `codex/port-water-module` | `4f05fa0` | 16 | +1953 / -2 | DONE |
| A4 | Design System | `codex/port-ui-foundation` | `fb816a7` | 8 | +959 / -0 | DONE |
| A5 | Supabase Bridge | `codex/port-contract-bridge` | `694de2f` | 7 | +434 / -1 | DONE |
| A6 | Checklist Module | `codex/port-ops-module-2` | `c775dca` | 10 | +1968 / -1 | DONE |
| A7 | QA Parity | N/A (docs only) | N/A | 1 doc | -- | DONE |

**Total new/modified code files**: 40 (excluding docs)
**Total lines added**: ~5,777

---

## Features Ported Today

### 1. Bottom Navigation Shell (A2)

Material 3 `NavigationBar` with 5 tabs: Inicio, Tickets, Agua, Checklist, Mas. Implements a two-tier `NavHost` architecture: root-level auth check (Login vs MainScaffold) and nested tab-scoped navigation graphs. State preservation via `saveState`/`restoreState` on tab switches. Placeholder screens for tabs not yet backed by real modules.

**Key files**:
- `core/navigation/BottomNavItem.kt` -- tab definitions with icons and labels
- `ui/shell/MainScaffold.kt` -- scaffold with bottom bar and nested NavHost
- `core/navigation/LcxNavHost.kt` -- simplified root NavHost delegating to MainScaffold
- `core/navigation/Screen.kt` -- extended with tab graph route objects
- `ui/placeholder/PlaceholderScreens.kt` -- temporary screens for Inicio, Mas

### 2. Supabase Kotlin SDK Bridge (A5)

Hilt-injected Supabase client providing typed CRUD operations against PostgREST. Wraps `io.github.jan-tennert.supabase` SDK v2.6.1 with `Result<T>` error handling and a `SupabaseError` sealed hierarchy (Unauthorized, BadRequest, NotFound, ServerError, NetworkError, Unknown). Auth token pass-through from existing `SessionManager`.

**Key files**:
- `core/di/SupabaseModule.kt` -- Hilt DI provision of SupabaseClient
- `core/network/SupabaseTableClient.kt` -- CRUD wrapper with `selectAll`, `selectWhere`, `insert`, `update`, `delete`, `selectWithRequest`
- `core/model/WaterLevel.kt`, `Checklist.kt`, `ChecklistItem.kt` -- data models with `kotlinx.serialization`

### 3. Design System Components (A4)

8 reusable Jetpack Compose components following Material 3 theming with `@Preview` annotations:

| Component | Purpose |
|-----------|---------|
| `LcxProgressIndicator` | Linear progress bar with label and percentage |
| `WaterTankIndicator` | Canvas-drawn water tank with fill animation |
| `LcxSlider` | Labeled slider with value display |
| `ChecklistItem` | Checkbox row with category badge and status |
| `CategoryBadge` | Colored pill badge for item categories |
| `QuickActionCard` | Tappable card with icon for dashboard actions |
| `SectionHeader` | Section title with optional action |
| `StatusBanner` | Full-width status announcement banner |

### 4. Water Level Monitor (A3)

Complete `feature:water` Gradle module. Two-tab screen (Nivel Actual + Historial) enabling operators to record daily water tank levels via slider, select a water provider, order water, and review history. Canvas-drawn tank visualization with color-coded status thresholds (critical < 15%, low < 30%, normal < 70%, optimal >= 70%).

**Key files**:
- `feature/water/ui/WaterScreen.kt` -- tab host
- `feature/water/ui/WaterLevelTab.kt` -- slider input, provider selection, save/order actions
- `feature/water/ui/WaterHistoryTab.kt` -- history list with user attribution via PostgREST join
- `feature/water/ui/WaterTankIndicator.kt` -- Canvas visualization (duplicate of core/ui)
- `feature/water/ui/WaterViewModel.kt` -- state management, load/save/order
- `feature/water/data/WaterRepository.kt` -- Supabase queries via SupabaseTableClient
- `feature/water/data/WaterProvider.kt` -- provider enum

### 5. Checklist Module (A6)

Complete `feature:checklist` Gradle module. Three-tab screen (Entrada / Salida / Historial). Entry checklist auto-creates 6 template items matching PWA (water level, cash register, cleaning, equipment check, supplies check, fire extinguishers). System-validated items for water level and cash register auto-check by querying respective tables. Progress bar gates completion on required items. Category badges with color coding. Exit checklist mirrors structure with 5 items.

**Key files**:
- `feature/checklist/ui/ChecklistScreen.kt` -- 3-tab host
- `feature/checklist/ui/EntryChecklistContent.kt` -- entry/exit checklist rendering
- `feature/checklist/ui/ChecklistItemRow.kt` -- individual item with checkbox, badge, action link
- `feature/checklist/ui/ChecklistViewModel.kt` -- state management, auto-validation logic
- `feature/checklist/data/ChecklistRepository.kt` -- Supabase queries (raw OkHttp)
- `feature/checklist/data/ChecklistModels.kt` -- local data models
- `feature/checklist/di/ChecklistModule.kt` -- Hilt DI

### 6. Feature Inventory (A1) & QA Parity Matrix (A7)

Two documentation deliverables: a complete 58-feature inventory of the PWA with priority/complexity ratings, and a feature-by-feature parity matrix comparing PWA vs Android status with gap severity analysis.

---

## Recommended Merge Order

The branches must be merged in a specific order due to shared file modifications and dependency relationships. Each step below identifies the conflicts that will arise and how to resolve them.

### Step 1: `codex/port-contract-bridge` (A5) -- SHA `694de2f`

**Why first**: A3 (water) cherry-picked A5's changes and extended `SupabaseTableClient` with `selectWithRequest`. A6 (checklist) depends on the Supabase data models. All downstream branches assume these core files exist.

**Conflicts**: None. A5 only adds new files and appends to `core/build.gradle.kts` and `gradle/libs.versions.toml`. No overlapping changes with main.

**Modified shared files**:
- `gradle/libs.versions.toml` -- adds `supabaseKt`, `ktor`, `kotlinxDatetime` versions and 5 library entries
- `core/build.gradle.kts` -- adds Supabase dependencies

### Step 2: `codex/port-ui-foundation` (A4) -- SHA `fb816a7`

**Why second**: Pure additive. No dependency on other branches. Merging early makes `core/ui/WaterTankIndicator.kt` available, which is needed to resolve the duplicate in A3.

**Conflicts**: None. All 8 files are new additions in `core/ui/`. No existing files modified.

### Step 3: `codex/port-bottom-nav` (A2) -- SHA `79b3d36`

**Why third**: The navigation shell must exist before feature modules can be wired into it. Does not depend on A5 or A4, but merging after them avoids rebase churn.

**Conflicts**:
- `gradle/libs.versions.toml` -- A2 adds `compose-material-icons-extended`. A5 (already merged) added Supabase entries to the same file. **Resolution**: Both additions are in different sections of the file. Standard merge should auto-resolve. If not, manually include both additions.
- `core/navigation/LcxNavHost.kt` -- A2 substantially rewrites this file (-145/+171 lines). No other branch touches it. No conflict expected against main.
- `core/navigation/Screen.kt` -- A2 adds new route objects. No other branch modifies this file. Clean merge.

### Step 4: `codex/port-water-module` (A3) -- SHA `4f05fa0`

**Why fourth**: Depends on A5 for `SupabaseTableClient` and `SupabaseModule`. A3 cherry-picked A5's commit, so their copies of shared files must be reconciled.

**Conflicts (EXPECTED)**:
- `core/build.gradle.kts` -- A3 contains the same changes as A5 (cherry-pick). **Resolution**: Accept A5's version (already merged). The changes are identical.
- `core/di/SupabaseModule.kt` -- Same content from cherry-pick. **Resolution**: Accept A5's version.
- `core/model/WaterLevel.kt`, `Checklist.kt`, `ChecklistItem.kt` -- Same content from cherry-pick. **Resolution**: Accept A5's version.
- `core/network/SupabaseTableClient.kt` -- A3's version includes the `selectWithRequest` method that A5's does not have. **Resolution**: Accept A3's version (superset of A5). This adds the `selectWithRequest` method with `PostgrestRequestBuilder` support (28 additional lines).
- `gradle/libs.versions.toml` -- A3 has the same Supabase entries as A5 (cherry-pick). **Resolution**: Accept A5's version (already merged); identical content.
- `settings.gradle.kts` -- A3 adds `:feature:water` to the include list. **Resolution**: Accept A3's version (appends `:feature:water`).

**Post-merge action**: Delete `feature/water/ui/WaterTankIndicator.kt` (179 lines) and update `WaterLevelTab.kt` to import `com.cleanx.lcx.core.ui.WaterTankIndicator` from A4's core/ui module instead. This eliminates the duplicate.

### Step 5: `codex/port-ops-module-2` (A6) -- SHA `c775dca`

**Why last**: A6 is the most isolated feature branch. It adds `feature:checklist` as a new module with no code dependencies on A2/A3/A4, but it modifies `settings.gradle.kts` which A3 also modifies.

**Conflicts (EXPECTED)**:
- `settings.gradle.kts` -- A3 (already merged) changed the include line to add `:feature:water`. A6 changes the same line to add `:feature:checklist`. **Resolution**: Manually merge to include both: `include(":app", ":core", ":feature:auth", ":feature:tickets", ":feature:payments", ":feature:printing", ":feature:water", ":feature:checklist")`

**Post-merge actions**:
1. Wire `ChecklistScreen` into A2's navigation graph (replace placeholder)
2. Wire `WaterScreen` into A2's navigation graph (replace placeholder)
3. Update the "Mas" placeholder to link to available modules

---

## Known Issues & Bugs (from A7)

### Severity: P0-BLOCKER (Functional correctness)

| # | Bug | Module | Impact | Recommended Fix | Effort |
|---|-----|--------|--------|-----------------|--------|
| 1 | `hasCashRegisterToday()` queries `cash_registers` table but PWA uses `cash_movements` | Checklist (A6) | Cash register auto-validation in entry checklist will always return false (wrong table name) | Change table name in `ChecklistRepository.kt` from `cash_registers` to `cash_movements` | 15 min |

### Severity: P1-HIGH (Data integrity)

| # | Bug | Module | Impact | Recommended Fix | Effort |
|---|-----|--------|--------|-----------------|--------|
| 2 | Branch scoping broken -- all queries are unscoped | Water (A3) + Checklist (A6) | Multi-branch deployments see data from all branches. In single-branch deployments, this is harmless but incorrect. | Create a `BranchProvider` interface that resolves the active branch from user profile. Inject into WaterRepository and ChecklistRepository. Pass branch param to all queries. | 1-2 days |
| 3 | User attribution missing -- `recordedBy`/`completedBy` never passed | Water (A3) + Checklist (A6) | Records inserted without user ownership. Audit trail is broken. Cannot determine who recorded a water level or completed a checklist. | Pass `SessionManager.userId` to `recordWaterLevel()`, `recordWaterOrder()`, `completeChecklist()`, `updateChecklistItem()`. | 0.5 day |
| 4 | Checklist uses raw OkHttp instead of SupabaseTableClient | Checklist (A6) | Inconsistent error handling, no typed errors, manual auth token management, duplicated networking code. Bypasses the standardized data layer from A5. | Migrate `ChecklistRepository` to use `SupabaseTableClient` from A5. | 1-2 days |

### Severity: P2-MEDIUM (Code quality)

| # | Bug | Module | Impact | Recommended Fix | Effort |
|---|-----|--------|--------|-----------------|--------|
| 5 | Duplicate `WaterTankIndicator` in `feature:water` and `core:ui` | Water (A3) + Design System (A4) | Two copies of the same component. Feature version (179 lines) is slightly different from core version (136 lines). Risk of divergent behavior over time. | Delete `feature/water/ui/WaterTankIndicator.kt`. Update imports in `WaterLevelTab.kt` to use `com.cleanx.lcx.core.ui.WaterTankIndicator`. Reconcile any API differences. | 1 hour |

---

## P0 Gaps for Next Sprint

These 4 missing features block the Android app from supporting a full operator shift without PWA fallback.

| # | Gap | Feature ID | Impact | Effort Estimate |
|---|-----|-----------|--------|-----------------|
| 1 | **Cash Register -- Register** | #3 | Operators cannot open or close the cash register. Checklist entry and exit system-validated items depend on this module. Without it, checklists can never fully complete via auto-validation. Every shift requires a cash count (opening + closing). | L (2-3 weeks) |
| 2 | **Enhanced Ticket Creation** | #8 | Current `CreateTicket` is missing: customer picker with search/create, bedding add-ons (sabanas, cobijas, edredones), inventory product add-ons with barcode scanner, pickup date/time estimate, special items separation toggle, payment method choice at creation. Operators creating wash-fold tickets cannot add extras or set accurate pricing. | XL (2-3 weeks) |
| 3 | **Self-Service Sales (Ventas)** | #14 | Walk-in customers using washers/dryers cannot be billed through Android. This is a primary revenue stream (self-service equipment usage + product sales). Requires cart, quantity controls, customer picker, inventory search. | L (2-3 weeks) |
| 4 | **Cash Payment Recording** | N/A (payments) | No way to record cash payments on tickets. Only Zettle card payments work. Many laundromat customers pay in cash. Minimum viable: "Mark as Paid (Cash)" action on TicketDetail that updates payment status. | M (3-5 days) |

**Total estimated effort to close P0 gaps**: 7-10 weeks for one developer.

---

## Parity Score

### Before Today (Wave 0 baseline)

| Status | Count | % |
|--------|-------|---|
| FULL | 7 | 12% |
| PARTIAL | 4 | 7% |
| MISSING | 47 | 81% |

The 7 FULL features were: Login, Ticket List, Ticket Create, Ticket Detail, Zettle Payments, Brother Printing, Transaction Orchestration. The 4 PARTIAL were the ticket list filter variants (Active, Ready, Completed, All) which exist as a single unfiltered list.

### After Today (Wave 1)

| Status | Count | % |
|--------|-------|---|
| FULL | 7 | 12% |
| PARTIAL | 8 | 14% |
| PLACEHOLDER | 2 | 3% |
| MISSING | 41 | 71% |

**New PARTIAL features added today** (4):
- Water Level Monitor (#2) -- functional but missing branch scoping, user attribution, offline cache, audit log
- Checklist Entrada (#5) -- functional but missing branch scoping, user attribution, table name bug, inconsistent data layer
- Checklist Salida (#6) -- functional with same gaps as Entrada
- Checklist History (#7) -- functional but missing date filters, stats summary

**New PLACEHOLDER features added today** (2):
- Dashboard (#1) -- shows icon and "proximamente" text, no data
- "Mas" menu -- shows icon grid with no navigation targets

**New infrastructure (not feature-counted)**:
- 5-tab bottom navigation shell
- Supabase Kotlin SDK bridge with typed CRUD
- 8 shared design system components

### Net Change

| Metric | Before | After | Delta |
|--------|--------|-------|-------|
| Features at PARTIAL or better | 11/58 | 17/58 | +6 |
| Parity percentage (FULL only) | 12% | 12% | +0% |
| Parity percentage (PARTIAL+) | 19% | 29% | +10% |
| Infrastructure readiness | Low | Medium | Supabase bridge, nav shell, design system |

The parity percentage for FULL features did not increase because all new modules were delivered with known gaps (branch scoping, user attribution). Once those bugs are fixed, Water and Checklist (Entrada + Salida) can be promoted to FULL, bringing FULL parity to 10/58 (17%).

---

## Next Wave Plan (48-72h)

### Block 1: Bug Fixes from Wave 1 (1-3 days)

Priority fixes that should land before any new feature work.

| # | Fix | Target File(s) | Effort |
|---|-----|----------------|--------|
| 1 | Fix table name `cash_registers` -> `cash_movements` | `feature/checklist/data/ChecklistRepository.kt` | 15 min |
| 2 | Create `BranchProvider` and inject into WaterRepository + ChecklistRepository | New: `core/di/BranchProvider.kt`; Modified: `WaterRepository.kt`, `ChecklistRepository.kt`, `WaterViewModel.kt`, `ChecklistViewModel.kt` | 1-2 days |
| 3 | Pass `SessionManager.userId` to all write operations | `WaterViewModel.kt`, `WaterRepository.kt`, `ChecklistViewModel.kt`, `ChecklistRepository.kt` | 0.5 day |
| 4 | Delete duplicate `WaterTankIndicator` from `feature:water` | Delete `feature/water/ui/WaterTankIndicator.kt`; modify `WaterLevelTab.kt` imports | 1 hour |
| 5 | Migrate `ChecklistRepository` from raw OkHttp to `SupabaseTableClient` | `feature/checklist/data/ChecklistRepository.kt` | 1-2 days |
| 6 | Wire real Water and Checklist screens into navigation (replace placeholders) | `ui/shell/MainScaffold.kt`, `app/build.gradle.kts` | 0.5 day |

### Block 2: Cash Register Module (unblocks checklist auto-validation)

| Deliverable | Complexity | Description |
|-------------|-----------|-------------|
| Cash Register -- Register (#3) | L | Denomination-by-denomination counting UI for MXN bills ($20-$1000) and coins ($0.50-$20). Auto-detect opening vs closing. Discrepancy preview for closing. Day summary hero card. Supabase table: `cash_movements`. |
| Cash Payment Recording | M | Add "Mark as Paid (Cash)" action to TicketDetail. Update payment status in Supabase without Zettle flow. |
| Cash Register -- History (#4) | M | Filterable movement list with summary cards (income, expenses, net, count). Date range picker. |

### Block 3: Enhanced Ticket Creation (customer picker, add-ons)

| Deliverable | Complexity | Description |
|-------------|-----------|-------------|
| Customer Picker | M | Search existing customers, create new customer inline. Used by Encargos and Ventas. Build as reusable component. |
| Bedding/Inventory Add-ons | M | Add-on selection UI with catalog lookup from `add_ons_catalog` and `inventory` tables. Barcode scanner search integration. |
| Pickup Estimate + Special Items | S | Date/time picker for pickup estimate. Toggle for special items separation. |
| Payment Method Choice | S | At-creation payment choice: pending vs paid (cash/card). |

---

## Risk Register

### Technical Risks

| # | Risk | Likelihood | Impact | Mitigation | Status |
|---|------|-----------|--------|------------|--------|
| R1 | **Branch scoping broken in production** | HIGH (confirmed) | HIGH -- multi-branch operators see wrong data | BranchProvider implementation in Block 1 | OPEN -- confirmed by A7 |
| R2 | **Cash register table name mismatch** | HIGH (confirmed) | HIGH -- checklist auto-validation silently fails | 15-min fix in Block 1 | OPEN -- confirmed by A7 |
| R3 | **Three competing data layer patterns** | MEDIUM | MEDIUM -- maintenance burden, inconsistent error handling | Migrate checklist to SupabaseTableClient in Block 1 | OPEN -- confirmed by A7 |
| R4 | **No offline support for new modules** | MEDIUM | HIGH -- operators lose connectivity in the field | Plan Room-based caching for water/checklist. Existing TransactionPersistence pattern is a template. | DEFERRED to Wave 2 |
| R5 | **Supabase Kotlin SDK maturity** | LOW | MEDIUM -- library is v2.6.1 with active development | Working successfully in water module. Monitor for breaking changes. | MONITORING |
| R6 | **Cash register denomination UI complexity** | MEDIUM | MEDIUM -- MXN-specific denominations, error-prone on small screens | Use LazyColumn with NumberTextField. Test on 5" and 6.5" screens. | OPEN -- Block 2 |
| R7 | **Enhanced ticket creation scope creep** | HIGH | HIGH -- most complex single feature, multi-step wizard | Break into sub-screens. Port pricing logic from `useEncargoPricing` as pure Kotlin. Write unit tests. | OPEN -- Block 3 |
| R8 | **Role-based access not enforced** | MEDIUM | MEDIUM -- all users see all screens regardless of role | Implement navigation guards after Wave 1 bug fixes. | DEFERRED to Wave 2 |
| R9 | **Audit log not written for water/checklist actions** | LOW | LOW -- PWA writes to `audit_logs`, Android does not | Add audit log writes to repositories once data layer is unified. | DEFERRED |
| R10 | **Merge conflicts on settings.gradle.kts** | HIGH (confirmed) | LOW -- easy manual resolution | Documented in merge plan above. Both A3 and A6 modify the same include line. | KNOWN -- merge plan provided |

### Schedule Risks

| # | Risk | Likelihood | Impact | Mitigation |
|---|------|-----------|--------|------------|
| S1 | **P0 gaps require 7-10 weeks for one developer** | HIGH | HIGH -- app cannot replace PWA until all 4 P0s resolved | Parallelize: one developer on Cash Register, one on Enhanced Ticket Creation. Ventas can be deferred if self-service is low volume. |
| S2 | **Bug fixes delay new feature work** | MEDIUM | LOW -- Block 1 is estimated at 1-3 days | Tackle table name fix and duplicate deletion same day. BranchProvider and data layer migration can overlap with Block 2. |
| S3 | **Merge integration may surface compile errors** | MEDIUM | MEDIUM -- branches developed in isolation, not integration-tested together | Run full Gradle build after each merge step. Fix import/dependency issues immediately. Budget 0.5 day for integration. |

---

## Appendix A: Complete Branch File Manifest

### `codex/port-contract-bridge` (A5) -- 7 files

| File | Action | Lines |
|------|--------|-------|
| `core/build.gradle.kts` | MODIFIED | +6/-1 |
| `core/src/main/java/com/cleanx/lcx/core/di/SupabaseModule.kt` | NEW | +63 |
| `core/src/main/java/com/cleanx/lcx/core/model/Checklist.kt` | NEW | +45 |
| `core/src/main/java/com/cleanx/lcx/core/model/ChecklistItem.kt` | NEW | +27 |
| `core/src/main/java/com/cleanx/lcx/core/model/WaterLevel.kt` | NEW | +39 |
| `core/src/main/java/com/cleanx/lcx/core/network/SupabaseTableClient.kt` | NEW | +244 |
| `gradle/libs.versions.toml` | MODIFIED | +10 |

### `codex/port-ui-foundation` (A4) -- 8 files

| File | Action | Lines |
|------|--------|-------|
| `core/src/main/java/com/cleanx/lcx/core/ui/CategoryBadge.kt` | NEW | +90 |
| `core/src/main/java/com/cleanx/lcx/core/ui/ChecklistItem.kt` | NEW | +146 |
| `core/src/main/java/com/cleanx/lcx/core/ui/LcxProgressIndicator.kt` | NEW | +123 |
| `core/src/main/java/com/cleanx/lcx/core/ui/LcxSlider.kt` | NEW | +111 |
| `core/src/main/java/com/cleanx/lcx/core/ui/QuickActionCard.kt` | NEW | +115 |
| `core/src/main/java/com/cleanx/lcx/core/ui/SectionHeader.kt` | NEW | +77 |
| `core/src/main/java/com/cleanx/lcx/core/ui/StatusBanner.kt` | NEW | +161 |
| `core/src/main/java/com/cleanx/lcx/core/ui/WaterTankIndicator.kt` | NEW | +136 |

### `codex/port-bottom-nav` (A2) -- 7 files

| File | Action | Lines |
|------|--------|-------|
| `app/build.gradle.kts` | MODIFIED | +1 |
| `app/src/main/java/com/cleanx/lcx/core/navigation/BottomNavItem.kt` | NEW | +50 |
| `app/src/main/java/com/cleanx/lcx/core/navigation/LcxNavHost.kt` | MODIFIED | +26/-145 |
| `app/src/main/java/com/cleanx/lcx/core/navigation/Screen.kt` | MODIFIED | +16 |
| `app/src/main/java/com/cleanx/lcx/ui/placeholder/PlaceholderScreens.kt` | NEW | +101 |
| `app/src/main/java/com/cleanx/lcx/ui/shell/MainScaffold.kt` | NEW | +268 |
| `gradle/libs.versions.toml` | MODIFIED | +1 |

### `codex/port-water-module` (A3) -- 16 files (8 unique to water, 8 cherry-picked from A5)

| File | Action | Lines | Note |
|------|--------|-------|------|
| `feature/water/build.gradle.kts` | NEW | +64 | |
| `feature/water/src/.../data/WaterProvider.kt` | NEW | +40 | |
| `feature/water/src/.../data/WaterRepository.kt` | NEW | +176 | |
| `feature/water/src/.../ui/WaterHistoryTab.kt` | NEW | +206 | |
| `feature/water/src/.../ui/WaterLevelTab.kt` | NEW | +437 | |
| `feature/water/src/.../ui/WaterScreen.kt` | NEW | +103 | |
| `feature/water/src/.../ui/WaterTankIndicator.kt` | NEW | +179 | DUPLICATE -- delete after merge |
| `feature/water/src/.../ui/WaterViewModel.kt` | NEW | +285 | |
| `core/network/SupabaseTableClient.kt` | MODIFIED | +28 vs A5 | Adds `selectWithRequest` |
| `settings.gradle.kts` | MODIFIED | +1/-1 | Adds `:feature:water` |
| `core/build.gradle.kts` | cherry-pick from A5 | -- | Identical to A5 |
| `core/di/SupabaseModule.kt` | cherry-pick from A5 | -- | Identical to A5 |
| `core/model/Checklist.kt` | cherry-pick from A5 | -- | Identical to A5 |
| `core/model/ChecklistItem.kt` | cherry-pick from A5 | -- | Identical to A5 |
| `core/model/WaterLevel.kt` | cherry-pick from A5 | -- | Identical to A5 |
| `gradle/libs.versions.toml` | cherry-pick from A5 | -- | Identical to A5 |

### `codex/port-ops-module-2` (A6) -- 10 files

| File | Action | Lines |
|------|--------|-------|
| `feature/checklist/build.gradle.kts` | NEW | +65 |
| `feature/checklist/src/main/AndroidManifest.xml` | NEW | +2 |
| `feature/checklist/src/.../data/ChecklistModels.kt` | NEW | +187 |
| `feature/checklist/src/.../data/ChecklistRepository.kt` | NEW | +453 |
| `feature/checklist/src/.../di/ChecklistModule.kt` | NEW | +16 |
| `feature/checklist/src/.../ui/ChecklistItemRow.kt` | NEW | +257 |
| `feature/checklist/src/.../ui/ChecklistScreen.kt` | NEW | +296 |
| `feature/checklist/src/.../ui/ChecklistViewModel.kt` | NEW | +353 |
| `feature/checklist/src/.../ui/EntryChecklistContent.kt` | NEW | +338 |
| `settings.gradle.kts` | MODIFIED | +1/-1 |

---

## Appendix B: Merge Conflict Resolution Cheat Sheet

```bash
# Step 1: Merge A5 (clean -- no conflicts expected)
git checkout main
git merge codex/port-contract-bridge --no-ff -m "feat(core): add Supabase Kotlin SDK bridge"

# Step 2: Merge A4 (clean -- no conflicts expected)
git merge codex/port-ui-foundation --no-ff -m "feat(core/ui): add 8 shared design-system components"

# Step 3: Merge A2 (possible auto-resolve on libs.versions.toml)
git merge codex/port-bottom-nav --no-ff -m "feat(nav): add 5-tab bottom navigation shell"
# If libs.versions.toml conflicts: keep both additions (A5's supabase + A2's icons-extended)

# Step 4: Merge A3 (WILL conflict on cherry-picked files)
git merge codex/port-water-module --no-ff -m "feat(water): port water level monitor module"
# Expected conflicts:
#   core/build.gradle.kts -> accept current (A5 already merged)
#   core/di/SupabaseModule.kt -> accept current (A5 already merged)
#   core/model/*.kt -> accept current (A5 already merged)
#   core/network/SupabaseTableClient.kt -> accept INCOMING (A3 has selectWithRequest)
#   gradle/libs.versions.toml -> accept current (A5 already merged)
#   settings.gradle.kts -> accept incoming (adds :feature:water)

# Step 5: Merge A6 (WILL conflict on settings.gradle.kts)
git merge codex/port-ops-module-2 --no-ff -m "feat(checklist): port entry/exit checklist module"
# Expected conflict:
#   settings.gradle.kts -> manually merge to include both :feature:water AND :feature:checklist

# Post-merge: delete duplicate WaterTankIndicator
rm feature/water/src/main/java/com/cleanx/lcx/feature/water/ui/WaterTankIndicator.kt
# Update WaterLevelTab.kt import to use com.cleanx.lcx.core.ui.WaterTankIndicator
```

---

## Appendix C: Reference Documents

| Document | Path |
|----------|------|
| Feature Inventory (A1) | `/Users/diegolden/Code/LCX/lcx-android/docs/porting/pwa-feature-inventory-20260303.md` |
| Parity Matrix (A7) | `/Users/diegolden/Code/LCX/lcx-android/docs/porting/parity-matrix-20260303.md` |
| API Contract Spec | `/Users/diegolden/Code/LCX/lcx-android/docs/api-contract-spec.md` |
| Physical Device QA Report | `/Users/diegolden/Code/LCX/lcx-android/docs/qa-physical-device-report-20260302.md` |
| This Report | `/Users/diegolden/Code/LCX/lcx-android/docs/porting/android-port-wave-report-20260303.md` |
