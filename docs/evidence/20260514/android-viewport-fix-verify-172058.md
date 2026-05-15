# Android viewport fix verification

Date: 2026-05-14
Device: Pixel 9, ADB serial `49281FDAQ0011J`
Package: `com.cleanx.app`
Evidence directory: `docs/evidence/20260514/android-viewport-fix-verify-172058/`

## Safety

- Installed `devDebug` over the existing app; no `pm clear`.
- Used USB ADB with `adb reverse tcp:3000 tcp:3000` and `tcp:54321 tcp:54321` during verification.
- Did not press charge, print, Brother discovery/connect/print, or hardware-flow buttons.
- Restored device settings after verification: `font_scale=1.0`, `wm size=reset`, `wm density=420`, `accelerometer_rotation=0`, `user_rotation=0`, night mode `auto`, and removed ADB reverses.

## Results

| Screen | Viewport | Result | Screenshot | Observations |
|---|---:|:---:|---|---|
| Dashboard | Portrait normal | PASS | [000](android-viewport-fix-verify-172058/000-dashboard-portrait-normal.png) | Top bar no longer overlaps status bar; quick actions are scrollable above bottom nav. |
| Encargos list | Portrait normal | PASS | [010](android-viewport-fix-verify-172058/010-tickets-portrait-normal.png) | Quick filters wrap into two rows; `Entregados (40)` and `Todos (76)` are complete. |
| Ticket detail | Portrait normal scrolled | PASS | [022](android-viewport-fix-verify-172058/022-ticket-detail-scrolled-portrait-normal-fixed.png) | `Cobrar` is not duplicated; inline advance action was removed; `Imprimir` remains visible above sticky action bar. |
| Dashboard | Font large | PASS | [030](android-viewport-fix-verify-172058/030-dashboard-portrait-font-large.png) | Quick actions switch to single column and are not cut by bottom navigation. |
| Encargos list | Font large | PASS | [031](android-viewport-fix-verify-172058/031-tickets-portrait-font-large.png) | Filters wrap cleanly and shell title stays one line. |
| More bottom | Font large | PASS | [033](android-viewport-fix-verify-172058/033-more-bottom-portrait-font-large.png) | Bottom rows including `Ayuda` are fully reachable above bottom navigation. |
| Dashboard | Display large | PASS | [040](android-viewport-fix-verify-172058/040-dashboard-portrait-display-large.png) | Shell title stays stable; quick actions remain scrollable. |
| Encargos list | Display large | PASS | [041](android-viewport-fix-verify-172058/041-tickets-portrait-display-large.png) | `Encargos` no longer wraps vertically; quick filters wrap without clipping. |
| Sales | Landscape normal | PASS | [050](android-viewport-fix-verify-172058/050-sales-landscape-normal.png) | Initial `$0.00` state no longer shows the sticky charge bar, so it does not overlay the content panel. |

## Superseded captures

- [020](android-viewport-fix-verify-172058/020-ticket-detail-portrait-normal.png) and [021](android-viewport-fix-verify-172058/021-ticket-detail-scrolled-portrait-normal.png) captured the intermediate state where the inline status action still duplicated the sticky action. That residual was fixed and superseded by [022](android-viewport-fix-verify-172058/022-ticket-detail-scrolled-portrait-normal-fixed.png).

## Verification commands

- `LCX_DEV_API_BASE_URL=http://127.0.0.1:3000 LCX_DEV_SUPABASE_URL=http://127.0.0.1:54321 LCX_DEV_PLATFORM_BASE_URL=http://127.0.0.1:8080 LCX_DEV_NOTIFICATIONS_BASE_URL=http://127.0.0.1:8080 ./gradlew :app:compileDevDebugKotlin :app:installDevDebug`
- Focused ADB screenshot pass over normal portrait, font large, display large, and landscape.
