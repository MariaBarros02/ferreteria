<!--
  Nombre: MODULOS.md
  Descripción: Comportamiento implementado de los módulos operativos: arquitectura, ventas y comprobantes, compras y recepción, lotes y vencimientos, precios, devoluciones, ajustes, conteos, reservas, cierres, auditoría, importación/exportación Excel, lectores de código, consulta sin conexión y permisos.
  Fecha de creación: 2026-10-16
  Fecha de actualización: 2026-10-16
  Autor: Equipo Ferretería
  Versión: 1.0.0
-->

# Módulos operativos: cómo funcionan

Este documento describe el comportamiento **realmente implementado**. Los requisitos y su estado están en [`3_levantamiento_consolidado.md`](3_levantamiento_consolidado.md) (secciones 9 a 11: matriz de trazabilidad, decisiones y pendientes). La instalación y las pruebas están en [`GUIA_INICIO.md`](GUIA_INICIO.md).

## 1. Arquitectura

```
React (MUI, Formik/Yup, i18n)  ──JSON/JWT──►  Spring Boot (controller → service → repository)  ──JPA/JDBC──►  PostgreSQL 16
   selectores con búsqueda                      KardexService: único punto que cambia saldos           Flyway V1..V4
   vista previa de importes                     EventoAuditoriaService: auditoría funcional            disparadores de integridad
   catálogo offline (IndexedDB)                 FacturacionPort: puerto de facturación                 (inmutabilidad, no borrado)
```

| Pieza | Responsabilidad |
|---|---|
| `KardexService` | Bloquea el inventario del producto (`SELECT … FOR UPDATE`), impide saldos negativos, verifica que el mes no esté cerrado y registra **un movimiento auditable por cada cambio de saldo** (tipo, estado, lote, usuario, IP, documento, saldo anterior/nuevo, motivo). Invariante comprobada en `04_verificaciones.sql`: saldo de cada estado = suma de sus movimientos. |
| `LoteService` | Lotes con fecha de vencimiento por producto; asignación FEFO en ventas; baja por vencimiento. |
| `EventoAuditoriaService` | Evento inmutable por operación funcional (tabla `evento_auditoria`, disparador que impide `UPDATE`/`DELETE`; claves sensibles eliminadas del detalle). |
| `ParametrosNegocio` | Parámetros en `application.yml` / variables de entorno: zona horaria del negocio, descuento máximo, días de alerta, modo de precio, múltiplo de redondeo, datos de la empresa. |
| Fechas | Se guardan en UTC; "hoy", vencimientos y meses de cierre se calculan en `ZONA_HORARIA` (por defecto `America/Bogota`). |

Toda operación que cambia inventario o documentos es **transaccional (todo o nada)** y acepta una clave `idOperacion` (8–64 caracteres `[A-Za-z0-9_-]`): si la misma operación llega dos veces (doble clic, reintento por red), el servidor devuelve el documento ya creado en lugar de duplicarlo. El frontend genera la clave por formulario y la conserva entre reintentos.

## 2. Ventas, comprobante y detalle (RF-04, RF-18, RF-19)

**Registro** (`/ventas/nueva`, roles Ventas y Administrador):
1. El cliente se elige con un **selector con búsqueda** (nombre, identificación o correo); no se escriben IDs.
2. Los productos se agregan con el selector (nombre, marca, presentación o código) o con un **lector de código de barras** que actúa como teclado: escanear + Enter agrega el producto; escanear de nuevo suma una unidad.
3. Cada línea admite presentación (conversión a la unidad base, RF-02), descuento en % (máximo `DESCUENTO_MAXIMO`, por defecto 10 %) y, si el cliente tiene reservas activas del producto, la reserva a consumir.
4. La pantalla muestra una **vista previa** de subtotal, descuentos, IVA y total. El servidor **recalcula todo**: precio vigente aprobado (RN-07), IVA del producto (`porcentaje_iva`, 0 por defecto para no alterar productos existentes), redondeo HALF_UP a 2 decimales por línea. El cliente no puede enviar importes.
5. Confirmación → transacción única: descuenta inventario (lotes por FEFO; los lotes vencidos no se venden), consume la reserva si se indicó, registra garantía (`garantia_hasta` = fecha + meses de garantía del producto), crea la factura y audita.

**Listado** (`/ventas`): filtros por número interno o factura, fechas y estado; paginación; exportación a Excel; cada fila abre el detalle.

**Detalle** (`/ventas/:id`): número interno, numeración de factura, modalidad, vendedor, cliente completo, líneas (presentación, descuento, IVA, total, garantía, cantidad ya devuelta), totales, devoluciones asociadas, movimientos de inventario y eventos de auditoría.

