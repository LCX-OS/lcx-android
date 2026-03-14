# Android Native Feature Gate vs PWA

Fecha de corte: 2026-03-13
Workspace: `/Users/diegolden/Code/LCX`
PWA source of truth: `/Users/diegolden/Code/LCX/v0-lcx-pwa`
Android target: `/Users/diegolden/Code/LCX/lcx-android`

## 1. Objetivo

Convertir el inventario viejo de parity booleana en un gate operable por feature, con criterios de aceptacion verificables para cerrar la app nativa por iteraciones.

Este documento responde una pregunta mas util que el snapshot 1:1 por ruta:

- que falta para que Android pueda reemplazar al PWA en operacion real,
- que ya existe en codigo pero aun no esta expuesto,
- que puede cerrar como feature parity aunque la UX nativa no copie exactamente la navegacion web.

## 2. Regla de cierre

Una feature puede marcarse `DONE` aunque la UX nativa no replique exactamente la ruta del PWA, solo si conserva:

1. descubribilidad desde la IA nativa,
2. guardas de rol equivalentes,
3. el mismo contrato de datos y side effects relevantes,
4. las validaciones y bloqueos operativos del PWA,
5. evidencia objetiva de build/test/smoke.

Estados de trabajo usados por este gate:

- `DONE`: reemplazo nativo aceptable del comportamiento PWA.
- `PARTIAL`: expuesto y util, pero aun le faltan capacidades operativas.
- `CODE_NOT_WIRED`: existe modulo o logica, pero la app aun expone placeholder o no lo conecta.
- `SHELL`: navegacion o pantalla vacia sin logica de negocio.
- `MISSING`: no existe implementacion util.

## 3. Baseline real Android al 2026-03-13

### 3.1 Expuesto y util hoy

- Auth login.
- Ticket list sobre `tickets` reales, con refresco y accesos discoverable a presets.
- Ticket detail con carga en frio, quick actions nativas post-create, avance de estado, mark-as-paid, SMS, charge y print.
- Ticket create ampliado con pago al crear, fecha promesa, indicaciones especiales y add-ons manuales.
- Ticket preset screens (`active`, `ready`, `completed`, `all`) discoverable desde Encargos.
- Ventas autoservicio nativa con customer picker, cliente anonimo, carrito mixto y create batch `source=venta`.
- Flujo de charge + print + transaction orchestration.
- Caja como tab funcional con registrar + historial.
- Agua conectada al feature real, con scoping por `profile.branch` y persistencia de `recorded_by` / `branch`.
- Dashboard operador nativo con quick actions reales, rutina operativa del dia y agregados minimos de tickets pendientes + suministros.
- More hub y role guards operator/manager.

### 3.2 Existe en codigo pero no esta conectado al producto real

- Ninguno relevante en este corte.

### 3.3 Expuesto solo como shell

- Incidentes nuevo / historial.
- Turnos control / historial / horario / reportes.
- Ropa danada nuevo / historial.
- Suministros inventario / etiquetas / reportes / brother-debug.
- Vacaciones.
- Calendario mensual / eventos.
- Practicas.
- Ayuda.

### 3.4 Placeholder directo

- Ninguno relevante en este corte.

### 3.5 Ausente

- Signup / forgot password / reset password.
- Profile.
- Notifications.
- Todo Admin.
- Todo Gerencia.

## 4. Observacion importante sobre medicion

`scripts/porting/verify-parity.sh` sigue siendo util, pero hoy solo mide presencia de rutas declaradas. No alcanza para cerrar parity funcional.

Limitaciones observadas:

- No distingue entre ruta expuesta con UI real y ruta expuesta con placeholder.
- No refleja bien que Caja ya es usable como feature consolidada, aunque no tenga deep links 1:1 para `registrar` e `historial`.
- No captura `CODE_NOT_WIRED` para modulos que ya existen en `feature/*`.

Decision de gate:

- mantener `verify-parity.sh` como chequeo de route coverage,
- usar este documento como fuente de verdad para feature parity.

## 5. Gate G0 - Measurement Integrity

