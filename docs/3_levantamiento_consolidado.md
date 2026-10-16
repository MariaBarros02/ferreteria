# Levantamiento de requisitos — Sistema de inventario de ferretería

**Versión:** 1.1 (consolidada + auditoría de implementación del 2026-10-16)

**Estado:** Levantamiento de requisitos cerrado — contradicciones resueltas. La versión 1.1 añade RF-18 a RF-20 (aprobados), RF-21 y RF-22 (identificados en la auditoría, pendientes de aprobación), la matriz de trazabilidad con el estado real de implementación (sección 9), las decisiones de implementación (sección 10) y los pendientes (sección 11).

**Fuentes:**
- `levantamiento_inventario_ferreteria.md` (v0.3) — preguntas, respuestas del interesado y fichas RF-01 a RF-17.
- Matriz de requerimientos del encargo de auditoría (2026-10-16) — fichas RF-18 a RF-20.
- `recomendaciones_requisitos_inventario_ferreteria.md` — decisiones y resoluciones de las contradicciones abiertas en la sección 7 del documento base.

**Referencia de documentación:** ISO/IEC/IEEE 29148:2018.
**Referencia de calidad:** ISO/IEC 25010:2023.

> **Control de versión:** este documento reemplaza como versión vigente a `levantamiento_inventario_ferreteria.md` v0.3. Todas las contradicciones señaladas en su sección 7 quedan resueltas aquí. Las preguntas originales de la sección 5 de esa versión se conservan como trazabilidad histórica en el **Anexo A**, sin necesidad de responderlas nuevamente: sus respuestas fueron reemplazadas por las decisiones de la sección 6 de ese documento, que a su vez quedan formalizadas en este documento.

---

## 1. Modelo conceptual del sistema

El sistema se organiza en cinco bloques centrales y varios componentes transversales:

```text
PRODUCTOS → INVENTARIO → COMPRAS → VENTAS → FACTURACIÓN
```

Transversales: Clientes/proveedores, Usuarios/permisos, Precios, Reservas, Devoluciones/ajustes/conteos, Auditoría, DIAN, Importación de Excel.

**Ejemplo de flujo completo:** se compran 20 taladros a un proveedor → entra mercancía → aumenta inventario → se registra el costo → se calcula y propone el precio → se venden 2 → disminuye inventario → se genera la factura → todo queda registrado en auditoría.

### Estructura general

```text
                    PRODUCTOS
                        │
              ┌─────────┼─────────┐
              ↓         ↓         ↓
           COMPRAS   INVENTARIO  PRECIOS
              │         │         │
              ↓         ↓         ↓
        RECEPCIONES  RESERVAS  APROBACIÓN
              │         │
              └────┬────┘
                   ↓
                 VENTAS
                   │
                   ↓
              FACTURACIÓN
                   │
                   ↓
                  DIAN

Transversal: USUARIOS + PERMISOS · AUDITORÍA · CIERRES · EXCEL · LECTORES
```

### Definiciones base (cierran RN-01 y RN-02)

| Concepto | Definición aprobada |
|---|---|
| Referencia vendible | **Producto + marca + presentación** (ej.: Taladro-Bosch-500W ≠ Taladro-Bosch-750W ≠ Taladro-DeWalt-500W). Cada combinación tiene precio, inventario y código de barras propios. |
| Código de barras | **Único en todo el catálogo**, uno por referencia. |
| Unidad base | Una sola **unidad base por producto**; las presentaciones de compra (caja, paquete) se convierten a esa unidad base. El inventario nunca se divide en sub-inventarios de caja/paquete/unidad. |

Ejemplo de unidad base (tornillo, unidad base = unidad; caja = 100, paquete = 10): al entrar 3 cajas se contabilizan 300 unidades; al vender 17, el saldo queda en 283 unidades.

---

## 2. Alcance y actores

**Alcance confirmado:** compras con recepción parcial; ventas con facturación; una sola bodega; distintas marcas y presentaciones; un código de barras único por referencia; fracciones y conversiones parametrizadas por producto; existencias en disponible/reservado/dañado; saldos nunca negativos; vencimiento/garantía parametrizable con rastreo por lote, serie o ninguno; documento de ingreso respaldado por factura de proveedor; devoluciones, ajustes y conteos físicos como movimientos autorizados; cierres mensuales; terceros (clientes/proveedores) identificados, incluyendo la posibilidad de que un mismo tercero sea ambos; costos, precios e historial con aprobación; usuarios y permisos por rol; migración validada desde Excel; integración con lectores de código de barras y QR; **sin transacciones confirmadas sin conexión a internet**; capacidad dimensionada por escenarios de carga (no "sin límite"); auditoría de todos los movimientos.

| Actor | Interacción confirmada |
|---|---|
| Personal de compras | Registra compras (pedido) y participa en la recepción de mercancía. |
| Personal de ventas | Registra ventas, crea reservas y consulta existencias. |
| Responsable de inventario | Mantiene catálogo, clasifica dañados, propone precios, ejecuta recepciones, ajustes y conteos. |
| Administrador | Gestiona usuarios y permisos; acceso operativo según autorización. |
| Responsable de cierre / Gerencia | Aprueba precios, cierra/reabre períodos, consulta auditoría y costos. |
| Cliente / Proveedor | Terceros referenciados en documentos (compra, venta, reserva, devolución); no acceden directamente al sistema salvo que se defina lo contrario en una fase posterior. |
| Lector de código de barras / QR | Captura de códigos para agilizar operaciones de compra, venta e inventario. |
| Sistema externo | Base de datos única de origen para clientes, productos y facturas; alcance exacto de intercambio de datos pendiente de una fase de integración posterior (no bloquea el diseño base). |

---

## 3. Reglas de negocio (RN) — consolidadas

| ID | Regla aprobada |
|---|---|
| RN-01 | La referencia vendible es la combinación **producto + marca + presentación**; el código de barras es único en todo el catálogo. |
| RN-02 | Cada producto tiene **una unidad base**; conversiones, factores y fracción mínima se parametrizan por producto; la precisión y el redondeo se configuran por país y producto. |
| RN-03 | El inventario se divide en **disponible, reservado y dañado**; ningún saldo puede ser negativo. Una reserva (cliente, pedido, cantidad, fecha límite) se libera por: (1) venta asociada, (2) cancelación manual autorizada o (3) vencimiento de la fecha límite. **No existe liberación "por compra".** |
| RN-04 | Una unidad dañada la clasifica el usuario de inventario; se recupera mediante una solicitud de cambio al proveedor (disponible → dañado → solicitud → cambio/devolución/rechazo). El producto dañado nunca se elimina físicamente del registro. |
| RN-05 | El vencimiento y la garantía son parametrizables por producto. El **rastreo** (ninguno, lote o serie) se parametriza por producto — no se exige lote+serie+unidad simultáneamente para todo el catálogo. Al vencer, la baja aplica **solo a la unidad o lote afectado**, nunca a toda la referencia comercial. |
| RN-06 | **Tercero** es una entidad general que puede tener el rol de cliente, proveedor o ambos. Persona natural y persona jurídica tienen conjuntos de campos obligatorios distintos (sección 5). |
| RN-07 | El costo que alimenta el precio es el **costo promedio ponderado**. El precio propuesto (costo promedio × factor de incremento) requiere aprobación de un usuario autorizado antes de convertirse en precio vigente; el sistema nunca lo aplica automáticamente. |
| RN-08 | Los movimientos de inventario reconocidos son: compra (+), venta (−), devolución de cliente (+ si el producto vuelve a estar disponible), devolución a proveedor (−), ajuste (+/− con autorización y motivo) y conteo físico (genera una diferencia que requiere aprobación antes de convertirse en ajuste; el sistema nunca ajusta el saldo automáticamente). |
| RN-09 | Ningún documento se elimina. Los estados válidos son **Activo** y **Anulado** (no "borrado"); anular requiere motivo, usuario, fecha y, cuando corresponda, una nota de ajuste; el consecutivo utilizado se conserva. |
| RN-10 | **No se confirman transacciones sin conexión a internet** (ventas, compras, facturas, reservas o movimientos de inventario). Sin conexión solo se permite consultar el catálogo e información previamente sincronizada. |
| RN-11 | En ventas concurrentes sobre el mismo saldo, obtiene el inventario **la primera transacción confirmada exitosamente por el sistema**, no la que inició primero; la segunda se rechaza por saldo insuficiente. |
| RN-12 | La capacidad del sistema (productos, movimientos diarios, usuarios simultáneos, años de histórico) se dimensiona mediante **escenarios de carga medibles**, definidos y acordados antes de las pruebas de aceptación; no se documenta como "sin límite". |
| RN-13 | La retención de información se define por dominio: facturas 10 años; auditoría según política de retención que se defina; productos mientras estén en uso o tengan valor histórico; clientes según necesidad legal u operativa. Estos períodos son independientes entre sí. |
| RN-14 | La numeración fiscal de factura (rangos/numeración autorizada por la DIAN) es **distinta** del número interno de venta; la modalidad principal es factura electrónica conforme a la normativa vigente, y la factura de papel solo se usa en los escenarios legalmente permitidos. |
| RN-15 | Ningún dato del Excel de origen se importa directamente: pasa por validación, detección de errores y de posibles duplicados, revisión de un usuario y consolidación, antes de la importación definitiva. |
| RN-16 | Un movimiento histórico nunca se modifica directamente; su corrección se realiza mediante una **nota de ajuste** que genera un nuevo movimiento vinculado al original, conservando ambos en la auditoría. |

