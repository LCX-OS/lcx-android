# Subagent Merge Checklist (Non-Interference)

Date: 2026-03-03

Purpose: integrate parallel subagent outputs quickly while minimizing merge collisions.

## 1) Branch Hygiene

- Branch naming: `codex/<lane>-<topic>-<date>`
- One concern per branch (navigation, water, contracts, QA docs, etc.)
- Keep docs-only branches separate from feature branches.

## 2) File Ownership by Lane

- Navigation lane:
  - `app/src/main/java/.../navigation/*`
  - `core/src/main/java/.../navigation/*`
- Water lane:
  - `feature/water/**`
  - optional `core/network` contracts only if approved
- Ticket/payment/printing lane:
  - existing `feature/tickets`, `feature/payments`, `feature/printing`
- QA docs lane:
  - `docs/qa-*`
  - `docs/evidence/**`
- Porting docs lane:
  - `docs/porting/**`

Rule: do not edit outside lane-owned files unless explicitly coordinated.

## 3) Merge Order (Recommended)

1. `codex/port-bottom-nav` (shell foundation)
2. `codex/port-water-module` (new module integration)
3. `codex/port-ops-module-*` (additional module)
4. `codex/contract-bridge-*` (if needed after module wiring)
5. `codex/qa-observability-*` + docs lanes

## 4) Fast Validation Before Merge

Run in candidate branch:

```bash
./gradlew :app:assembleDebug
./gradlew test
```

If branch touches device-critical flows:

```bash
./gradlew :app:installDevDebug
/Users/diegolden/Library/Android/sdk/platform-tools/adb logcat -d | rg -i "TXN|HTTP|PAYMENT|PRINT|WATER"
```

## 5) Conflict Resolution Policy

- Code conflicts: resolve in favor of newest lane owner for that file area.
- Navigation conflicts: preserve route IDs and typed route contracts.
- Contract conflicts: never merge breaking payload changes without Contract Guardian signoff.
- Doc conflicts: prefer additive merge (do not drop evidence sections).

## 6) Evidence Required Per Merge PR

- What changed (scope)
- Why (porting objective)
- Acceptance checks run
- Risk/residual gaps
- Screens or logs when UI/flow behavior changed

## 7) Stop Conditions (Do Not Merge)

- Any lane reports failing acceptance gate.
- No evidence for a claimed PASS.
- Unresolved auth/session regression.
- Build/install broken on dev flavor.