Antes de cerrar varias features en paralelo, la medicion debe dejar de mentir.

### G0.1 Registry y snapshot actualizados

Estado actual: `NO`

Done when:

- `docs/porting/route-registry.json` refleja el estado real al 2026-03-13.
- Caja deja de aparecer como "no route present" si la decision oficial es consolidarla en un solo screen/tab.
- Agua y Checklist quedan marcados con su estado real (`PARTIAL`, `DONE`, etc.), no como placeholders ficticios.
- Cada PR que cierre un gap actualiza este gate y el registry correspondiente.

### G0.2 Taxonomia unica de estado

Estado actual: `NO`

Done when:

- equipo usa solo `DONE`, `PARTIAL`, `CODE_NOT_WIRED`, `SHELL`, `MISSING`,
- no se vuelve a mezclar "route present" con "feature done" en conversaciones de avance.

## 6. Gate G1 - Operador Standalone

Objetivo: un operador puede abrir, operar y cerrar turno completo sin fallback al PWA.

### G1.1 Dashboard operador

Estado actual: `PARTIAL`

PWA refs:

- `app/(authenticated)/operador/dashboard/page.tsx`

Done when:

- reemplaza el placeholder de `Screen.Dashboard`,
- muestra quick actions a checklist entrada, agua, caja y nuevo encargo,
- muestra rutina operativa del dia con estado de agua / caja / checklist,
- muestra pendientes operativos minimos: tickets pendientes y necesidades de suministros,
- respeta roles de operador.

Evidence minima:

- `./gradlew :app:assembleDevDebug`
- smoke manual con data real o fixture que muestre quick actions y tarjetas vivas

Estado al 2026-03-13:

- `Screen.Dashboard` ya no usa placeholder; monta un dashboard nativo con quick actions reales hacia Checklist, Agua, Caja y Nuevo encargo,
- agrega rutina operativa con estado de checklist entrada/salida, agua y caja sobre datos reales del backend,
- agrega pendientes operativos minimos con tickets abiertos y suministros bajos sin inventar metricas nuevas,
- el smoke manual post-login ya confirmo quick actions reales y rutina operativa viva con una sesion `employee` del entorno; tras guardar Agua y registrar apertura/cierre de Caja, el dashboard reflejo `Nivel de agua = OK`, `Apertura de caja = OK` y `Corte de caja = OK`,
- las tarjetas inferiores tambien quedaron revalidadas con data real: `Tickets pendientes` mostro `Abiertos: 86`, el CTA `Abrir` navego a un detail real (`T-20260306-0001` / `Diego Jimenez`) y `Necesidades de suministros` renderizo su empty state real (`No hay suministros por reordenar.`).

Residual exacto para flippear a `DONE`:

- falta validar de forma explicita los role variants distintos a `employee` dentro del dashboard nativo,
- mientras ese smoke no quede documentado, el gate se mantiene en `PARTIAL` aunque quick actions, rutina y tarjetas inferiores ya esten vivas post-login.

### G1.2 Agua

Estado actual: `PARTIAL`

PWA refs:

- `app/(authenticated)/operador/agua/page.tsx`
- `lib/db/water.ts`

Android refs:

- `feature/water/**`
- `app/src/main/java/com/cleanx/lcx/ui/shell/MainScaffold.kt`

Done when:

- `Screen.Water` deja de usar placeholder y monta `feature/water`,
- lecturas y escrituras van scoping por branch,
- inserts guardan `recorded_by` y `branch`,
- si el contrato vigente usa `audit_logs` para alertar bajo nivel, Android replica el mismo write contract del PWA,
- existen current tab + history tab + order water,
- estados `critical/low/normal/optimal` coinciden con PWA,
- errores y retry UX funcionan,
- save y order actualizan historial sin salir de la pantalla.

No blocker para G1:

- selector de sucursal explicito para manager/superadmin,
- cache offline local.

Evidence minima:

- `./gradlew :app:assembleDevDebug`
- tests unitarios/repo para branch scoping e inserts
- smoke manual: save level + order water + refresh history