---

## 4. Requisitos funcionales

### RF-01 — Mantener catálogo de productos

| Campo | Información |
|---|---|
| **Código** | RF-01 |
| **Versión** | 1.0 |
| **Descripción** | El sistema deberá registrar y consultar productos identificados por la combinación producto + marca + presentación, cada uno con su código de barras único en todo el catálogo. |
| **Justificación** | Identificar inequívocamente cada referencia al comprar y vender. |
| **Precondiciones** | Usuario autorizado (inventario o administrador). |
| **Restricciones** | Código de barras único a nivel global; unidad base y parámetros de rastreo (RF-02, RF-05) definidos al crear el producto. |
| **Prioridad** | Alta |
| **Dependencias** | RN-01, RF-02, RF-05, RF-09 |
| **Actores** | Responsable de inventario; administrador si se autoriza. |
| **Entradas** | Producto, marca, presentación, código de barras, unidad base, tipo de rastreo (ninguno/lote/serie), indicador de vencimiento/garantía. |
| **Proceso** | Capturar y validar datos; rechazar código duplicado; crear o actualizar la referencia. |
| **Salida** | Referencia consultable por código o por combinación producto/marca/presentación. |
| **Postcondiciones** | La referencia queda identificada y disponible para compras, ventas e inventario. |
| **Criterios de aceptación** | Una referencia válida se recupera por su código; un código duplicado en el catálogo se rechaza. |
| **Requisitos no funcionales** | RNF-01, RNF-03, RNF-05 |
| **Estado** | Aprobado |

### RF-02 — Gestionar unidades y conversiones

| Campo | Información |
|---|---|
| **Código** | RF-02 |
| **Versión** | 1.0 |
| **Descripción** | El sistema deberá registrar la unidad base de cada producto y las equivalencias de sus presentaciones de compra (caja, paquete, etc.), convirtiendo toda entrada y salida a la unidad base. |
| **Justificación** | Mantener un único saldo de inventario por referencia, evitando sub-inventarios separados por presentación. |
| **Precondiciones** | Producto existente con unidad base definida. |
| **Restricciones** | Precisión y redondeo configurables por país y producto; el inventario nunca se negativiza. |
| **Prioridad** | Alta |
| **Dependencias** | RF-01, RN-02 |
| **Actores** | Responsable de inventario. |
| **Entradas** | Unidad base, presentaciones, factor de conversión de cada presentación a la unidad base. |
| **Proceso** | Convertir toda cantidad de compra o venta a la unidad base antes de afectar el saldo. |
| **Salida** | Saldo expresado siempre en la unidad base del producto. |
| **Postcondiciones** | El saldo del producto refleja la suma de entradas y salidas ya convertidas. |
| **Criterios de aceptación** | Una compra de 3 cajas de 100 unidades incrementa el saldo en 300 unidades; una conversión no configurada se rechaza. |
| **Requisitos no funcionales** | RNF-02 |
| **Estado** | Aprobado |

### RF-03 — Registrar compras

| Campo | Información |
|---|---|
| **Código** | RF-03 |
| **Versión** | 1.0 |
| **Descripción** | El sistema deberá registrar compras con una o varias líneas por proveedor, representando **lo que se pidió**, de forma independiente de lo que efectivamente llegue (ver RF-17, Recepción). |
| **Justificación** | Separar el pedido de la recepción evita inconsistencias cuando la entrega es parcial. |
| **Precondiciones** | Producto y proveedor identificados; usuario autorizado. |
| **Restricciones** | Una compra no afecta el saldo de inventario por sí sola; el saldo solo aumenta con la recepción (RF-17). |
| **Prioridad** | Alta |
| **Dependencias** | RF-01, RF-02, RF-06, RF-17 |
| **Actores** | Personal de compras. |
| **Entradas** | Proveedor, líneas (producto, cantidad pedida, costo pactado). |
| **Proceso** | Validar y registrar la compra con sus líneas; dejarla disponible para recepción. |
| **Salida** | Compra consultable, con estado de avance de recepción por línea (ej. 30/50 recibidos). |
| **Postcondiciones** | Compra y sus líneas quedan trazables hasta la recepción y el documento de ingreso. |
| **Criterios de aceptación** | Una compra registrada no altera el inventario hasta que exista una recepción asociada. |
| **Requisitos no funcionales** | RNF-02, RNF-04, RNF-06 |
| **Estado** | Aprobado |

### RF-04 — Registrar ventas y facturación

| Campo | Información |
|---|---|
| **Código** | RF-04 |
| **Versión** | 1.0 |
| **Descripción** | El sistema deberá registrar ventas, descontar existencias disponibles de forma atómica y emitir la factura correspondiente (electrónica o impresa, según normativa vigente de la DIAN). |
| **Justificación** | Asociar cada salida de inventario con su venta, cliente y factura, sin permitir saldos negativos. |
| **Precondiciones** | Producto y cliente identificados; saldo disponible suficiente; usuario autorizado; conexión a internet activa (RN-10). |
| **Restricciones** | Ninguna venta se confirma sin internet; en ventas concurrentes gana la primera confirmada exitosamente (RN-11); el número interno de venta y la numeración fiscal DIAN son campos distintos (RN-14). |
| **Prioridad** | Alta |
| **Dependencias** | RF-01, RF-02, RF-05, RF-06, RF-07, RF-09 |
| **Actores** | Personal de ventas. |
| **Entradas** | Cliente, líneas (producto, cantidad, precio vigente), impuestos, descuentos. |
| **Proceso** | Verificar saldo disponible; descontar inventario; generar venta y factura (número interno + numeración fiscal). |
| **Salida** | Factura y saldo de inventario actualizado. |
| **Postcondiciones** | Venta, movimiento de inventario y factura quedan correlacionados y auditables. |
| **Criterios de aceptación** | Un saldo insuficiente impide confirmar la venta; una venta confirmada descuenta el inventario una sola vez; ante fallo, no queda factura confirmada sin su salida correspondiente. |
| **Requisitos no funcionales** | RNF-01, RNF-02, RNF-04 |
| **Estado** | Aprobado |

### RF-05 — Gestionar estados de inventario, vencimientos y garantías

