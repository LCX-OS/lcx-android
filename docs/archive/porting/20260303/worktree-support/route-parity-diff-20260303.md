# Route Parity Diff (PWA -> Android)

Generated: 2026-03-03 08:59:55 CST
PWA root: '/Users/diegolden/Code/LCX/v0-lcx-pwa'
Android root: '/Users/diegolden/Code/LCX/.worktrees/lcx-android-porting-support'

## 1) Operator Module Parity

| Module | PWA route | Android status | Notes |
|---|---|---|---|
| agua | /operador/agua | TODO | Sin módulo water nativo |
| ayuda | /operador/ayuda | TODO | Sin módulo ayuda nativo |
| caja | /operador/caja | TODO | Sin flujo caja nativo |
| calendario | /operador/calendario | TODO | Sin módulo calendario nativo |
| checklist | /operador/checklist | TODO | Sin módulo checklist nativo |
| dashboard | /operador/dashboard | TODO | No dashboard operador equivalente |
| encargos | /operador/encargos | PARTIAL | TicketList/Create/Detail + Charge/Print existentes; falta shell/tab parity |
| incidentes | /operador/incidentes | TODO | Sin módulo incidentes nativo |
| practicas | /operador/practicas | TODO | Sin módulo prácticas nativo |
| ropa-danada | /operador/ropa-danada | TODO | Sin módulo ropa dañada nativo |
| suministros | /operador/suministros | TODO | Sin módulo suministros nativo |
| turnos | /operador/turnos | TODO | No módulo turnos |
| vacaciones | /operador/vacaciones | TODO | Sin módulo vacaciones nativo |
| ventas | /operador/ventas | TODO | No módulo ventas dedicado |

## 2) Bottom Navigation Parity

PWA mobile bottom nav routes (source: 'components/bottom-navigator.tsx'):

- /operador/dashboard
- /operador/ventas
- /operador/encargos
- /operador/turnos
- /operador/caja

Android current route set (source: 'core/navigation/Screen.kt' + 'LcxNavHost'):

- Login
- TicketList / CreateTicket / TicketDetail
- Charge / Print / Transaction
- PaymentDiagnostics (debug)

Parity result: **MISSING bottom-tab shell and module tabs parity**.

## 3) Immediate Porting Delta

1. Add native bottom nav scaffold with 5 operator tabs parity.
2. Port `agua` and `caja` first (high operational impact).
3. Port `checklist` (entrada/salida/historial) with operational gating semantics.
4. Keep current ticket/payment/print flow as one tab (Encargos) during wave-1.
