# Android viewport audit - physical USB device

Date: 2026-05-14
Device: Pixel 9, ADB serial `49281FDAQ0011J`
Package: `com.cleanx.app` already installed; no reinstall and no `pm clear` used.
Evidence directory: `docs/evidence/20260514/android-viewport-audit-160809/`

## Scope and safety

- Launched the installed app and used the current session where possible.
- Started local PWA backend on `:3000` only because login branch loading required it; stopped it at the end.
- Added temporary `adb reverse tcp:3000` and `tcp:54321`; removed reverses at the end.
- Did not press charge, print, printer discovery/connect, Brother print, or card payment confirmation buttons.
- One accidental external wallet focus occurred while testing orientation; it opened a wallet profile screen, not a charge confirmation flow. I immediately backed out, returned to Clean X, and removed the PII screenshot from evidence.
- Restored device settings at postflight: `font_scale=1.0`, `wm density=420`, `wm size=1080x2424`, `user_rotation=0`, `accelerometer_rotation=0`, night mode `auto`.

## Findings by severity

### FAIL

1. **Tickets quick filters overflow horizontally.** `Entregados (40)` is clipped in normal portrait and worse under display size large.
   Evidence: [140-tickets-list-portrait-normal.png](android-viewport-audit-160809/140-tickets-list-portrait-normal.png), [601-tickets-list-portrait-display-large.png](android-viewport-audit-160809/601-tickets-list-portrait-display-large.png).
   Recommendation: render the filters in a `LazyRow`/scrollable segmented row or wrap chips responsively.

2. **Ticket detail content is clipped under the sticky action bar.** The payment card bottom row is cut at the same y-boundary as the sticky actions; after scrolling, inline actions and sticky actions duplicate competing controls.
   Evidence: [150-ticket-detail-portrait-normal.png](android-viewport-audit-160809/150-ticket-detail-portrait-normal.png), [171-ticket-detail-paid-scrolled-portrait-normal.png](android-viewport-audit-160809/171-ticket-detail-paid-scrolled-portrait-normal.png).
   Recommendation: add bottom content padding equal to sticky action bar plus bottom nav, and avoid showing duplicate action groups simultaneously.

3. **Dashboard breaks under large font.** Quick-action cards are partially hidden behind the bottom nav; copy such as `Autoservicio y...` is truncated by the viewport.
   Evidence: [504-dashboard-portrait-font-large.png](android-viewport-audit-160809/504-dashboard-portrait-font-large.png).
   Recommendation: increase bottom padding for scroll content and switch quick cards to a single-column or tighter responsive layout for large font.

4. **Display size large clips shell title and ticket filters.** `Encargos` wraps into `Enc / argo / s` and is cut by the top bar; ticket filters overflow.
   Evidence: [601-tickets-list-portrait-display-large.png](android-viewport-audit-160809/601-tickets-list-portrait-display-large.png).
   Recommendation: constrain top-bar title width, reduce user chip width, or allow a dedicated second line without clipping.

5. **Sales landscape overlays content with the sticky total bar.** The bottom total/action region covers the lower part of the content panel.
   Evidence: [300-sales-landscape-normal.png](android-viewport-audit-160809/300-sales-landscape-normal.png).
   Recommendation: if landscape is supported, use a landscape-specific layout; otherwise lock this flow to portrait.

### WARN

1. **Large blank band below top bars on primary module screens.** Agua, Caja, Checklist, Encargos, More, and shell placeholders reserve a large vertical band between header and content. This is visually heavy and reduces usable viewport.
   Evidence: [110-water-portrait-normal.png](android-viewport-audit-160809/110-water-portrait-normal.png), [120-cash-portrait-normal.png](android-viewport-audit-160809/120-cash-portrait-normal.png), [190-more-portrait-normal.png](android-viewport-audit-160809/190-more-portrait-normal.png).

2. **Logo asset appears as a tiny dotted/placeholder mark.** The brand/logo area in the app bar and drawer looks broken or too small to identify.
   Evidence: [100-dashboard-portrait-normal.png](android-viewport-audit-160809/100-dashboard-portrait-normal.png), [010-drawer-portrait-normal.png](android-viewport-audit-160809/010-drawer-portrait-normal.png).

3. **More list with large font can be clipped near bottom nav.** The lower `Ayuda` item is partly obscured by the bottom navigation.
   Evidence: [507-more-portrait-font-large.png](android-viewport-audit-160809/507-more-portrait-font-large.png).

4. **Landscape support is inconsistent.** Some screens can be captured in landscape, but repeated rotation attempts returned to portrait or kept an unexpected current route. Treat landscape as unsupported unless explicitly designed and locked.
   Evidence: [300-sales-landscape-normal.png](android-viewport-audit-160809/300-sales-landscape-normal.png), [320-cash-landscape-normal.png](android-viewport-audit-160809/320-cash-landscape-normal.png), [330-checklist-landscape-normal.png](android-viewport-audit-160809/330-checklist-landscape-normal.png).