| Campo | Información |
|---|---|
| **Código** | RF-05 |
| **Versión** | 1.0 |
| **Descripción** | El sistema deberá mantener las existencias en disponible, reservado y dañado; gestionar el ciclo de reservas y de productos dañados; y controlar vencimiento/garantía con rastreo parametrizable (ninguno, lote o serie) por producto, dando de baja únicamente la unidad o lote afectado. |
| **Justificación** | Evitar vender cantidades no disponibles y conservar trazabilidad de condiciones especiales del producto. |
| **Precondiciones** | Referencia existente; usuario autorizado. |
| **Restricciones** | La suma de disponible + reservado + dañado siempre es igual al total físico; una reserva no puede exceder el disponible. |
| **Prioridad** | Alta |
| **Dependencias** | RF-01, RN-03, RN-04, RN-05 |
| **Actores** | Personal de ventas (crea reservas); responsable de inventario (clasifica dañados, gestiona vencimiento). |
| **Entradas** | Producto, cantidad, tipo de transición (reservar, liberar, dañar, recuperar, vencer), cliente/pedido/fecha límite cuando aplique. |
| **Proceso** | Validar la transición contra el saldo de origen; registrar el nuevo estado; en caso de vencimiento, dar de baja solo la unidad/lote afectado. |
| **Salida** | Saldos por estado, historial de reservas y de productos dañados/vencidos. |
| **Postcondiciones** | Toda transición queda auditada; ningún estado queda negativo. |
| **Criterios de aceptación** | Una reserva se libera únicamente por venta, cancelación autorizada o vencimiento de fecha límite; una unidad dañada nunca se elimina, solo cambia de estado; el vencimiento de un lote no afecta el resto de la referencia. |
| **Requisitos no funcionales** | RNF-02, RNF-06 |
| **Estado** | Aprobado |

### RF-06 — Gestionar terceros (clientes y proveedores)

| Campo | Información |
|---|---|
| **Código** | RF-06 |
| **Versión** | 1.0 |
| **Descripción** | El sistema deberá registrar terceros bajo una entidad general que admite el rol de cliente, proveedor o ambos, con campos diferenciados según sea persona natural o jurídica. |
| **Justificación** | Identificar de forma única a los participantes de compras y ventas, sin duplicar registros cuando una misma persona cumple ambos roles. |
| **Precondiciones** | Usuario autorizado. |
| **Restricciones** | Persona natural: tipo y número de identificación, primer nombre, segundo nombre, primer apellido, segundo apellido, dirección, ciudad, teléfono, correo. Persona jurídica: NIT, razón social, dirección, ciudad, teléfono, correo. No se exige apellido a una persona jurídica ni razón social a una persona natural. |
| **Prioridad** | Alta |
| **Dependencias** | RN-06 |
| **Actores** | Personal de compras y ventas. |
| **Entradas** | Tipo de persona, identificación, datos de contacto, roles (cliente/proveedor). |
| **Proceso** | Validar campos obligatorios según tipo de persona; registrar o actualizar el tercero. |
| **Salida** | Ficha de tercero consultable, con sus roles vigentes. |
| **Postcondiciones** | El tercero queda disponible para compras, ventas, reservas y devoluciones. |
| **Criterios de aceptación** | Un tercero puede tener simultáneamente el rol de cliente y proveedor; el sistema rechaza el registro que incumpla los campos obligatorios de su tipo de persona. |
| **Requisitos no funcionales** | RNF-03 |
| **Estado** | Aprobado |

### RF-07 — Calcular y proponer precios

| Campo | Información |
|---|---|
| **Código** | RF-07 |
| **Versión** | 1.0 |
| **Descripción** | El sistema deberá calcular el costo promedio ponderado de cada producto y proponer un precio aplicando el porcentaje de incremento definido, sin convertirlo automáticamente en precio vigente (ver RF-16, aprobación). |
| **Justificación** | Mantener el precio trazable frente a variaciones de costo, con control humano sobre el precio final. |
| **Precondiciones** | Producto con historial de compras/costos. |
| **Restricciones** | El costo se recalcula como promedio ponderado en cada recepción; el precio propuesto no se aplica sin aprobación. |
| **Prioridad** | Alta |
| **Dependencias** | RF-01, RF-03, RF-17, RN-07, RF-16 |
| **Actores** | Responsable de inventario (propone/calcula). |
| **Entradas** | Costo de cada recepción, cantidad recibida, porcentaje de incremento del producto. |
| **Proceso** | Recalcular costo promedio ponderado; aplicar el porcentaje; generar propuesta de precio. |
| **Salida** | Costo promedio vigente, precio propuesto e historial de ambos. |
| **Postcondiciones** | El historial de costos y precios permite recuperar cualquier valor anterior. |
| **Criterios de aceptación** | Ante los mismos datos de entrada, el costo promedio y el precio propuesto son reproducibles y coinciden con el cálculo aprobado (ej.: 10 unidades a $10.000 + 10 a $14.000 → costo promedio $12.000; con margen del 30% → precio propuesto $15.600). |
| **Requisitos no funcionales** | RNF-03, RNF-06 |
| **Estado** | Aprobado |

### RF-08 — Auditar movimientos y ejecutar cierres

| Campo | Información |
|---|---|
| **Código** | RF-08 |
| **Versión** | 1.0 |
| **Descripción** | El sistema deberá registrar en auditoría todo movimiento de inventario (compra, venta, devolución, ajuste, conteo, cambio de estado) y permitir cierres mensuales. |
| **Justificación** | Reconstruir cualquier transacción y controlar los períodos contables/operativos. |
| **Precondiciones** | Movimiento registrado; responsable autorizado para cierres. |
| **Restricciones** | Un movimiento histórico nunca se edita directamente (RN-16); solo se corrige mediante nota de ajuste. |
| **Prioridad** | Alta |
| **Dependencias** | RF-03, RF-04, RF-05, RF-13, RF-14, RF-15, RN-08, RN-09 |
| **Actores** | Responsable de inventario (genera movimientos); responsable de cierre/gerencia (cierra períodos); administrador/gerencia (consulta auditoría). |
| **Entradas** | ID del movimiento, tipo, usuario, fecha/hora, IP, documento relacionado, valor anterior, valor nuevo, motivo. |
| **Proceso** | Registrar el evento auditable en el momento de cada movimiento; al cerrar un período, validar pendientes y dejar constancia del cierre. |
| **Salida** | Historial de auditoría consultable y constancia de cierre por período. |
| **Postcondiciones** | Todo movimiento confirmado queda rastreable hasta su actor, documento y motivo. |
| **Criterios de aceptación** | Cada movimiento auditado contiene los ocho campos definidos; una corrección se refleja como nuevo movimiento vinculado al original, nunca como edición. |
| **Requisitos no funcionales** | RNF-03, RNF-04, RNF-06 |
| **Estado** | Aprobado |

### RF-09 — Administrar usuarios y permisos

| Campo | Información |
|---|---|
| **Código** | RF-09 |
| **Versión** | 1.0 |
| **Descripción** | El sistema deberá administrar usuarios y aplicar la matriz de permisos por rol. |
| **Justificación** | Restringir operaciones según el rol y atribuir cada movimiento a un usuario responsable. |
| **Precondiciones** | Administrador autorizado. |
| **Restricciones** | Matriz de permisos según la tabla siguiente. |
| **Prioridad** | Alta |
| **Dependencias** | RNF-03 |
| **Actores** | Administrador. |
| **Entradas** | Identidad del usuario, rol asignado, estado de la cuenta. |
| **Proceso** | Crear, modificar o revocar accesos; validar cada operación contra la matriz de permisos. |
| **Salida** | Permisos aplicados y auditados. |
| **Postcondiciones** | Cada operación queda atribuida a un usuario con permiso vigente en el momento de ejecutarla. |
| **Criterios de aceptación** | Un usuario sin permiso no puede ejecutar la operación restringida; todo cambio de permiso queda registrado. |
| **Requisitos no funcionales** | RNF-03 |
| **Estado** | Aprobado |