**Comprobante** (`/ventas/:id/comprobante`): previsualización, impresión (hoja de estilos de impresión) y PDF (jsPDF, generado en el navegador).
- **Comprobante interno vs. factura electrónica:** `FacturacionPort.esValidaFiscalmente()` devuelve `false` en el adaptador actual (no hay integración DIAN). Por eso el documento se titula *Comprobante interno de venta* y lleva la leyenda *"No constituye factura electrónica de venta ni tiene validez fiscal ante la DIAN"* en pantalla y en cada página del PDF. Solo un adaptador fiscal real que devuelva `true` cambia el título a *Factura electrónica de venta*.
- La exportación se **bloquea** (con motivos) si la venta está anulada, no tiene líneas, no tiene factura o faltan datos del cliente.
- Los datos de la empresa salen de `EMPRESA_*`; los vacíos se omiten (no se inventan).

Ventas históricas (anteriores a V4): los totales se respetan; si faltan, se calculan desde las líneas y la factura (migración V4).

## 3. Compras y recepción (RF-03, RF-17)

- **Orden de compra** (`/compras/nueva`): proveedor y productos con selectores; cantidad y costo por presentación, IVA y descuento por línea. La orden **no** mueve inventario.
- **Recepción parcial o total** (detalle de la compra → *Registrar recepción*): factura del proveedor obligatoria; por línea, la cantidad recibida (no puede superar lo pendiente). Productos con rastreo por **lote** exigen código de lote; si además vencen, exigen la **fecha de vencimiento real** (no se aceptan lotes ya vencidos). Cada recepción crea un **documento de ingreso** (`ING-…`), suma inventario disponible, recalcula el **costo promedio ponderado** y, si el producto tiene recargo configurado, genera una **propuesta de precio** pendiente de aprobación.
- Estados de recepción: Pendiente, Recepción parcial, Recibida, Anulada.
- **Anular un ingreso** (Inventario/Administrador): motivo obligatorio; revierte lo recibido con movimientos vinculados al original (RN-16). Falla si la mercancía ya salió. El consecutivo se conserva (RN-09).
- **Anular la compra** (Compras/Administrador): solo si no tiene ingresos activos; motivo obligatorio.
- **Devolución al proveedor**: desde el detalle de la compra, por línea recibida, desde disponible o dañado y, para productos por lote, desde un lote concreto o asignación automática.

## 4. Inventario, lotes y vencimientos (RF-05)

- Estados: **disponible, reservado, dañado**; ningún saldo negativo (RN-03).
- `/inventario` → *Existencias*: saldos por estado, unidades por vencer, stock mínimo, precio vigente, costo y valor (solo Inventario, Administrador y Gerencia), alertas y exportación; por producto, **kardex** filtrable y **lotes**. *Cambiar estado* mueve cantidades entre estados con motivo (Inventario/Administrador).
- *Alertas*: lotes vencidos (con **baja por vencimiento** del lote, RN-05: solo el lote afectado), lotes por vencer en `DIAS_ALERTA_VENCIMIENTO` días (editable en pantalla), agotados y bajo stock mínimo.
- **Datos históricos**: el inventario existente antes de V4 queda **sin lote** (no se inventan fechas de vencimiento). Ese saldo se vende después de los lotes con fecha; la pantalla de lotes lo distingue como "Sin fecha (histórico)".
- **Reservas** (*Reservas*): cliente, producto, pedido, cantidad y fecha límite; pasan de disponible a reservado. Se liberan por venta asociada, cancelación con motivo o vencimiento (tarea diaria `CRON_RESERVAS` y botón *Liberar vencidas*) (RN-03).

## 5. Precios (RF-07, RF-16)

- Fórmulas: **recargo** `P = C × (1 + r)` (RN-07, por defecto) o **margen** `P = C ÷ (1 − m)` (m < 100 %), con `MULTIPLO_REDONDEO`; o **manual** con justificación obligatoria. `C` es el costo promedio ponderado.
- Toda propuesta queda **pendiente**; solo **Gerencia** aprueba o rechaza (con motivo) y **nunca su propia propuesta**. Al aprobar, el precio queda vigente de inmediato; el historial por producto conserva propuestas, decisiones y vigencias.

## 6. Devoluciones (RF-13)

- **Cliente** (`/devoluciones/nueva`, o desde el detalle de la venta): se elige la venta de origen; por línea, cantidad (máximo = vendido − ya devuelto) y estado del producto: *apto* vuelve a disponible (al mismo lote; si el lote ya venció, a dañado), *dañado* o *vencido* van a dañado. Motivo y tratamiento (reembolso, saldo a favor, cambio, ninguno). El valor se calcula en proporción al total de la línea.
- **Proveedor**: ver sección 3.
- Las devoluciones no se editan ni se eliminan (disparador de BD).

## 7. Ajustes (RF-14) y conteos físicos (RF-15)

- **Ajuste**: solicitud con producto, lote (obligatorio en entradas de productos por lote), entrada/salida, cantidad y motivo. Queda **pendiente sin afectar el inventario**; lo aprueba Gerencia o Administrador **distinto del solicitante**; al aprobar se aplica con su movimiento. Rechazo con motivo.
- **Conteo**: sesión `CF-…` → se registran cantidades (conteo ciego: el saldo del sistema se muestra al cerrar) → cierre → aprobación por otra persona, que genera **ajustes de conteo** por cada diferencia (RN-08: el conteo nunca ajusta automáticamente) o rechazo con motivo.