5. **Charge and print screens are visually complete but high-risk actions are prominent.** Initial screens are not clipped, but they expose primary action buttons immediately. No action buttons were pressed.
   Evidence: [160-charge-initial-portrait-normal.png](android-viewport-audit-160809/160-charge-initial-portrait-normal.png), [180-print-initial-portrait-normal.png](android-viewport-audit-160809/180-print-initial-portrait-normal.png).

## Audit table

| Screen | Viewport | Result | Screenshot | Observations |
|---|---:|:---:|---|---|
| Login - branch load without backend | Portrait normal | WARN | [020](android-viewport-audit-160809/020-login-branch-portrait-normal.png) | Visual layout held, but backend was down and error text appeared. Backend was started for the real login audit. |
| Login - branch selector | Portrait normal | PASS | [022](android-viewport-audit-160809/022-login-branch-selector-portrait-normal.png) | Centered card is complete; no clipping. |
| Login - operator selector | Portrait normal | PASS | [023](android-viewport-audit-160809/023-login-operator-portrait-normal.png) | Operator and guest actions fit. |
| Login - PIN | Portrait normal | PASS | [024](android-viewport-audit-160809/024-login-pin-portrait-normal.png) | PIN field and actions fit before keyboard. |
| Drawer | Portrait normal | WARN | [010](android-viewport-audit-160809/010-drawer-portrait-normal.png) | Drawer fits; logo appears as tiny dotted mark. |
| Dashboard / Inicio | Portrait normal | WARN | [100](android-viewport-audit-160809/100-dashboard-portrait-normal.png) | Usable, but lower content starts under bottom nav and top logo mark looks broken. |
| Dashboard / Inicio | Font large | FAIL | [504](android-viewport-audit-160809/504-dashboard-portrait-font-large.png) | Quick cards and copy are clipped behind bottom nav. |
| Dashboard / Inicio | Display large | WARN | [600](android-viewport-audit-160809/600-dashboard-portrait-display-large.png) | Layout degrades; reduced visible content. |
| Dashboard / Inicio | Dark mode | PASS | [700](android-viewport-audit-160809/700-dashboard-portrait-dark.png) | Contrast and content remain readable. |
| Agua | Portrait normal | WARN | [110](android-viewport-audit-160809/110-water-portrait-normal.png) | Large blank header gap; next section begins behind bottom nav. |
| Caja | Portrait normal | WARN | [120](android-viewport-audit-160809/120-cash-portrait-normal.png) | Screen is usable, but content continues under bottom nav. |
| Caja | Font large | WARN | [506](android-viewport-audit-160809/506-cash-portrait-font-large.png) | Larger text fits primary cards, but visible depth is reduced. |
| Caja | Display large | WARN | [602](android-viewport-audit-160809/602-cash-portrait-display-large.png) | Usable with reduced viewport. |
| Caja | Landscape | WARN | [320](android-viewport-audit-160809/320-cash-landscape-normal.png) | Landscape renders but visible area is very short. |
| Checklist | Portrait normal | WARN | [130](android-viewport-audit-160809/130-checklist-portrait-normal.png) | Third checklist item is partially hidden at bottom; scroll likely required. |
| Checklist | Landscape | WARN | [330](android-viewport-audit-160809/330-checklist-landscape-normal.png) | Renders, but little vertical room. |
| Encargos list | Portrait normal | FAIL | [140](android-viewport-audit-160809/140-tickets-list-portrait-normal.png) | `Entregados` filter is clipped; FAB overlaps list item area. |
| Encargos list | Font large | FAIL | [505](android-viewport-audit-160809/505-tickets-list-portrait-font-large.png) | Filters and visible list density degrade. |
| Encargos list | Display large | FAIL | [601](android-viewport-audit-160809/601-tickets-list-portrait-display-large.png) | Top title clips and filters overflow. |
| Encargos list | Dark mode | FAIL | [701](android-viewport-audit-160809/701-tickets-list-portrait-dark.png) | Same structural overflow as light mode. |
| Encargo detail | Portrait normal | FAIL | [150](android-viewport-audit-160809/150-ticket-detail-portrait-normal.png) | Bottom action bar clips payment content. |
| Encargo detail scrolled | Portrait normal | FAIL | [171](android-viewport-audit-160809/171-ticket-detail-paid-scrolled-portrait-normal.png) | Sticky and inline actions duplicate; sticky bar covers lower content. |
| Charge initial | Portrait normal | WARN | [160](android-viewport-audit-160809/160-charge-initial-portrait-normal.png) | Complete; no charge started. No obvious back affordance except system/nav. |
| Print initial | Portrait normal | PASS | [180](android-viewport-audit-160809/180-print-initial-portrait-normal.png) | Complete; no print action pressed. |
| Print initial | Landscape | WARN | [380](android-viewport-audit-160809/380-print-initial-landscape-normal.png) | Renders in landscape but viewport is constrained. |
| More | Portrait normal | WARN | [190](android-viewport-audit-160809/190-more-portrait-normal.png) | Large top bar area and long list; usable. |
| More mid list | Portrait normal | WARN | [191](android-viewport-audit-160809/191-more-mid-portrait-normal.png) | List content fits, but top band remains large. |
| More bottom list | Portrait normal | WARN | [192](android-viewport-audit-160809/192-more-bottom-portrait-normal.png) | Bottom list fits in normal font. |
| More | Font large | FAIL | [507](android-viewport-audit-160809/507-more-portrait-font-large.png) | Bottom `Ayuda` row is cut by bottom nav. |
| More | Dark mode | PASS | [702](android-viewport-audit-160809/702-more-portrait-dark.png) | Readable. |
| More | Landscape | WARN | [391](android-viewport-audit-160809/391-more-landscape-normal.png) | Renders, but list starts under a tall header. |
| Payment Diagnostics | Portrait normal | PASS | [200](android-viewport-audit-160809/200-payment-diagnostics-portrait-normal.png) | Complete. Charge button not pressed. |
| Printer Settings / Brother Debug | Portrait normal | PASS | [210](android-viewport-audit-160809/210-printer-settings-portrait-normal.png) | Complete. Did not discover, connect, print, forget, or toggle settings. |
| Sales | Portrait normal | WARN | [220](android-viewport-audit-160809/220-sales-portrait-normal.png) | Sticky total/action bar occupies bottom; no charge pressed. |
| Sales | Landscape | FAIL | [300](android-viewport-audit-160809/300-sales-landscape-normal.png) | Sticky total bar overlays content panel. |
| Nueva Incidencia | Portrait normal | WARN | [230](android-viewport-audit-160809/230-more-incidents-new-portrait-normal.png) | Placeholder shell is complete but has large top bar/empty region. |
| Historial de Incidencias | Portrait normal | WARN | [231](android-viewport-audit-160809/231-more-incidents-history-portrait-normal.png) | Placeholder shell complete. |
| Control de Turnos | Portrait normal | WARN | [232](android-viewport-audit-160809/232-more-shifts-control-portrait-normal.png) | Placeholder shell complete. |
| Historial de Turnos | Portrait normal | WARN | [233](android-viewport-audit-160809/233-more-shifts-history-portrait-normal.png) | Placeholder shell complete. |
| Horarios | Portrait normal | WARN | [234](android-viewport-audit-160809/234-more-shifts-schedule-portrait-normal.png) | Placeholder shell complete. |
| Ropa Dañada - Nuevo | Portrait normal | WARN | [235](android-viewport-audit-160809/235-more-damaged-new-portrait-normal.png) | Placeholder shell complete. |
| Ropa Dañada - Historial | Portrait normal | WARN | [236](android-viewport-audit-160809/236-more-damaged-history-portrait-normal.png) | Placeholder shell complete. |
| Inventario de Insumos | Portrait normal | WARN | [237](android-viewport-audit-160809/237-more-supplies-inventory-portrait-normal.png) | Placeholder shell complete. |
| Etiquetas | Portrait normal | WARN | [238](android-viewport-audit-160809/238-more-supplies-labels-portrait-normal.png) | Placeholder shell complete. |
| Vacaciones | Portrait normal | WARN | [239](android-viewport-audit-160809/239-more-vacations-portrait-normal.png) | Placeholder shell complete. |
| Calendario Mensual | Portrait normal | WARN | [240](android-viewport-audit-160809/240-more-calendar-monthly-portrait-normal.png) | Placeholder shell complete. |
| Eventos | Portrait normal | WARN | [241](android-viewport-audit-160809/241-more-calendar-events-portrait-normal.png) | Placeholder shell complete. |
| Mejores Prácticas | Portrait normal | WARN | [242](android-viewport-audit-160809/242-more-best-practices-portrait-normal.png) | Placeholder shell complete. |
| Ayuda | Portrait normal | WARN | [243](android-viewport-audit-160809/243-more-help-portrait-normal.png) | Placeholder shell complete. |

## Postflight

- `font_scale=1.0`
- `wm size=Physical size: 1080x2424`
- `wm density=Physical density: 420`
- `accelerometer_rotation=0`
- `user_rotation=0`
- `Night mode: auto`
- `adb reverse --list` empty
- Current focus returned to `com.cleanx.app/com.cleanx.lcx.MainActivity`