**Matriz de roles y permisos:**

| Rol | Compras | Ventas | Inventario | Precios | Usuarios | Auditoría | Cierres |
|---|---|---|---|---|---|---|---|
| Ventas | Ver | Sí | Consultar | No | No | No | No |
| Compras | Sí | No | Consultar | No | No | No | No |
| Inventario | Sí | Consultar | Sí | Proponer | No | Consultar | No |
| Administrador | Sí | Sí | Sí | Según autorización | Sí | Sí | No |
| Gerencia / Responsable de cierre | Consultar | Consultar | Consultar | Aprobar | No | Sí | Sí |

### RF-10 — Migrar datos desde Excel

| Campo | Información |
|---|---|
| **Código** | RF-10 |
| **Versión** | 1.0 |
| **Descripción** | El sistema deberá validar, detectar posibles duplicados y consolidar la información de un archivo Excel antes de importarla; nunca se importa directamente. |
| **Justificación** | Conservar la información existente sin arrastrar duplicados ni inconsistencias al catálogo. |
| **Precondiciones** | Archivo y mapeo de columnas aprobados. |
| **Restricciones** | Ninguna fila se importa sin pasar por el flujo Validar → Detectar errores → Detectar duplicados → Revisión de usuario → Consolidar → Importar. |
| **Prioridad** | Media |
| **Dependencias** | RF-01, RF-02, RF-06, RN-15 |
| **Actores** | Responsable de migración / inventario. |
| **Entradas** | Archivo Excel. |
| **Proceso** | Ejecutar el flujo de validación y consolidación; presentar posibles duplicados (ej.: "Taladro Bosch 500W" vs "Taladro Bosch 500 W") para decisión del usuario antes de importar. |
| **Salida** | Resumen de carga, filas aceptadas y filas rechazadas/observadas. |
| **Postcondiciones** | Los datos aceptados quedan conciliados con el archivo de origen. |
| **Criterios de aceptación** | Ninguna fila se importa sin pasar por las cinco etapas del flujo; los posibles duplicados quedan señalados para revisión antes de consolidar. |
| **Requisitos no funcionales** | RNF-01, RNF-04 |
| **Estado** | Aprobado |

### RF-11 — Integrar lectores y sistemas externos

| Campo | Información |
|---|---|
| **Código** | RF-11 |
| **Versión** | 1.0 |
| **Descripción** | El sistema deberá aceptar la lectura de códigos de barras y QR, utilizando una base de datos única como origen de clientes, productos y facturas. |
| **Justificación** | Agilizar la captura de información y mantener consistencia con el sistema de origen. |
| **Precondiciones** | Dispositivos lectores disponibles. |
| **Restricciones** | Protocolos e interfaces de integración con otros sistemas quedan como alcance de una fase de detalle posterior; no bloquean el diseño base de datos. |
| **Prioridad** | Media |
| **Dependencias** | RF-01, RN-01 |
| **Actores** | Usuario operativo; lector de código de barras/QR. |
| **Entradas** | Código leído. |
| **Proceso** | Buscar la referencia asociada al código y devolverla al proceso que la solicitó (compra, venta, inventario). |
| **Salida** | Referencia identificada o mensaje de código no encontrado. |
| **Postcondiciones** | No se duplican operaciones por reintentos de lectura. |
| **Criterios de aceptación** | Un código conocido recupera siempre la misma referencia; un reenvío del mismo evento no genera una segunda operación. |
| **Requisitos no funcionales** | RNF-01, RNF-05 |
| **Estado** | Aprobado |

### RF-12 — Consultar información sin conexión (modificado)

| Campo | Información |
|---|---|
| **Código** | RF-12 |
| **Versión** | 2.0 (reemplaza a "Operar sin internet" de la v0.1) |
| **Descripción** | Sin conexión a internet, el sistema deberá permitir **únicamente consulta** del catálogo y de información previamente sincronizada. Ninguna transacción (venta, compra, factura, reserva, movimiento de inventario) se confirma sin conexión. |
| **Justificación** | Resolver la contradicción entre "ninguna transacción sin internet" y "documentos fuera de línea": se prioriza la primera regla y se elimina el alcance de sincronización de transacciones. |
| **Precondiciones** | Catálogo e información previamente sincronizados en el dispositivo. |
| **Restricciones** | No existen operaciones offline confirmadas; no aplica numeración de documentos fuera de línea. |
| **Prioridad** | Media |
| **Dependencias** | RF-01, RN-10 |
| **Actores** | Personal operativo. |
| **Entradas** | Consulta de catálogo o información local. |
| **Proceso** | Responder la consulta con la información disponible localmente; bloquear cualquier intento de confirmar una transacción sin conexión. |
| **Salida** | Resultado de consulta o mensaje de operación no disponible sin conexión. |
| **Postcondiciones** | Ningún dato se modifica sin conexión. |
| **Criterios de aceptación** | Sin conexión: consultar catálogo → permitido; vender, comprar, modificar inventario o confirmar factura → rechazado con mensaje explícito. |
| **Requisitos no funcionales** | RNF-02, RNF-05 |
| **Estado** | Aprobado (modificado) |

### RF-13 — Registrar devoluciones

| Campo | Información |
|---|---|
| **Código** | RF-13 |
| **Versión** | 1.0 |
| **Descripción** | El sistema deberá registrar devoluciones de cliente (aumentan el disponible si el producto vuelve a estar en condiciones de venta) y devoluciones a proveedor (disminuyen el inventario), siempre vinculadas al documento original. |
| **Justificación** | Mantener trazabilidad entre la venta o compra original y su devolución. |
| **Precondiciones** | Documento original (venta o compra) existente; devolución autorizada. |
| **Restricciones** | Una devolución rechazada no modifica saldos. |
| **Prioridad** | Media |
| **Dependencias** | RF-03, RF-04, RN-08 |
| **Actores** | Responsable de inventario / ventas autorizado. |
| **Entradas** | Documento original, producto, cantidad, motivo. |
| **Proceso** | Validar autorización; registrar el movimiento vinculado; actualizar el saldo correspondiente. |
| **Salida** | Devolución registrada con su efecto en inventario. |
| **Postcondiciones** | Se conserva el vínculo entre el documento original y la devolución. |
| **Criterios de aceptación** | Una devolución autorizada conserva vínculo, usuario y cambio de saldo; una rechazada no altera saldos. |
| **Requisitos no funcionales** | RNF-06 |
| **Estado** | Aprobado |

### RF-14 — Registrar ajustes

| Campo | Información |
|---|---|
| **Código** | RF-14 |
| **Versión** | 1.0 |
| **Descripción** | El sistema deberá registrar ajustes de inventario (positivos o negativos), cada uno con autorización y motivo, vinculados al movimiento que rectifican cuando aplique. |
| **Justificación** | Corregir el inventario sin perder trazabilidad ni modificar movimientos históricos. |
| **Precondiciones** | Usuario autorizado; motivo del ajuste. |
| **Restricciones** | Ningún saldo resultante puede ser negativo. |
| **Prioridad** | Media |
| **Dependencias** | RN-08, RN-16 |
| **Actores** | Responsable de inventario (registra); gerencia (autoriza según matriz). |
| **Entradas** | Producto, cantidad, signo, motivo, documento relacionado (si aplica). |
| **Proceso** | Validar autorización; registrar el ajuste como nuevo movimiento; actualizar saldo. |
| **Salida** | Ajuste registrado y auditado. |
| **Postcondiciones** | Se puede reconstruir el original, el motivo, la nota y el cambio antes/después. |
| **Criterios de aceptación** | Todo ajuste queda vinculado a un motivo y a un usuario autorizador; ningún ajuste deja saldos negativos. |
| **Requisitos no funcionales** | RNF-06 |
| **Estado** | Aprobado |

