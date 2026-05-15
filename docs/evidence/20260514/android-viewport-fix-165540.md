# Android viewport fix verification

Date: 2026-05-14
Device initially used: Pixel 9, ADB serial `49281FDAQ0011J`
Evidence directory: `docs/evidence/20260514/android-viewport-fix-165540/`

## Scope

- Installed `devDebug` over the existing app without `pm clear`.
- Reinstalled once more with local physical-device endpoints:
  - `LCX_DEV_API_BASE_URL=http://127.0.0.1:3000`
  - `LCX_DEV_SUPABASE_URL=http://127.0.0.1:54321`
  - `adb reverse tcp:3000 tcp:3000`
  - `adb reverse tcp:54321 tcp:54321`
- Did not press charge, print, Brother discovery/connect/print, or hardware-flow buttons.

## Code changes covered

| Area | Change | Intended fix |
|---|---|---|
| Shell top bar | Removed logo from shell title row, constrained user chip, and forced title/branch text to one line with ellipsis. | Prevent `Encargos` from wrapping vertically under large display size. |
| Ticket list | Replaced horizontal quick-filter `LazyRow` with wrapping `FlowRow`. | Prevent `Entregados (...)` from being clipped at normal and large display sizes. |
| Ticket detail | Removed duplicated inline `Cobrar (Zettle)`, kept `Imprimir`, added bottom scroll breathing room, and weighted detail rows. | Avoid competing charge controls and reduce sticky-bar clipping. |
| Dashboard | Added bottom content padding and switched quick actions to one column when font scale is large or width is narrow. | Prevent quick-action cards from being hidden/truncated under large font. |
| More | Added bottom content padding. | Prevent bottom rows such as `Ayuda` from sitting under bottom nav at large font. |
| Sales | Hide the sticky total/action bar while total is zero. | Prevent the initial Sales landscape screen from being covered by a disabled sticky bar. |
| Shared button | Allow button labels to use up to two centered lines. | Reduce button-label truncation with larger fonts. |

## Verification

| Screen | Viewport | Result | Screenshot | Notes |
|---|---:|:---:|---|---|
| Dashboard | Portrait normal | PASS | [002](android-viewport-fix-165540/002-dashboard-portrait-normal-localhost.png) | Top bar no longer overlaps status bar, title is stable, local backend loads with `127.0.0.1` build config. |
| Dashboard | Portrait normal, first reinstall | WARN | [001](android-viewport-fix-165540/001-dashboard-portrait-normal.png) | Shows expected backend timeout from the accidental `10.0.2.2` physical-device build; UI shell still showed no status-bar overlap. |
| Dashboard | Portrait normal, no top inset experiment | FAIL | [000](android-viewport-fix-165540/000-launch-after-fix.png) | Captured the rejected experiment where the top bar overlapped the status bar; code was reverted before final build. |

## Remaining verification gap

ADB stopped listing the USB device after the final physical-device reinstall, so the focused post-fix screenshots for Encargos, Ticket detail, More large font, Sales landscape, and large display size could not be captured in this run. The device settings were not changed during this post-fix pass; no font/display/orientation/night-mode mutations were applied before the disconnect.

## Commands verified

- `git diff --check`
- `./gradlew :app:compileDevDebugKotlin`
- `LCX_DEV_API_BASE_URL=http://127.0.0.1:3000 LCX_DEV_SUPABASE_URL=http://127.0.0.1:54321 LCX_DEV_PLATFORM_BASE_URL=http://127.0.0.1:8080 LCX_DEV_NOTIFICATIONS_BASE_URL=http://127.0.0.1:8080 ./gradlew :app:installDevDebug --rerun-tasks`