Estado al 2026-03-13:

- `Screen.Water` ya monta la feature real con tabs de nivel actual + historial y CTA de pedir agua cuando el tanque cae a critico,
- lecturas y escrituras siguen resolviendo `branch` y `recorded_by` desde el perfil autenticado,
- el snapshot inicial ahora replica el comportamiento del PWA cuando no hay registros previos: Android arranca en `75% / optimal` en vez de caer en falso `0% / critical`,
- la capa `feature/water:data` ya tiene tests puros para snapshot inicial e inserts (`recorded_by`, `branch`, action label, provider payload y alert payload PWA-style) y deja evidencia de que el contrato escrito sigue alineado con PWA,
- el smoke manual real ya confirmo `Nivel Actual -> Guardar Nivel -> Historial` con sesion operativa: Android guardo `17% / 1,700 L`, mostro feedback `Nivel guardado correctamente`, refresco el historial in-place y el dashboard reflejo `Nivel de agua = OK`,
- el subflujo `Pedir Agua` tambien quedo humedo manualmente con proveedor real de la UI (`Aguafina Express`): Android mostro `Llamar Proveedor`, registro la orden y refresco historial sin salir de la pantalla,
- Android ahora escribe el mismo side effect contractual del PWA para alertas de agua (`audit_logs` con `table_name=water_alerts`, `action=push_notification`, branch y payload de nivel) tanto en `Guardar Nivel` como en `Pedir Agua`; se verifico contra Supabase local con filas nuevas `1773464053036` y `1773464170887`,
- durante ese smoke se cerraron dos residuos reales de Android: `Historial` dejaba de renderizar por un `String.format` invalido y la carga de tickets podia romper por `status=\"paid\"` legado; ambos quedaron endurecidos en codigo y con tests.

Residual exacto para flippear a `DONE`:

- el contrato que si existe hoy ya quedo cubierto hasta `audit_logs`, pero en este workspace/entorno no aparecio un consumidor o backing service verificable que demuestre entrega efectiva de esa alerta al gerente; por eso la parte de notify al gerente sigue sin poder declararse cerrada,
- selector explicito de sucursal para manager/superadmin y cache offline siguen fuera de G1 y no bloquean la paridad operativa minima.

### G1.3 Caja

Estado actual: `PARTIAL`

PWA refs:

- `app/(authenticated)/operador/caja/registrar/page.tsx`
- `app/(authenticated)/operador/caja/historial/page.tsx`
- `lib/db/cash-movements.ts`

Android refs:

- `feature/cash/**`

Done when:

- apertura, gasto y cierre funcionan con desglose por denominacion,
- resumen del dia y discrepancy preview de cierre son confiables,
- historial muestra movimientos con usuario, monto, tipo y desglose expandible,
- la feature es facilmente descubrible para registrar e historial, aunque sea dentro de un solo tab screen,
- el contrato oficial usa `cash_movements`,
- checklist y dashboard pueden depender de caja sin hacks temporales.

Mover a G2, no blocker para G1:

- filtros avanzados por fecha/tipo/busqueda,
- export CSV,
- deep link separado tipo `/registrar` y `/historial`.

Evidence minima:

- `./gradlew :feature:cash:test`
- smoke manual apertura -> gasto -> cierre -> historial

Estado al 2026-03-13:

- `Screen.Cash` sigue consolidando registro + historial en un mismo screen/tab sobre `cash_movements`,
- el smoke manual real ya confirmo apertura de caja (`OPENING $1000.00`) y cierre de caja (`CLOSING $1000.00`) con persistencia backend y reflejo inmediato en Dashboard/Checklist,
- despues de abrir caja, Dashboard reflejo `Apertura de caja = OK` y Checklist Entrada auto-valido `entry-2` (`Registrar caja inicial`),
- despues de cerrar caja, Checklist Salida auto-valido `exit-1` (`Cerrar caja`) y el corte quedo visible como registrado hoy.

Residual exacto para flippear a `DONE`:

- en esta pasada no se rehizo smoke manual del subflujo `gasto` ni del preview de discrepancia/desglose de cierre; por eso Caja sigue en `PARTIAL` aunque el loop de apertura/cierre ya este operativo,
- la feature ya sirve de dependencia real para Dashboard/Checklist, pero todavia falta cerrar el smoke completo `apertura -> gasto -> cierre -> historial` pedido por el gate.

### G1.4 Checklist entrada / salida / historial

Estado actual: `PARTIAL`

PWA refs:

- `app/(authenticated)/operador/checklist/entrada/page.tsx`
- `app/(authenticated)/operador/checklist/salida/page.tsx`
- `app/(authenticated)/operador/checklist/historial/page.tsx`
- `lib/db/checklists.ts`

Android refs:

- `feature/checklist/**`

Done when:

- `Screen.Checklist` deja de usar placeholder y monta `feature/checklist`,
- contrato y tablas se alinean con PWA (`maintenance_checklists`, `checklist_items`, `cash_movements`),
- no se usa `cash_registers` como fuente de auto-validacion,
- entrada y salida auto-validan agua y caja correctamente,
- `completed_by` se persiste y los items sistemicos quedan sincronizados con BD,
- checklists completados quedan read-only,
- el operador puede completar entrada y salida sin tocar PWA,
- historial minimo muestra checklists completados del dia y recientes.

Mover a G2, no blocker para G1:

- filtros de fecha y stats de historial,
- vista manager de pendientes / incompletos.

Evidence minima:

- `./gradlew :app:assembleDevDebug`
- tests para auto-validacion entry-1 / entry-2 / exit cash rule
- smoke manual: agua -> caja -> checklist entrada -> checklist salida

Estado al 2026-03-13:

- `Screen.Checklist` ya monta la feature real consolidada en tabs `Entrada` / `Salida` / `Historial`,
- la auto-validacion sigue el contrato PWA sobre `water_levels` + `cash_movements` (`entry-1`, `entry-2`, `exit-1`) y no depende de `cash_registers`,
- al volver al foreground o tocar `Verificar`, Android vuelve a sincronizar los items sistemicos para no dejar estado viejo despues de pasar por Agua o Caja,
- antes de completar un checklist, Android revalida en BD que todos los requeridos sigan completos para no cerrar con drift local o carrera de estado,
- los checklists completados siguen en modo read-only y el historial nativo conserva el minimo operativo de completados recientes,
- el smoke manual post-login ya confirmo que `Entrada`, `Salida` y `Historial` abren sin shell; despues de guardar Agua, `entry-1` se auto-valido en `Entrada` como `Nivel de agua registrado hoy`,
- al registrar apertura de Caja, `entry-2` se auto-valido como `Registrar caja inicial`, y tras registrar cierre de Caja `exit-1` se auto-valido como `Cerrar caja`,
- `Historial` siguio mostrando completados recientes y Salida expuso el CTA operativo correcto (`Registrar corte de caja`) antes de cerrar.

Residual exacto para flippear a `DONE`:

- todavia falta completar efectivamente ambos checklists con sus pasos manuales, no solo la auto-validacion sistemica,
- el smoke dejo un residual operativo real: Salida puede empezar y auto-validar `exit-1` aunque Entrada siga incompleta; mientras no se decida si ese orden es correcto o debe bloquearse, Checklist sigue en `PARTIAL`,
- el historial sigue en modo minimo; filtros/stats quedan fuera de G1 pero aun no tienen smoke manual que cierre parity operacional completa.

### G1.5 Encargos nuevo

Estado actual: `DONE`

Snapshot al 2026-03-13:

- ya usa customer picker con buscar / crear y confirmacion explicita si el telefono ya existe,
- carga `services_catalog`, `add_ons_catalog` e `inventory` desde Supabase y deja de depender de strings manuales,
- recalcula pricing con las mismas reglas del PWA: minimo 3 kg, extras fijos y surcharge de 15% para fragancia premium / hipoalergenico / quitamanchas,
- soporta add-ons de ropa de cama, extras e inventory items con busqueda por nombre / SKU / barcode y entrada compatible con scanner fisico,
- persiste `promised_pickup_date`, `special_items`, `shared_machine_pool`, `special_instructions`, `payment_status` y `payment_method` con el mismo contrato operativo del web,
- se acepta parity funcional y el smoke manual queda como hardening post-flip.