### RF-15 — Registrar conteos físicos

| Campo | Información |
|---|---|
| **Código** | RF-15 |
| **Versión** | 1.0 |
| **Descripción** | El sistema deberá registrar conteos físicos y la diferencia frente al saldo del sistema, sin modificar el inventario automáticamente. |
| **Justificación** | Detectar diferencias entre el saldo del sistema y la existencia real, dejando la corrección sujeta a aprobación. |
| **Precondiciones** | Producto con saldo registrado en el sistema. |
| **Restricciones** | El conteo no genera un ajuste de inventario por sí solo. |
| **Prioridad** | Media |
| **Dependencias** | RF-14 |
| **Actores** | Responsable de inventario (realiza el conteo); gerencia (aprueba el ajuste resultante). |
| **Entradas** | Producto, cantidad física contada. |
| **Proceso** | Comparar el conteo contra el saldo del sistema; presentar la diferencia; si se aprueba, generar el ajuste correspondiente (RF-14). |
| **Salida** | Diferencia de conteo y, si aplica, ajuste aprobado. |
| **Postcondiciones** | El saldo solo cambia tras la aprobación del ajuste derivado del conteo. |
| **Criterios de aceptación** | Se presenta la diferencia entre conteo y saldo del sistema; el inventario no cambia hasta que el ajuste derivado sea aprobado. |
| **Requisitos no funcionales** | RNF-06 |
| **Estado** | Aprobado |

### RF-16 — Gestionar aprobación de precios

| Campo | Información |
|---|---|
| **Código** | RF-16 |
| **Versión** | 1.0 |
| **Descripción** | El sistema deberá separar la propuesta/modificación de precio de su aprobación, manteniendo el historial de costo promedio, porcentaje y precio. |
| **Justificación** | Evitar que un cambio de precio quede vigente sin control de un usuario autorizado. |
| **Precondiciones** | Precio propuesto por RF-07. |
| **Restricciones** | Solo un usuario autorizado según la matriz de roles puede aprobar (rol Gerencia: "Aprobar"; rol Inventario: "Proponer"). |
| **Prioridad** | Alta |
| **Dependencias** | RF-07, RF-09, RN-07 |
| **Actores** | Responsable de inventario (propone); gerencia (aprueba). |
| **Entradas** | Precio propuesto, usuario aprobador. |
| **Proceso** | Presentar la propuesta; registrar la decisión de aprobación o rechazo; si se aprueba, actualizar el precio vigente y su historial. |
| **Salida** | Precio vigente actualizado e historial de precios. |
| **Postcondiciones** | El precio efectivo solo cambia tras la aprobación. |
| **Criterios de aceptación** | Un precio propuesto no aprobado no afecta las ventas; el historial conserva valor, fecha y usuario responsable de cada cambio. |
| **Requisitos no funcionales** | RNF-03, RNF-06 |
| **Estado** | Aprobado |

### RF-17 — Gestionar recepción de compras

| Campo | Información |
|---|---|
| **Código** | RF-17 |
| **Versión** | 1.0 |
| **Descripción** | El sistema deberá asociar una o varias recepciones (parciales o totales) a las líneas de una compra, generando un documento de ingreso por cada recepción, respaldado por la factura del proveedor y con un consecutivo propio. |
| **Justificación** | Registrar lo que efectivamente llega, de forma independiente de lo que se pidió (RF-03), y aumentar el inventario solo por lo recibido. |
| **Precondiciones** | Compra registrada (RF-03). |
| **Restricciones** | El documento de ingreso solo admite los estados **Activo** y **Anulado** (RN-09); anular requiere motivo y nota de soporte. |
| **Prioridad** | Alta |
| **Dependencias** | RF-01, RF-02, RF-03, RF-06, RF-07, RN-09 |
| **Actores** | Responsable de inventario / compras. |
| **Entradas** | Compra de referencia, líneas recibidas, cantidades, factura del proveedor. |
| **Proceso** | Validar la recepción contra las líneas pendientes de la compra; generar el documento de ingreso con su consecutivo; aumentar el inventario y actualizar el costo promedio (RF-07). |
| **Salida** | Documento de ingreso y saldo de inventario actualizado. |
| **Postcondiciones** | El estado de avance de la compra (recibido/pedido) queda actualizado por línea. |
| **Criterios de aceptación** | Cada recepción afecta el saldo solo por la cantidad efectivamente recibida y conserva trazabilidad con la compra y la factura del proveedor; un documento de ingreso anulado conserva su consecutivo y no se elimina. |
| **Requisitos no funcionales** | RNF-02, RNF-04, RNF-06 |
| **Estado** | Aprobado |

### RF-18 — Consultar el detalle de facturas de venta

| Campo | Información |
|---|---|
| **Código** | RF-18 |
| **Versión** | 1.0 |
| **Descripción** | El sistema deberá mostrar el detalle completo de una venta y su factura: número interno, numeración fiscal, modalidad, fecha, vendedor, cliente, líneas (presentación, cantidad, precio, descuento, IVA, total, garantía, cantidad devuelta), totales, devoluciones asociadas y trazabilidad (movimientos y eventos de auditoría). |
| **Justificación** | Atender reclamos, garantías y devoluciones con la información real de la venta. |
| **Precondiciones** | Venta registrada (RF-04); usuario con permiso de consulta de ventas (RF-09). |
| **Restricciones** | Solo lectura; los costos no se muestran a roles sin permiso. |
| **Prioridad** | Alta |
| **Dependencias** | RF-04, RF-08, RF-09, RF-13 |
| **Actores** | Ventas, inventario, administrador, gerencia. |
| **Entradas** | Venta seleccionada desde el listado (filtros por número, factura, fechas, estado). |
| **Proceso** | Consultar la venta, su factura, el cliente, las devoluciones por línea, los movimientos y los eventos. |
| **Salida** | Pantalla de detalle con acceso al comprobante (RF-19) y a la devolución (RF-13). |
| **Postcondiciones** | Ninguna (consulta). |
| **Criterios de aceptación** | Desde el listado se abre el detalle; los totales coinciden con los almacenados; se ve lo ya devuelto por línea. |
| **Requisitos no funcionales** | RNF-03, RNF-06 |
| **Estado** | Aprobado — implementado (ver sección 9) |

### RF-19 — Previsualizar y exportar facturas de venta

| Campo | Información |
|---|---|
| **Código** | RF-19 |
| **Versión** | 1.0 |
| **Descripción** | El sistema deberá previsualizar, imprimir y exportar a PDF el documento de venta, distinguiendo un **comprobante interno** de una **factura electrónica con validez fiscal**. |
| **Justificación** | Entregar soporte al cliente sin presentar como factura fiscal un documento que no lo es (RN-14). |
| **Precondiciones** | Venta activa con líneas, factura y cliente (RF-04, RF-18). |
| **Restricciones** | Sin integración DIAN validada el documento se titula “Comprobante interno de venta” y lleva la leyenda de que no tiene validez fiscal; un documento anulado o incompleto no se imprime ni exporta. |
| **Prioridad** | Alta |
| **Dependencias** | RF-04, RF-18, RN-14 |
| **Actores** | Ventas, inventario, administrador, gerencia. |
| **Entradas** | Venta seleccionada. |
| **Proceso** | Validar que el documento sea exportable; componer la vista con datos de empresa, cliente, líneas y totales; imprimir o generar el PDF. |
| **Salida** | Vista imprimible y archivo `comprobante-<número>.pdf`. |
| **Postcondiciones** | Ninguna (no modifica la venta). |
| **Criterios de aceptación** | El PDF contiene el número interno, la numeración, los totales y la leyenda correcta; una venta anulada muestra los motivos y bloquea la exportación. |
| **Requisitos no funcionales** | RNF-05 |
| **Estado** | Aprobado — implementado como comprobante interno; factura electrónica pendiente de integración DIAN (sección 11) |

### RF-20 — Consultar la auditoría desde la interfaz