## 8. Cierres (RF-08)

`/cierres` (solo Gerencia): resumen del mes (ventas, devoluciones, compras recibidas, movimientos por tipo/estado), **conciliación inventario vs. kardex**, pendientes. El cierre se **bloquea** si hay ajustes pendientes o conteos sin decidir; si hay diferencias de conciliación, exige observaciones. Un mes cerrado **no admite movimientos** con fecha en ese mes. La reapertura exige motivo y queda auditada. Cierre único por mes, siempre el día 1 (restricciones de BD).

## 9. Auditoría (RF-08, RF-20)

`/auditoria` (Inventario, Administrador, Gerencia):
- **Eventos**: fecha, usuario, IP, módulo, acción, entidad, descripción; filtros por fechas, usuario, módulo, acción, entidad y texto; paginación; detalle con el JSON del cambio.
- **Movimientos de inventario**: filtros por producto, tipo, documento y fechas; exportación a Excel.
Registros inmutables (disparadores). No hay acciones de edición en la interfaz.

## 10. Importación y exportación Excel (RF-10)

**Importación** (`/importacion`; productos: Inventario/Administrador; terceros: Compras/Ventas/Administrador):
1. Descargar la **plantilla** del tipo (encabezados exactos).
2. Cargar un `.xlsx` (máx. 5 MB y 5000 filas). El servidor **valida sin importar**: cada fila queda *válida*, *con errores* (por celda), *duplicada* (código o identificación ya existente o repetida en el archivo) o *posible duplicado* (nombre normalizado parecido a un registro existente).
3. Revisión: los posibles duplicados se importan solo si el usuario los marca.
4. Confirmación **atómica**: se revalida la unicidad y se importan las filas aprobadas; las demás quedan *omitidas*. Se puede descargar el **informe de errores**.
5. Un archivo idéntico (mismo SHA-256) ya importado se rechaza: re-procesar no duplica datos. Una importación confirmada o cancelada no se vuelve a confirmar.

**Exportación** (productos, existencias, terceros, ventas, movimientos): mismos permisos que la consulta en pantalla; costos solo para roles autorizados; máximo 50 000 filas; cada exportación se audita. **Protección contra inyección de fórmulas:** toda celda de texto que empieza por `=`, `+`, `-`, `@`, tabulador o retorno de carro se antepone con `'`.

## 11. Lectores de código y sistemas externos (RF-11)

- **Soportado:** lectores de código de barras en modo teclado (HID) en los selectores de producto de ventas, compras, conteos y ajustes; búsqueda por código (`GET /api/productos/seleccion/codigo/{codigo}`, solo productos activos) con aviso si no existe.
- **No implementado:** integración con la DIAN, pasarelas u otros sistemas externos; no se simulan. El puerto `FacturacionPort` es el punto de extensión.

## 12. Consulta sin conexión (RF-12, RN-10)

- Al iniciar sesión (y al recuperar la conexión) se descarga el catálogo (productos activos con existencias y precio vigente) a **IndexedDB** del navegador; también hay un botón *Sincronizar para uso sin conexión*.
- Sin conexión, *Productos* muestra la copia local con la fecha de sincronización y búsqueda local; se avisa en todas las pantallas.
- **Solo consulta:** el cliente HTTP rechaza cualquier escritura sin conexión y los botones de registro se deshabilitan; no hay cola de operaciones pendientes, por lo que no puede haber duplicados al reconectar.
- Al cerrar sesión la copia local se borra.

## 13. Permisos (RF-09)

La API es la autoridad (`@PreAuthorize` por endpoint); el frontend oculta el menú y las acciones no permitidas (`frontend/src/utils/permisos.js`) y muestra *acceso denegado* en URLs directas. Resumen:

| Acción | Ventas | Compras | Inventario | Administrador | Gerencia |
|---|---|---|---|---|---|
| Registrar ventas | ✔ | | | ✔ | |
| Consultar ventas y comprobantes | ✔ | | ✔ | ✔ | ✔ |
| Registrar compras / recibir | | ✔ / ✔ | ✔ / ✔ | ✔ | |
| Proponer precios / aprobar | | | ✔ / | ✔ / | / ✔ |
| Ajustes: solicitar / decidir | | | ✔ / | ✔ / ✔ | / ✔ |
| Conteos: contar / decidir | | | ✔ / | ✔ / ✔ | / ✔ |
| Cierres | | | | | ✔ |
| Auditoría | | | ✔ | ✔ | ✔ |
| Usuarios y roles | | | | ✔ | |
| Ver costos | | | ✔ | ✔ | ✔ |

Gestión de usuarios (`/usuarios`): alta, edición de rol y estado, cambio de contraseña (mínimo 10 caracteres con mayúscula, minúscula y número); el sistema impide dejar sin administradores activos.