PWA refs:

- `app/(authenticated)/operador/encargos/nuevo/page.tsx`
- `hooks/use-encargo-catalogs.ts`
- `hooks/use-encargo-pricing.ts`
- `components/tickets/customer-picker.tsx`
- `components/tickets/inventory-search-scanner.tsx`

Done when:

- deja de ser form basico y usa catalogos reales,
- incluye customer picker con buscar / crear,
- soporta add-ons de ropa de cama y extras,
- soporta inventory items con busqueda o scanner,
- soporta pickup estimate,
- soporta special items y notas especiales,
- permite decidir `pending` vs `paid` al crear,
- si `paid`, captura metodo (`cash`, `card`, `transfer`),
- pricing coincide con reglas del PWA.

Evidence minima:

- `./gradlew :app:assembleDevDebug`
- tests de pricing / mapping de payload
- smoke manual creando ticket con add-ons y pago seleccionado

Estado de evidencia actual:

- `./gradlew :feature:tickets:testDebugUnitTest :app:testDevDebugUnitTest --tests 'com.cleanx.lcx.core.network.contract.*' :app:assembleDevDebug` paso,
- `./scripts/porting/verify-parity.sh` sigue en `32/32` rutas presentes verificadas y `0` mismatches,
- `PARITY_DONE=YES` se flippea por decision de porting al quedar feature-complete en codigo,
- smoke manual queda diferido a hardening posterior.

### G1.6 Encargos detail

Estado actual: `PARTIAL`

Snapshot al 2026-03-13:

- carga el ticket por `id` desde `tickets` para cold entry,
- muestra `add_ons`, `promised_pickup_date`, `actual_pickup_date` y `special_instructions` si existen,
- el flujo post-creacion ya aterriza en quick actions nativas equivalentes,
- queda pendiente la validacion manual end-to-end para cerrarlo como gate formal.

PWA refs:

- `app/(authenticated)/operador/encargos/[id]/page.tsx`

Done when:

- muestra add-ons y pickup estimate si existen,
- el flujo post-creacion puede entrar a quick actions reales o equivalente nativo,
- mantiene status advance, mark paid, charge, print y SMS,
- el detalle expone toda la informacion necesaria para entregar el ticket sin abrir PWA.

Evidence minima:

- `./gradlew :app:assembleDevDebug`
- smoke manual desde ticket recien creado hasta cobro / impresion / ready / delivered

### G1.7 Listas de encargos

Estado actual: `DONE`

PWA refs:

- `/operador/encargos/activos`
- `/operador/encargos/listos`
- `/operador/encargos/completados`
- `/operador/encargos/todos`

Done when:

- presets `active`, `ready`, `completed`, `all` son accesibles desde la IA nativa,
- filtros por estado coinciden con el PWA,
- no obligan al usuario a conocer rutas ocultas.

Evidence minima:

- `./gradlew :app:assembleDevDebug`
- `./gradlew :app:testDevDebugUnitTest --tests 'com.cleanx.lcx.core.network.contract.*'`
- smoke manual navegando y validando filtros

### G1.8 Ventas autoservicio

Estado actual: `PARTIAL`

PWA refs:

- `app/(authenticated)/operador/ventas/page.tsx`
- `hooks/use-sales-cart.ts`
- `hooks/use-ventas-catalogs.ts`

Done when:

- reemplaza shell por POS usable,
- soporta customer picker y cliente anonimo,
- soporta equipo + productos + inventario en un mismo cart,
- persiste la venta con los mismos efectos del PWA,
- usa un flujo de cobro nativo compatible con pagos ya existentes.

Evidence minima:

- `./gradlew :app:assembleDevDebug`
- `./gradlew :feature:sales:test`
- smoke manual con venta mixta de equipo + producto

Estado al 2026-03-13:

- `Screen.Sales` ya monta una feature nativa real sobre `feature/sales`,
- reutiliza `feature/tickets` para customer picker buscar/crear, validacion de duplicados y catalogos live,
- soporta cliente anonimo (`Cliente anónimo` / `0000000000`) y customer picker normal,
- replica el contrato PWA de catalogos: equipos `MAQUINARIA`/lavadora/secadora/combo/centrifugado, productos sin ropa de cama, inventario vendible filtrado por `quantity > 0`, `is_for_sale = true`, `price > 0`,
- soporta carrito mixto con equipo + add-ons + inventario vendible en la misma venta,
- arma tickets `source=venta` equivalentes al PWA: un ticket por equipo y un ticket consolidado `Venta Productos` para productos/inventario,
- pago nativo: efectivo/transfer persisten directo como `paid`; tarjeta cobra primero en la terminal nativa y luego crea el batch `venta`,
- si tarjeta cobra pero `POST /api/tickets` falla, la UI deja estado critico con `transactionId` y `correlationId` para conciliacion manual en vez de reintentar a ciegas y duplicar ventas,
- el wiring de pagos ya no rompe por flavor cuando `USE_REAL_ZETTLE=true`: Android integra el SDK real de Zettle, registra `OAuthActivity`, inicializa el SDK al arrancar y lanza el flujo de cobro via `ActivityResult` desde `MainActivity`,
- Android acepta config de Zettle por build (`clientId`, `redirectUrl`, `approved applicationId`) y diagnostica si el APK actual no coincide con el app aprobado por Zettle,
- `devDebug` ya se pudo ensamblar localmente con `USE_REAL_ZETTLE=true`, `clientId`/`redirectUrl` reales y `applicationId` alineado a `com.cleanx.app` via config local fuera de git,
- Login ya quedo operativo en esta maquina con una sesion local valida, `Payment Diagnostics` mostro backend real de Zettle y el SDK inicializado (`Zettle SDK initialized successfully` / no stub),
- el smoke manual real de Ventas ya recorrio una venta mixta con cliente anonimo, un equipo + un item vendible, total `$101.90` y path tarjeta via SDK real; tras endurecer el request critico para no disparar logout global en `POST /api/tickets`, el cobro termino con `POST /api/tickets -> 200` y la UI regreso a Ventas con carrito limpio (`$0.00`).

Residual exacto para flippear a `DONE`:

- falta validacion con hardware real del lector: permisos/location services, autenticacion/pairing y los caminos de cancelacion/fallo fuera del success path de emulador/dev-mode,
- falta una verificacion manual final de conciliacion contra backend/tickets creados desde UI, mas alla del `200` confirmado en logcat para `POST /api/tickets`,
- para reproducir el build real de tarjeta en otra maquina sigue haciendo falta configurar localmente `LCX_ZETTLE_GITHUB_TOKEN` para GitHub Packages y alinear `LCX_ANDROID_APPLICATION_ID`/suffixes al app aprobado por Zettle,
- antes de flippear a `DONE` falta smoke manual real de tarjeta con lector disponible del entorno: permiso/location service, autenticacion del SDK y cobro/cancelacion/failed path fisico.

### G1.9 Role access completo de operador

Estado actual: `PARTIAL`

Done when:

- employee no ve ni navega superficies manager/admin,
- manager puede ver solo lo que el PWA permite,
- More hub, drawer, dashboard y futuros accesos usan la misma matriz de roles,
- cualquier acceso denegado tiene fallback UX claro.

Evidence minima:

- `./gradlew :core:test`
- matrix de pruebas por rol

## 7. Gate G2 - Operador Completo

Objetivo: dejar de depender del PWA para herramientas operativas secundarias o de soporte.

### G2.1 Incidentes

Estado actual: `SHELL`

Done when:

- nuevo incidente soporta tipo, severidad, descripcion, personas, acciones,
- soporta foto y audio con permisos nativos,
- historial filtra y abre registros recientes.

### G2.2 Turnos

Estado actual: `SHELL`

Done when:

- control de entrada/salida es funcional,
- historial y horario son navegables y correctos,
- reportes respetan rol manager.

### G2.3 Ropa danada

Estado actual: `SHELL`

Done when:

- nuevo reporte soporta ticket, prenda, dano, fotos, audio y speech-to-text si se mantiene en scope,
- historial navegable.

### G2.4 Suministros

Estado actual: `SHELL`

Done when:

- inventario de suministros soporta alta y ajuste stock,
- etiquetas usa el stack de printing ya existente,
- reportes y brother-debug dejan de ser placeholders.

### G2.5 Shared surfaces operador

Estado actual: `MISSING` o `SHELL`

Incluye:

- Vacaciones.
- Calendario mensual / eventos.
- Practicas.
- Ayuda.
- Profile.
- Notifications.
- Auth recovery (`forgot/reset`) si sigue siendo requisito del producto movil.

Done when:

- cada surface deja de ser shell o queda explicitamente fuera de scope movil por decision de producto,
- si se saca de scope, la decision queda escrita aqui y en el registry.

### G2.6 Deuda funcional movida desde G1

Done when:

- Caja tiene filtros/export si se decide parity completa.
- Checklist historial tiene filtros/stats.
- Agua tiene cache offline y/o audit trail si se decide parity completa.

## 8. Gate G3 - Admin y Gerencia

Objetivo: cerrar parity fuera de operacion de piso.

### G3.1 Admin Precios

Estado actual: `MISSING`

Incluye:

- servicios,
- articulos,
- paquetes,
- promociones,
- historial.

Done when:

- CRUD y auditoria relevante existen en Android o se decide formalmente que Admin queda web-only.

### G3.2 Admin Usuarios

Estado actual: `MISSING`

Done when:

- listado, detalle, alta, cambio de rol y activacion/desactivacion son posibles,
- guardas de rol coinciden con PWA.

### G3.3 Gerencia

Estado actual: `MISSING`

Incluye:

- estadisticas,
- inventario,
- mantenimiento,
- reportes.

Done when:

- cada modulo existe en Android o queda explicitamente fuera de scope movil por decision de producto,
- reportes sensibles solo para manager/superadmin.

## 9. Gate G4 - Hardening y Release Readiness

Cerrar parity funcional no es suficiente. La app nativa se considera terminada solo si tambien pasa este gate.

Estado actual: `NO`

Done when:

- QA fisico de pagos e impresion actualizado,
- kill/resume del flujo transaccional sigue consistente,
- permisos nativos criticos (camera, audio, notifications) tienen manejo correcto,
- evidencias multimedia suben correctamente a Storage,
- este gate, el registry y el plan de workspace quedaron sincronizados,
- existe memo final de go/no-go para reemplazo operativo del PWA en los modulos cerrados.

## 10. Orden recomendado de cierre

Menor arrepentimiento tecnico:

1. G0 measurement integrity.
2. Agua hardening.
3. Checklist hardening.
4. Completar Encargos nuevo.
5. Hacer discoverable presets de encargos y cerrar detail.
6. Reemplazar Dashboard.
7. Construir Ventas.
8. Incidentes / Ropa danada / Suministros / Turnos.
9. Shared surfaces operador.
10. Admin.
11. Gerencia.
12. G4 hardening final.

Razon:

- Dashboard depende de agua, caja, checklist y tickets.
- Checklist depende de agua y caja correctos.
- Ventas reutiliza customer/catalog/payment primitives que tambien sirven para encargos.
- Admin y Gerencia son alto volumen de UI pero no desbloquean operacion de piso.

## 11. Regla de merge para futuras iteraciones

Una PR solo puede mover una feature a `DONE` en este gate si entrega:

1. cambio funcional visible en Android,
2. referencia explicita al comportamiento PWA preservado,
3. build y tests de modulos tocados,
4. smoke manual minimo si la feature toca hardware, media o storage,
5. actualizacion de este documento.

Si una feature se decide `web-only`, no se deja silenciosamente en shell:

- se documenta aqui,
- se actualiza el registry,
- se quita de cualquier gate futuro de cierre nativo.