| Campo | Información |
|---|---|
| **Código** | RF-20 |
| **Versión** | 1.0 |
| **Descripción** | El sistema deberá permitir consultar desde la interfaz los eventos de auditoría (quién, cuándo, desde qué IP, módulo, acción, entidad y detalle del cambio) y los movimientos de inventario, con filtros, paginación, detalle y exportación. |
| **Justificación** | Hacer verificable la trazabilidad exigida por RF-08 y RNF-06 sin acceso directo a la base de datos. |
| **Precondiciones** | Usuario con rol Inventario, Administrador o Gerencia. |
| **Restricciones** | Solo lectura; los registros son inmutables; los datos sensibles (contraseñas, tokens) no se registran. |
| **Prioridad** | Alta |
| **Dependencias** | RF-08, RF-09, RNF-06 |
| **Actores** | Inventario, administrador, gerencia. |
| **Entradas** | Filtros: fechas, usuario, módulo, acción, entidad, texto; para movimientos: producto, tipo, documento, fechas. |
| **Proceso** | Consultar paginado en la zona horaria del negocio; mostrar el detalle del evento. |
| **Salida** | Tablas paginadas, detalle JSON y Excel de movimientos. |
| **Postcondiciones** | La exportación queda registrada como evento. |
| **Criterios de aceptación** | Un evento recién generado aparece al filtrar por su módulo; un rol sin permiso no accede. |
| **Requisitos no funcionales** | RNF-03, RNF-06 |
| **Estado** | Aprobado — implementado |

### RF-21 — Gestionar reservas de inventario (identificado en la auditoría de 2026-10-16)

| Campo | Información |
|---|---|
| **Código** | RF-21 |
| **Versión** | 1.0 |
| **Descripción** | El sistema deberá registrar reservas (cliente, pedido, producto, cantidad, fecha límite) que pasan cantidades de disponible a reservado, y liberarlas solo por venta asociada, cancelación autorizada con motivo o vencimiento de la fecha límite. |
| **Justificación** | RN-03 define la reserva y sus causas de liberación, pero ningún RF la operaba. |
| **Precondiciones** | Saldo disponible suficiente; cliente registrado. |
| **Restricciones** | No existe liberación “por compra” (decisión 4 de la sección 6); sin conexión no se crean reservas (RN-10). |
| **Prioridad** | Media |
| **Dependencias** | RF-04, RF-05, RF-06, RN-03 |
| **Actores** | Ventas (crear), ventas/inventario/administrador (cancelar), proceso diario (vencimiento). |
| **Entradas** | Cliente, producto, pedido, cantidad, fecha límite. |
| **Proceso** | Mover disponible → reservado con movimiento auditable; al vender con la reserva, consumirla; al cancelar o vencer, devolver a disponible. |
| **Salida** | Reserva con estado Activa o Liberada (por venta, cancelación o vencimiento). |
| **Postcondiciones** | Saldos por estado consistentes con los movimientos. |
| **Criterios de aceptación** | Una reserva vencida se libera automáticamente; una venta con reserva no descuenta dos veces. |
| **Requisitos no funcionales** | RNF-02, RNF-06 |
| **Estado** | Propuesto e implementado — requiere aprobación del interesado |

### RF-22 — Exportar información a Excel (identificado en la auditoría de 2026-10-16)

| Campo | Información |
|---|---|
| **Código** | RF-22 |
| **Versión** | 1.0 |
| **Descripción** | El sistema deberá exportar a Excel productos, existencias, terceros, ventas y movimientos, con los mismos permisos de la consulta en pantalla y protección contra inyección de fórmulas. |
| **Justificación** | RF-10 cubre la entrada de datos desde Excel; la salida no estaba especificada. |
| **Precondiciones** | Usuario con permiso de consulta del módulo. |
| **Restricciones** | Costos solo para roles autorizados; máximo 50 000 filas por archivo; celdas que empiezan por `= + - @`, tabulador o retorno se neutralizan. |
| **Prioridad** | Media |
| **Dependencias** | RF-01, RF-05, RF-06, RF-08, RF-09 |
| **Actores** | Según módulo. |
| **Entradas** | Módulo y filtros (fechas, producto, tipo). |
| **Proceso** | Consultar por páginas, escribir el libro en flujo, auditar la exportación. |
| **Salida** | Archivo `.xlsx`. |
| **Postcondiciones** | Evento de exportación en la auditoría. |
| **Criterios de aceptación** | Un texto `=1+1` se exporta como texto; un rol sin permiso recibe 403. |
| **Requisitos no funcionales** | RNF-03 |
| **Estado** | Propuesto e implementado — requiere aprobación del interesado |


---

## 5. Requisitos no funcionales (ISO/IEC 25010:2023)

La norma aporta el modelo de calidad; los umbrales numéricos se acuerdan como escenarios de diseño antes de las pruebas de aceptación (RN-12), sustituyendo la expresión "sin límite".

| Código | Tipo | Descripción | Prioridad |
|---|---|---|---|
| RNF-01 | Eficiencia de desempeño | El sistema deberá atender consultas por código, compras, ventas e importación bajo escenarios de carga acordados. Propuesta de referencia (a validar): hasta 100.000 productos, 10.000 movimientos diarios, 50 usuarios simultáneos y 10 años de histórico. | Alta |
| RNF-02 | Fiabilidad e integridad operativa | Ante un error, una compra, venta, recepción o ajuste no deberá dejar documentos y saldos inconsistentes (todo o nada). | Alta |
| RNF-03 | Seguridad y trazabilidad | El sistema deberá autenticar usuarios, aplicar la matriz de permisos (RF-09) y proteger los datos de terceros. | Alta |
| RNF-04 | Continuidad y recuperación | El sistema deberá conservar y recuperar la información tras interrupciones, con objetivos de disponibilidad, RTO y RPO a definir en la fase de diseño técnico. | Media |
| RNF-05 | Compatibilidad y facilidad de uso | El sistema deberá funcionar con los lectores de código de barras/QR aprobados y permitir ejecutar las tareas frecuentes de compra, venta e inventario de forma ágil. | Media |
| RNF-06 | Auditabilidad de movimientos | Cada movimiento deberá dejar evidencia consultable con los ocho campos definidos en RF-08 (ID, tipo, usuario, fecha/hora, IP, documento relacionado, valor anterior, valor nuevo, motivo). | Alta |

---

## 6. Registro de decisiones que cierran las contradicciones de la v0.3

| # | Punto abierto en v0.3 (sección 7) | Decisión que lo cierra |
|---|---|---|
| 1 | Sin internet vs. documentos fuera de línea | No se confirman transacciones sin conexión (RN-10); RF-12 se redefine solo para consulta. Se elimina la numeración de documentos fuera de línea. |
| 2 | Numeración DIAN vs. consecutivo interno | Son campos distintos (RN-14): numeración fiscal DIAN y número interno de venta. Factura electrónica como modalidad principal. |
| 3 | Baja por vencimiento (¿unidad, lote o referencia?) | Se da de baja solo la unidad o lote afectado (RN-05); la referencia comercial se mantiene. |
| 4 | Liberación de reserva "por compra" | Se elimina; una reserva se libera solo por venta asociada, cancelación autorizada o vencimiento de la fecha límite (RN-03). |
| 5 | Campos obligatorios de terceros por tipo de persona | Persona natural y persona jurídica tienen conjuntos de campos distintos; ninguna se obliga a los campos de la otra (RN-06, RF-06). |
| 6 | Método de costo promedio | Costo promedio ponderado, recalculado en cada recepción (RN-07, RF-07). |
| 7 | Duración de auditoría e historial | Retención diferenciada por dominio: facturas 10 años, auditoría según política a definir, productos mientras estén en uso, clientes según necesidad legal/operativa (RN-13). |
| 8 | "Sin límite" en capacidad | Se sustituye por escenarios de carga medibles, a validar antes de aceptación (RN-12, RNF-01). |
| 9 | "Primera transacción" en concurrencia | Es la primera **confirmada exitosamente**, no la que inició primero (RN-11). |
| 10 | Integración y Excel | El Excel nunca se importa directamente: pasa por validación, detección de duplicados, revisión de usuario y consolidación (RN-15, RF-10). |
| 11 | Devoluciones, ajustes y conteos | Se formalizan como movimientos autorizados y controlados (RF-13, RF-14, RF-15, RN-08); el conteo nunca ajusta automáticamente. |
| 12 | Estado "borrado" en documento de ingreso | Se reemplaza por **Activo/Anulado**, con motivo, nota y conservación del consecutivo y la auditoría (RN-09). |
| 13 | Rastreo por lote/serie/unidad | Se parametriza por producto: ninguno, lote o serie — no se exige todo a la vez (RN-05, RF-01). |

---

## 7. Anexo A — Trazabilidad de las preguntas originales

Las preguntas planteadas en la sección 5 del documento base (`levantamiento_inventario_ferreteria.md` v0.3) no se responden nuevamente en este documento: sus respuestas quedaron registradas en la sección 6 de esa versión y fueron formalizadas como reglas de negocio y requisitos funcionales en las secciones 3 y 4 de este documento. El documento base se conserva como evidencia histórica del proceso de levantamiento.

---

## 8. Conclusión

El alcance queda delimitado para no convertir el proyecto en un ERP de inventarios de alcance abierto:

- Una referencia = producto + marca + presentación, con un código de barras único.
- Una unidad base por producto.
- Inventario separado en disponible, reservado y dañado.
- Compras con recepciones parciales, separadas conceptualmente de lo pedido.
- Ventas que descuentan inventario de forma atómica, con concurrencia resuelta por la primera confirmación exitosa.
- Precios calculados desde el costo promedio ponderado, pero vigentes solo tras aprobación.
- Devoluciones, ajustes y conteos como movimientos controlados y auditados.
- Documentos anulados, nunca eliminados.
- Auditoría de todas las operaciones, con corrección mediante nota de ajuste.
- Facturación electrónica conforme a la normativa DIAN vigente.
- Sin transacciones confirmadas sin conexión a internet.
- Capacidad dimensionada por escenarios de carga, no como "sin límite".

Con estas decisiones cerradas, el levantamiento de requisitos queda listo para avanzar al diseño de entidades, relaciones y claves de la base de datos.

---

## 9. Estado de implementación y matriz de trazabilidad (auditoría del 2026-10-16)

Leyenda del estado:
- **Implementado** — código en backend y frontend conectado a la API real, con pruebas automatizadas escritas.
- **Parcial** — implementado con una limitación documentada en la sección 11.

Verificación disponible en el entorno de la auditoría (ver sección 11, P-01): pruebas de base de datos en PostgreSQL 16 real (25/25), pruebas Vitest del frontend (66/66), build de producción del frontend, verificación de tipos del backend y pruebas unitarias JUnit/Mockito del backend ejecutadas con un classpath sin conexión (104/110; las 6 restantes fallan por la versión de Apache POI disponible en ese entorno, no por el código). Las pruebas de integración (`*IT`, Testcontainers) y E2E (Playwright) están escritas y configuradas en GitHub Actions, pero **no se ejecutaron todavía**.

| ID | Requisito | Estado antes | Estado actual | Backend (principal) | Frontend | BD | Pruebas |
|---|---|---|---|---|---|---|---|
| RF-01 | Catálogo de productos | Implementado básico | Implementado: categoría, descripción, IVA, recargo, stock mínimo, meses de garantía, activar/desactivar | `ProductoService`, `ProductoController` | `ProductosPage` | V4 (`producto.*`) | UT-PRO, IT-PRO, FE-VAL, FE-HLP-02, E2E-PRO |
| RF-02 | Unidades y conversiones | Solo API | Implementado: pantalla de presentaciones; ventas y compras por presentación | `UnidadService` | `ProductosPage` (Presentaciones), selectores de línea | — | UT-VEN-07/08, UT-COM-01, IT-VEN-06, FE-TOT-03 |
| RF-03 | Registrar compras | No implementado | Implementado | `CompraService`, `CompraController` | `ComprasPage`, `CompraNuevaPage` | V4 (`compra.*`, `linea_compra.*`) | UT-COM-01/02/09/10, IT-FLU-01, E2E-FLU-01 |
| RF-04 | Ventas y facturación | Parcial (IDs escritos a mano, sin IVA/descuentos) | **Parcial**: selectores, IVA, descuentos, reservas, lotes FEFO, garantía, idempotencia; factura electrónica DIAN no integrada | `VentaService`, `FacturacionPort` | `VentaNuevaPage`, `VentasPage` | V4 (`venta.*`, `linea_venta.*`, `linea_venta_lote`) | UT-VEN-01..18, IT-VEN-01..09, IT-FLU-02, FE-VEN-01..04, E2E-VEN-01, E2E-FLU-04 |
| RF-05 | Estados, vencimientos y garantías | Parcial (saldos) | **Parcial**: estados, lotes con vencimiento, alertas, baja por lote, garantía por venta; rastreo por número de serie individual no implementado | `InventarioService`, `LoteService`, `KardexService` | `InventarioPage` | V4 (`lote`, `movimiento_inventario.estado_inventario/id_lote`) | UT-INV-01..04, UT-LOT-01/02, UT-KAR-01, UT-VEN-15/16/17 |
| RF-06 | Terceros | Implementado | Implementado + selector con búsqueda y exportación | `TerceroService` | `TercerosPage`, `TerceroSelector` | — | UT-TER, IT existentes |
| RF-07 | Calcular y proponer precios | No implementado | Implementado: costo promedio ponderado, recargo/margen/manual, redondeo, propuesta automática al recibir | `CalculadoraPrecio`, `PrecioService` | `PreciosPage` | V4 (`precio.*`) | UT-CAL-01..06/10, UT-PRE-01/02/07 |
| RF-08 | Auditar movimientos y cierres | Parcial (movimientos) | Implementado: eventos funcionales inmutables, cierre mensual con conciliación, bloqueo y reapertura auditada | `EventoAuditoriaService`, `CierreService`, `KardexService` | `CierresPage`, `AuditoriaPage` | V4 (`evento_auditoria`, `cierre_periodo`) | UT-AUD-01, UT-KAR-02, IT-ADM-01/07, IT-VEN-04, FE-CIE-01, pruebas SQL 14 y 18 |
| RF-09 | Usuarios, roles y permisos | Parcial (solo login/roles) | Implementado: administración de usuarios, permisos por endpoint y por pantalla | `UsuarioService`, `SecurityConfig`, `@PreAuthorize` | `UsuariosPage`, `permisos.js`, `RutaPermitida` | V2 | UT-USU-01..04, IT-ADM-02, IT-VEN-07, SEG-*, FE-PER, FE-RUT, E2E-ROL-01..03 |
| RF-10 | Importar desde Excel | No implementado | Implementado (RN-15): plantilla, validación, vista previa, duplicados, confirmación atómica, informe de errores, no reproceso | `ImportacionService`, `Excel` | `ImportacionPage` | V4 (`importacion`) | UT-IMP-01..07, IT-ADM-03, FE-IMP-01/02, FE-HLP-03 |
| RF-11 | Lectores y sistemas externos | No implementado | **Parcial**: lectores de código en modo teclado y búsqueda por código; sin integraciones externas (no se simulan) | `ProductoController` (`/seleccion/codigo`) | `ProductoSelector`, `EntitySelector` | — | FE-SEL-01..03, FE-VEN-02, E2E-FLU-04 |
| RF-12 | Consulta sin conexión | Aviso visual | Implementado (solo consulta): copia local del catálogo, búsqueda local, escrituras bloqueadas | `CatalogoController` | `offline/catalogo.js`, `ProductosPage`, `http.js` | — | IT-ADM-06, FE-OFF-01/02, FE-VEN-04, E2E-OFF-01 |
| RF-13 | Devoluciones | No implementado | Implementado: cliente (por línea, estado del producto) y proveedor (por origen y lote) | `DevolucionService` | `DevolucionesPage`, `DevolucionNuevaPage`, detalle de compra | V4 (`documento_devolucion`, `devolucion.*`) | UT-DEV-01..07, UT-CAL-09, FE-HLP-04, E2E-FLU-04/05, prueba SQL 16 |
| RF-14 | Ajustes | No implementado | Implementado: solicitud, aprobación por otra persona, rechazo con motivo | `AjusteService` | `AjustesPage` | V4 (`ajuste_inventario`) | UT-AJU-01..05, IT-FLU-03, FE-AJU-01, prueba SQL 15 |
| RF-15 | Conteos físicos | No implementado | Implementado: sesiones, conteo ciego, cierre, aprobación que genera ajustes | `ConteoService` | `ConteosPage`, `ConteoDetallePage` | V4 (`sesion_conteo`, `conteo_fisico.*`) | UT-CON-01..04, IT-ADM-05 |
| RF-16 | Aprobación de precios | No implementado | Implementado: solo Gerencia, sin autoaprobación, rechazo con motivo, historial | `PrecioService` | `PreciosPage` | V4 (`chk_precio_aprobado_con_aprobador`) | UT-PRE-03..06, FE-PRE-01/02, E2E-FLU-03, verificación SQL de autoaprobación |
| RF-17 | Recepción de compras | No implementado | Implementado: recepciones parciales, lote y vencimiento, documento de ingreso, anulación con reversión | `CompraService.recibir/anularIngreso` | `CompraDetallePage` | V4 (`documento_ingreso.*`, `recepcion.*`) | UT-COM-03..08, IT-FLU-01, FE-REC-01/02, E2E-FLU-02 |
| RF-18 | Detalle de facturas | No implementado | Implementado | `VentaConsultaService.detalle` | `VentaDetallePage` | — | IT-FLU-02, E2E-FLU-04 |
| RF-19 | Previsualizar y exportar facturas | No implementado | **Parcial**: comprobante interno imprimible y PDF; factura electrónica pendiente de DIAN | `VentaConsultaService` (exportable, leyenda) | `ComprobantePage`, `pdf/comprobante.js` | — | FE-CMP-01/02, FE-PDF-01..03, E2E-FLU-04 |
| RF-20 | Auditoría en la interfaz | No implementado | Implementado | `AuditoriaController` (`/eventos`, `/movimientos`) | `AuditoriaPage` | — | IT-ADM-01/07, E2E-ROL-03 |
| RF-21 | Reservas de inventario (nuevo) | No implementado | Implementado | `ReservaService`, `TareasProgramadas` | `InventarioPage` (Reservas), `VentaNuevaPage` | V4 (`reserva.*`) | UT-VEN-18 |
| RF-22 | Exportación a Excel (nuevo) | No implementado | Implementado | `ExportacionService` | `BotonDescarga` en listados | — | UT-IMP-08, IT-ADM-04 |

Reglas de negocio con soporte de base de datos (disparadores y restricciones) y verificaciones de integridad: `database/03_pruebas.sql` (25 pruebas) y `database/04_verificaciones.sql` (saldo por estado = suma de movimientos, lotes ≤ inventario, devoluciones ≤ vendido, totales de ventas, sin autoaprobación de precios).

## 10. Decisiones de implementación

| # | Tema | Decisión |
|---|---|---|
| D-1 | Comprobante vs. factura | Sin integración DIAN validada, el documento es un **comprobante interno** con leyenda explícita; `FacturacionPort.esValidaFiscalmente()` es el único interruptor (RN-14). |
| D-2 | Importes de venta | El servidor calcula precio (vigente aprobado), descuento por línea (máximo configurable), IVA del producto y totales con redondeo HALF_UP a 2 decimales; el cliente solo ve una vista previa. |
| D-3 | IVA de productos existentes | `porcentaje_iva` = 0 por defecto en la migración para no alterar el precio final de productos ya vendidos; se configura por producto. |
| D-4 | Vencimientos | La fecha vive en el **lote**; las ventas usan FEFO y no venden lotes vencidos; el inventario histórico queda sin lote y **no se le inventan fechas**. |
| D-5 | Idempotencia | Ventas, compras, recepciones, devoluciones y ajustes aceptan `idOperacion` único; un reintento devuelve el documento existente. |
| D-6 | Saldos | Un solo servicio (`KardexService`) modifica saldos, con bloqueo por fila, orden fijo por producto, prohibición de negativos y verificación de periodo cerrado. |
| D-7 | Fechas | Se guardan en UTC; “hoy” y los meses de cierre usan la zona del negocio (`America/Bogota`, configurable). |
| D-8 | Cierres | Mensuales; bloquean movimientos del mes; no se cierran con ajustes o conteos pendientes; las diferencias de conciliación exigen observaciones; la reapertura se audita. |
| D-9 | Sin conexión | Solo consulta (RN-10): copia del catálogo en IndexedDB, sin cola de escrituras; se borra al cerrar sesión. |
| D-10 | Excel | Nunca se importa directo (RN-15); el mismo archivo (SHA-256) no se reimporta; exportaciones neutralizan fórmulas y se auditan. |
| D-11 | Segregación de funciones | Precios, ajustes y conteos no los decide quien los propuso, solicitó o inició. |

## 11. Pendientes y limitaciones

| ID | Pendiente | Causa | Impacto | Pasos para completarlo |
|---|---|---|---|---|
| P-01 | Ejecutar `mvn verify` (integración con Testcontainers) y E2E Playwright | El entorno de la auditoría no tenía acceso a Maven Central ni Docker | Los mapeos JPA/consultas JPQL nuevos y los flujos E2E no se han ejecutado contra la aplicación real | Ejecutar el workflow **CI** en GitHub (o `cd backend && mvn verify` y `npm run test:e2e` localmente) y corregir lo que falle |
| P-02 | Factura electrónica DIAN (RF-04, RF-19) | Requiere proveedor tecnológico, credenciales y habilitación ante la DIAN | Los documentos son comprobantes internos sin validez fiscal | Implementar un adaptador de `FacturacionPort` con el proveedor elegido y `esValidaFiscalmente() = true` tras la habilitación |
| P-03 | Rastreo por número de serie individual (RF-05) | El modelo actual trata los productos “serie” como unidades sin registrar cada serial | No se puede consultar qué serial se vendió | Tabla `unidad_serie`, captura del serial en recepción y venta, y validación de unicidad |
| P-04 | Anulación de ventas (RN-09) | No se especificó el flujo (reintegro, nota crédito) | Una venta errónea se corrige con devolución total | Definir con el interesado y añadir `anular` con reversión vinculada (RN-16) |
| P-05 | Costo promedio al anular un ingreso | Al anular un documento de ingreso se revierten cantidades, pero el costo promedio no se recalcula hacia atrás | Diferencia menor en el costo hasta la siguiente recepción | Recalcular el promedio desde los movimientos de compra vigentes |
| P-06 | Avisos de seguridad en dependencias de desarrollo del frontend (vitest/vite/stryker) | Versiones mayores con cambios incompatibles | No afectan la imagen de producción; la CI audita solo dependencias de producción | Actualizar vitest/vite/stryker en una rama dedicada |
| P-07 | Aprobación de RF-21 y RF-22 | Requisitos identificados en la auditoría | Implementados sin aprobación formal | Validarlos con el interesado |
| P-08 | Protección de `main` y permiso de Actions para crear PR | Configuración del repositorio en GitHub | Sin ella, la CI no bloquea fusiones y el workflow de PR automático no puede crear PR | Ver `docs/GUIA_INICIO.md`, sección 11 |
