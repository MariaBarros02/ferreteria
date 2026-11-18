<!--
  Nombre: CASOS_DE_PRUEBA.md
  Descripción: Catálogo completo de casos de prueba (generado desde el código) y plan de pruebas no funcionales; trazabilidad con HU, RN y RNF.
  Fecha de creación: 2026-10-07
  Fecha de actualización: 2026-10-16
  Autor: Equipo Ferretería
  Versión: 1.1.0
-->


# Casos de prueba — Sistema de inventario de ferretería

La tabla de casos automatizados se genera con `python3 scripts/listar_casos.py` a partir de los propios tests (IDs en `@DisplayName`, `it()`, `test()`); si añade un test con ID, vuelva a ejecutarlo.

## Cómo ejecutar cada nivel

| Nivel | Comando |
|---|---|
| Unitarias backend | `cd backend && mvn test` |
| Integración, sistema y seguridad | `cd backend && mvn verify` (requiere Docker: Testcontainers levanta PostgreSQL) |
| Mutación backend (PIT) | `cd backend && mvn org.pitest:pitest-maven:mutationCoverage` (umbral 70 %) |
| Unitarias/componente frontend | `cd frontend && npm test` |
| Mutación frontend (Stryker) | `cd frontend && npm run test:mutation` |
| E2E Playwright (escritorio + móvil) | `cd frontend && npm run test:e2e` (con backend y frontend arriba y `DEMO_PASSWORD` definida) |
| Aceptación Selenium | `cd frontend && npm run test:selenium` |
| Scripts Taiga/métricas | `python3 -m unittest tests/scripts/test_taiga_metricas.py` |
| Reglas de BD (PL/pgSQL) | `database/init-db.sh` (ejecuta `03_pruebas.sql` y `04_verificaciones.sql`) |
| Carga / estrés / límite | `k6 run tests/stress/carga.js`, `limite.js`, `venta-concurrente.js` |
| Todo junto | `scripts/build-test-backend.sh --mutacion` y `scripts/build-test-frontend.sh --e2e --mutacion` |

## Casos automatizados

<!-- CASOS:INICIO -->
Total de casos automatizados: **487**.

### Unitaria

| ID | Caso | Herramienta | Archivo |
|---|---|---|---|
| UT-AJU-01 | solicitar deja el ajuste pendiente y no modifica el saldo | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AjusteConteoServiceTest.java` |
| UT-AJU-02 | aprobar aplica el ajuste y registra el movimiento vinculado | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AjusteConteoServiceTest.java` |
| UT-AJU-03 | quien solicita no puede aprobar su propio ajuste | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AjusteConteoServiceTest.java` |
| UT-AJU-04 | una salida mayor al disponible se rechaza (nunca saldo negativo) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AjusteConteoServiceTest.java` |
| UT-AJU-05 | un ajuste rechazado no altera saldos y no puede volver a decidirse | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AjusteConteoServiceTest.java` |
| UT-AJU-06 | la misma clave de operación devuelve el ajuste existente sin crear otro | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AjusteConteoServiceTest.java` |
| UT-AJU-07 | sin clave de operación no se consulta la idempotencia | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AjusteConteoServiceTest.java` |
| UT-AJU-08 | producto, inventario o ajuste inexistentes responden 404 (NO_ENCONTRADO) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AjusteConteoServiceTest.java` |
| UT-AJU-09 | lote inexistente → 404; lote de otro producto → LOTE_INVALIDO; lote propio se acepta | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AjusteConteoServiceTest.java` |
| UT-AJU-10 | movimiento original inexistente → 404; de otro producto → MOVIMIENTO_AJENO; propio se vincula | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AjusteConteoServiceTest.java` |
| UT-AJU-11 | una salida igual al disponible se puede solicitar y aprobar (deja saldo 0, nunca negativo) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AjusteConteoServiceTest.java` |
| UT-AJU-12 | una entrada mayor que el disponible se aprueba (la validación de saldo solo aplica a salidas) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AjusteConteoServiceTest.java` |
| UT-AJU-13 | si el saldo bajó entre la solicitud y la aprobación, la salida se rechaza al aprobar | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AjusteConteoServiceTest.java` |
| UT-AJU-14 | observaciones: en blanco se guardan como null y con texto se recortan | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AjusteConteoServiceTest.java` |
| UT-AJU-15 | la respuesta muestra producto, solicitante, aprobador, fechas y saldo actual | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AjusteConteoServiceTest.java` |
| UT-AJU-16 | rechazar registra quién decide, cuándo y el motivo recortado | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AjusteConteoServiceTest.java` |
| UT-AJU-17 | rechazar un ajuste cuyo producto ya no existe audita solo el motivo | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AjusteConteoServiceTest.java` |
| UT-AJU-18 | el evento de auditoría lleva el detalle del ajuste (producto, tipo, cantidad, motivo, saldo) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AjusteConteoServiceTest.java` |
| UT-AJU-19 | aprobar con lote ajusta ese lote; producto inexistente al aprobar → 404 | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AjusteConteoServiceTest.java` |
| UT-AJU-20 | producto por lote: una salida sin lote descuenta del lote; una entrada sin lote no toca lotes | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AjusteConteoServiceTest.java` |
| UT-AJU-21 | obtener y listar: filtros por estado/producto, paginación acotada y número de conteo de origen | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AjusteConteoServiceTest.java` |
| UT-ASI-01 | la base de conocimiento real carga y es válida (aunque el ObjectMapper rechace campos desconocidos) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AsistenteServiceTest.java` |
| UT-ASI-02 | bienvenida con los temas permitidos para el rol, sin temas ocultos | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AsistenteServiceTest.java` |
| UT-ASI-03 | bienvenida y títulos en inglés | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AsistenteServiceTest.java` |
| UT-ASI-04 | pregunta frecuente con enlace a la pantalla y sin aviso de permiso para el rol autorizado | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AsistenteServiceTest.java` |
| UT-ASI-05 | rol sin permiso: misma explicación, aviso con los roles autorizados y sin enlace | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AsistenteServiceTest.java` |
| UT-ASI-06 | aviso en singular para un único rol y en inglés | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AsistenteServiceTest.java` |
| UT-ASI-07 | tema con subtemas: opciones en orden; un tema sin permiso solo informa y oculta su enlace | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AsistenteServiceTest.java` |
| UT-ASI-08 | el tema elegido tiene prioridad sobre el texto; tema inexistente → 404 | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AsistenteServiceTest.java` |
| UT-ASI-09 | mensaje vacío (nulo, en blanco o tema en blanco) → 422 MENSAJE_VACIO | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AsistenteServiceTest.java` |
| UT-ASI-10 | saludo y agradecimiento; sin coincidencia se sugieren los temas principales | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AsistenteServiceTest.java` |
| UT-ASI-11 | empate entre temas → aclaración con las opciones empatadas | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AsistenteServiceTest.java` |
| UT-ASI-12 | preguntas frecuentes variadas llegan al tema correcto | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AsistenteServiceTest.java` |
| UT-ASI-13 | código de barras: precio vigente y existencias con formato colombiano | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AsistenteServiceTest.java` |
| UT-ASI-14 | código inexistente o inactivo → aviso sin error | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AsistenteServiceTest.java` |
| UT-ASI-15 | búsqueda por nombre: prueba el singular, sin precio, vencido sin baja y bajo el mínimo | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AsistenteServiceTest.java` |
| UT-ASI-16 | varios resultados: máximo tres, con viñetas y aviso de más coincidencias | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AsistenteServiceTest.java` |
| UT-ASI-17 | producto inexistente → aviso con la palabra buscada; un término que es tema no se trata como producto | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AsistenteServiceTest.java` |
| UT-ASI-18 | las preguntas de procedimiento no consultan datos | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AsistenteServiceTest.java` |
| UT-ASI-19 | tasa del dólar con fecha y fuente, en español y en inglés | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AsistenteServiceTest.java` |
| UT-ASI-20 | todas las tasas: solo monedas extranjeras activas; sin tasa vigente e inactiva se informan | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AsistenteServiceTest.java` |
| UT-AUD-01 | el detalle de auditoría nunca guarda contraseñas, tokens ni secretos | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/SoporteServiciosTest.java` |
| UT-CAL-01 | RF-07: 10 u a $10.000 + 10 u a $14.000 → costo promedio $12.000 | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CalculosTest.java` |
| UT-CAL-02 | RN-07 recargo: $12.000 con 30 % → precio propuesto $15.600 | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CalculosTest.java` |
| UT-CAL-03 | margen sobre precio: P = C / (1 − m) no es lo mismo que recargo | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CalculosTest.java` |
| UT-CAL-04 | margen del 100 % o más se rechaza | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CalculosTest.java` |
| UT-CAL-05 | redondeo configurable al múltiplo (100) con HALF_UP | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CalculosTest.java` |
| UT-CAL-06 | costo negativo o porcentaje nulo se rechazan | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CalculosTest.java` |
| UT-CAL-07 | línea: descuento sobre el subtotal e IVA sobre la base (redondeo a centavos) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CalculosTest.java` |
| UT-CAL-08 | totales del documento = suma de líneas ya redondeadas | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CalculosTest.java` |
| UT-CAL-09 | devolución parcial: valor proporcional del total persistido de la línea | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CalculosTest.java` |
| UT-CAL-10 | promedio sin existencias previas toma el costo de la entrada | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CalculosTest.java` |
| UT-CIE-01 | resumen de un mes abierto: ventas, devoluciones, compras, movimientos y pendientes | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CierreServiceTest.java` |
| UT-CIE-02 | ajustes y conteos sin decidir aparecen como bloqueantes con su cantidad | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CierreServiceTest.java` |
| UT-CIE-03 | el resumen muestra el estado CERRADO o REABIERTO y los datos del cierre | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CierreServiceTest.java` |
| UT-CIE-04 | la conciliación lista los productos cuyo saldo no coincide con sus movimientos | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CierreServiceTest.java` |
| UT-CIE-05 | cerrar un mes guarda la constancia completa, exige y limpia la observación si hay diferencias | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CierreServiceTest.java` |
| UT-CIE-06 | sin diferencias se cierra sin observación; una observación en blanco se guarda vacía | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CierreServiceTest.java` |
| UT-CIE-07 | con diferencias de conciliación no se cierra sin una observación que las explique | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CierreServiceTest.java` |
| UT-CIE-08 | con ajustes o conteos pendientes el mes no se cierra | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CierreServiceTest.java` |
| UT-CIE-09 | un mes futuro no se cierra; el mes en curso sí | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CierreServiceTest.java` |
| UT-CIE-10 | un mes ya cerrado no se vuelve a cerrar; uno reabierto se re-cierra sobre el mismo registro | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CierreServiceTest.java` |
| UT-CIE-11 | reabrir deja constancia de quién, cuándo y por qué; solo se reabre un mes cerrado | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CierreServiceTest.java` |
| UT-CIE-12 | el período debe tener formato AAAA-MM (se toleran espacios) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CierreServiceTest.java` |
| UT-CIE-13 | el historial lista los cierres del más reciente al más antiguo | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CierreServiceTest.java` |
| UT-CIE-14 | si el resumen no se puede serializar, el cierre se registra igual sin el JSON | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CierreServiceTest.java` |
| UT-COM-01 | crear: 3 cajas x 100 = 300 unidades pedidas y costo por unidad base = 15.000 / 100 | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CompraServiceTest.java` |
| UT-COM-02 | proveedor sin rol de proveedor se rechaza | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CompraServiceTest.java` |
| UT-COM-03 | recepción parcial suma solo lo recibido y recalcula el costo promedio ponderado | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CompraServiceTest.java` |
| UT-COM-04 | no se puede recibir más de lo pendiente (409) y no cambia el inventario | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CompraServiceTest.java` |
| UT-COM-05 | producto por lote exige lote y fecha de vencimiento | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CompraServiceTest.java` |
| UT-COM-06 | recepción con lote registra la fecha de vencimiento en el lote (no en el producto) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CompraServiceTest.java` |
| UT-COM-07 | no se recibe un lote ya vencido | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CompraServiceTest.java` |
| UT-COM-08 | reintento de la misma recepción (misma clave) no vuelve a sumar inventario | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CompraServiceTest.java` |
| UT-COM-09 | una compra anulada no admite recepciones | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CompraServiceTest.java` |
| UT-COM-10 | no se anula una compra con mercancía recibida | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CompraServiceTest.java` |
| UT-COM-11 | compra en USD: el costo se pacta en USD y el costo base del promedio se guarda en COP | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CompraServiceTest.java` |
| UT-COM-12 | crear con la misma clave de operación devuelve la compra existente; sin clave no se consulta | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CompraServiceTest.java` |
| UT-COM-13 | proveedor, producto, compra, línea, documento inexistentes responden 404 | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CompraServiceTest.java` |
| UT-COM-14 | presentación: cantidad y costo de la caja se conservan solo si hubo conversión; observaciones se limpian | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CompraServiceTest.java` |
| UT-COM-15 | la auditoría de una compra en COP no lleva tasa; en USD lleva tasa, fecha y total en COP | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CompraServiceTest.java` |
| UT-COM-16 | el reintento de una recepción devuelve el detalle de la compra | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CompraServiceTest.java` |
| UT-COM-17 | recibir exactamente lo pendiente es válido y deja la compra RECIBIDA | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CompraServiceTest.java` |
| UT-COM-18 | las líneas se procesan ordenadas por producto (mismo orden de bloqueo, evita interbloqueos) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CompraServiceTest.java` |
| UT-COM-19 | anular un ingreso revierte cantidades, lote y línea, y registra quién, cuándo y por qué | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CompraServiceTest.java` |
| UT-COM-20 | anular una compra sin recepciones la deja ANULADA con motivo y fecha | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CompraServiceTest.java` |
| UT-COM-21 | el detalle muestra producto, proveedor, usuario y las recepciones con su lote | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CompraServiceTest.java` |
| UT-COM-22 | compras anteriores a V5 (sin moneda ni tasa) se muestran en COP con tasa 1 | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CompraServiceTest.java` |
| UT-COM-23 | estado de recepción: una compra sin líneas está PENDIENTE | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CompraServiceTest.java` |
| UT-COM-24 | listar: resumen con proveedor y totales, paginación acotada y filtros de la especificación | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/CompraServiceTest.java` |
| UT-CON-01 | registrar un conteo calcula la diferencia y no modifica el inventario | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AjusteConteoServiceTest.java` |
| UT-CON-02 | aprobar la sesión genera el ajuste por la diferencia con tipo de movimiento conteo | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AjusteConteoServiceTest.java` |
| UT-CON-03 | rechazar la sesión no altera saldos | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AjusteConteoServiceTest.java` |
| UT-CON-04 | no se aprueba una sesión abierta ni la aprueba quien la abrió | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AjusteConteoServiceTest.java` |
| UT-DEV-01 | devolución parcial apta vuelve a disponible con importe proporcional (incluye IVA) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/DevolucionServiceTest.java` |
| UT-DEV-02 | producto dañado no se reintegra al disponible: va al saldo no apto | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/DevolucionServiceTest.java` |
| UT-DEV-03 | devoluciones múltiples: la segunda se valida contra lo ya devuelto | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/DevolucionServiceTest.java` |
| UT-DEV-04 | una devolución que excede lo vendido se rechaza y no altera saldos | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/DevolucionServiceTest.java` |
| UT-DEV-05 | la línea debe pertenecer a la venta indicada | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/DevolucionServiceTest.java` |
| UT-DEV-06 | la factura original no se modifica (la venta conserva su total y líneas) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/DevolucionServiceTest.java` |
| UT-DEV-07 | devolución a proveedor desde no apto descuenta el saldo dañado | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/DevolucionServiceTest.java` |
| UT-DEV-08 | la misma clave de operación devuelve la devolución existente (cliente y proveedor) sin mover saldos | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/DevolucionServiceTest.java` |
| UT-DEV-09 | sin clave de operación no se consulta la idempotencia | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/DevolucionServiceTest.java` |
| UT-DEV-10 | venta, compra, línea de compra, producto o devolución inexistentes responden 404 | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/DevolucionServiceTest.java` |
| UT-DEV-11 | devolución de cliente: las líneas se procesan ordenadas por producto (mismo orden de bloqueo) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/DevolucionServiceTest.java` |
| UT-DEV-12 | devolución a proveedor: las líneas se procesan ordenadas por producto (mismo orden de bloqueo) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/DevolucionServiceTest.java` |
| UT-DEV-13 | a proveedor se puede devolver exactamente lo recibido, pero no más | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/DevolucionServiceTest.java` |
| UT-DEV-14 | a proveedor con lote indicado: descuenta del lote según el origen (disponible o no apto) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/DevolucionServiceTest.java` |
| UT-DEV-15 | a proveedor, producto por lote sin lote indicado: solo se devuelve la existencia sin lote del origen | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/DevolucionServiceTest.java` |
| UT-DEV-16 | cliente, producto por lote: el lote vigente vuelve a disponible y el lote vencido va a no apto | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/DevolucionServiceTest.java` |
| UT-DEV-17 | cliente, producto por lote: una devolución parcial usa solo el primer lote necesario | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/DevolucionServiceTest.java` |
| UT-DEV-18 | cliente, producto por lote: un lote ya devuelto por completo se salta | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/DevolucionServiceTest.java` |
| UT-DEV-19 | cliente, producto por lote: lo que no salió de un lote se devuelve como existencia sin lote | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/DevolucionServiceTest.java` |
| UT-DEV-20 | la respuesta de cliente muestra venta, cliente, usuario, fecha, producto y observaciones limpias | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/DevolucionServiceTest.java` |
| UT-DEV-21 | la respuesta de proveedor muestra compra y proveedor; documentos de origen inexistentes no fallan | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/DevolucionServiceTest.java` |
| UT-DEV-22 | obtener, devoluciones de una venta, devuelto por línea y listado con filtros | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/DevolucionServiceTest.java` |
| UT-EXP-01 | productos sin costos: 15 columnas, filas en orden, valores tipados y evento con 2 filas | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ExportacionServiceTest.java` |
| UT-EXP-02 | productos con costos: agrega costo_promedio como columna 16 y su valor | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ExportacionServiceTest.java` |
| UT-EXP-03 | productos sin registros: solo encabezado y evento con 0 filas | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ExportacionServiceTest.java` |
| UT-EXP-04 | existencias sin costos: 13 columnas, alertas unidas y una sola página consultada | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ExportacionServiceTest.java` |
| UT-EXP-05 | existencias con costos: agrega costo_promedio y valor_inventario | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ExportacionServiceTest.java` |
| UT-EXP-06 | existencias recorre exactamente totalPaginas (0 y 1) y se detiene | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ExportacionServiceTest.java` |
| UT-EXP-07 | existencias vacías (0 páginas): solo encabezado y 0 filas | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ExportacionServiceTest.java` |
| UT-EXP-08 | terceros: columnas, nombre por razón social o nombres, teléfono neutralizado | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ExportacionServiceTest.java` |
| UT-EXP-09 | terceros vacío: solo encabezado y 0 filas | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ExportacionServiceTest.java` |
| UT-EXP-10 | ventas: filtros por rango, columnas, fecha ISO, cliente neutralizado y moneda | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ExportacionServiceTest.java` |
| UT-EXP-11 | ventas recorre las páginas 0 y 1 y no pide la 2 | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ExportacionServiceTest.java` |
| UT-EXP-12 | movimientos: filtros y zona de negocio, columnas y valores (original nulo en blanco) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ExportacionServiceTest.java` |
| UT-EXP-13 | movimientos recorre las páginas 0 y 1 y no pide la 2 | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ExportacionServiceTest.java` |
| UT-EXP-14 | productos: con MAX_FILAS + 1 registros exporta exactamente MAX_FILAS | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ExportacionServiceTest.java` |
| UT-EXP-15 | terceros: con MAX_FILAS + 1 registros exporta exactamente MAX_FILAS | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ExportacionServiceTest.java` |
| UT-EXP-16 | existencias: con r == MAX_FILAS todavía consulta una página más (r <= MAX_FILAS) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ExportacionServiceTest.java` |
| UT-EXP-17 | ventas: con r == MAX_FILAS todavía consulta una página más (r <= MAX_FILAS) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ExportacionServiceTest.java` |
| UT-EXP-18 | movimientos: con r == MAX_FILAS todavía consulta una página más (r <= MAX_FILAS) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ExportacionServiceTest.java` |
| UT-IMP-01 | la validación clasifica cada fila sin importar nada | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ImportacionServiceTest.java` |
| UT-IMP-02 | faltan columnas obligatorias → 422 con la lista | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ImportacionServiceTest.java` |
| UT-IMP-03 | un archivo que no es .xlsx válido se rechaza | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ImportacionServiceTest.java` |
| UT-IMP-04 | confirmar importa solo válidas y los posibles duplicados aprobados | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ImportacionServiceTest.java` |
| UT-IMP-05 | sin aprobar el posible duplicado, se omite | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ImportacionServiceTest.java` |
| UT-IMP-06 | un archivo idéntico ya importado no se vuelve a procesar | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ImportacionServiceTest.java` |
| UT-IMP-07 | una importación procesada no se confirma de nuevo | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ImportacionServiceTest.java` |
| UT-IMP-08 | exportación: textos que empiezan por = + - @ se neutralizan (inyección de fórmulas) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ImportacionServiceTest.java` |
| UT-IMP-09 | plantillas: encabezados, fila de ejemplo e instrucciones según el tipo; tipo inválido se rechaza | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ImportacionServiceTest.java` |
| UT-IMP-10 | nombre del archivo: sin nombre usa archivo.xlsx, quita rutas y se recorta a 255; se guarda el SHA-256 | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ImportacionServiceTest.java` |
| UT-IMP-11 | límite de filas: 5000 filas de datos se aceptan y 5001 se rechazan | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ImportacionServiceTest.java` |
| UT-IMP-12 | los valores del archivo se convierten al ProductoRequest (rastreo, SI/NO, números, vacíos a null) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ImportacionServiceTest.java` |
| UT-IMP-13 | valores inválidos: SI/NO, entero y número se reportan en su columna con un mensaje claro | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ImportacionServiceTest.java` |
| UT-IMP-14 | los errores de Bean Validation se traducen a la columna del archivo y se ordenan por columna | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ImportacionServiceTest.java` |
| UT-IMP-15 | confirmar registra la fecha de proceso, guarda el resultado y audita importadas y omitidas | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ImportacionServiceTest.java` |
| UT-IMP-16 | al confirmar se revalida la unicidad: código o referencia ya existentes se omiten con su motivo | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ImportacionServiceTest.java` |
| UT-IMP-17 | terceros: válida, duplicado exacto, posibles duplicados (base y archivo) y errores de valor | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ImportacionServiceTest.java` |
| UT-IMP-18 | confirmar terceros convierte los valores y omite la identificación que ya existe | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ImportacionServiceTest.java` |
| UT-IMP-19 | cancelar deja la importación CANCELADA con fecha y no se puede cancelar dos veces | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ImportacionServiceTest.java` |
| UT-IMP-20 | obtener, tipo e historial; una importación inexistente responde 404 | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ImportacionServiceTest.java` |
| UT-IMP-21 | el informe de errores lista solo filas con errores o coincidencias, una fila por error | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ImportacionServiceTest.java` |
| UT-INV-01 | alertas: lotes vencidos y por vencer se calculan con fechas reales y el umbral | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/SoporteServiciosTest.java` |
| UT-INV-02 | baja por vencimiento: solo el lote afectado pasa a no apto | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/SoporteServiciosTest.java` |
| UT-INV-03 | un lote no vencido no se da de baja por vencimiento | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/SoporteServiciosTest.java` |
| UT-INV-04 | cambio de estado disponible → dañado mueve ambos saldos; reservado no es destino válido | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/SoporteServiciosTest.java` |
| UT-INV-05 | existencias: saldos, total, valor, precio vigente y alertas AGOTADO / STOCK_BAJO / VENCIDO / POR_VENCER | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/InventarioServiceTest.java` |
| UT-INV-06 | existencias: filtro recortado y umbral de días acotado entre 0 y 365 | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/InventarioServiceTest.java` |
| UT-INV-07 | alertas: lotes vencidos y por vencer con nombre del producto, agotados activos y stock bajo con saldo | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/InventarioServiceTest.java` |
| UT-INV-08 | umbral de alertas: sin valor usa el configurado (30); se acota a 0–365 | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/InventarioServiceTest.java` |
| UT-INV-09 | lotes de un producto con su estado según la fecha; producto inexistente → 404 | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/InventarioServiceTest.java` |
| UT-INV-10 | recuperar de no apto a disponible sin lote: mueve saldos, no toca lotes y audita | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/InventarioServiceTest.java` |
| UT-INV-11 | marcar como dañado sin lote en un producto por lote descuenta de los lotes; saldo exacto es válido | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/InventarioServiceTest.java` |
| UT-INV-12 | con lote: dañar mueve el saldo del lote (aunque esté vencido); recuperar un lote vencido no se permite | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/InventarioServiceTest.java` |
| UT-INV-13 | transiciones distintas de disponible↔dañado se rechazan; producto inexistente → 404 | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/InventarioServiceTest.java` |
| UT-INV-14 | baja de lote vencido: con disponible igual al del lote se permite y devuelve el lote actualizado | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/InventarioServiceTest.java` |
| UT-INV-15 | baja de lote: parte reservada → LOTE_RESERVADO; lote o producto inexistentes → 404 | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/InventarioServiceTest.java` |
| UT-INV-16 | existencias con producto por lote y saldo sobre el mínimo no genera alertas de stock | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/InventarioServiceTest.java` |
| UT-JWT-01 | genera y valida un token con sujeto y rol | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/JwtServiceTest.java` |
| UT-JWT-02 | un token expirado se rechaza | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/JwtServiceTest.java` |
| UT-JWT-03 | un token alterado (payload) se rechaza | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/JwtServiceTest.java` |
| UT-JWT-04 | un token firmado con otro secreto se rechaza | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/JwtServiceTest.java` |
| UT-JWT-05 | un secreto débil o ausente impide arrancar | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/JwtServiceTest.java` |
| UT-KAR-01 | ningún movimiento deja un saldo negativo | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/SoporteServiciosTest.java` |
| UT-KAR-02 | con el mes cerrado no se registran movimientos (409 PERIODO_CERRADO) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/SoporteServiciosTest.java` |
| UT-LOG-01 | bloquea exactamente al alcanzar el máximo de fallos | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/LoginAttemptServiceTest.java` |
| UT-LOG-02 | el bloqueo expira tras el tiempo configurado | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/LoginAttemptServiceTest.java` |
| UT-LOG-03 | un inicio de sesión correcto reinicia el contador | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/LoginAttemptServiceTest.java` |
| UT-LOG-04 | el bloqueo es por correo y no distingue mayúsculas | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/LoginAttemptServiceTest.java` |
| UT-LOT-01 | un lote existente no puede reingresar con otra fecha de vencimiento | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/SoporteServiciosTest.java` |
| UT-LOT-02 | salida sin lote indicado: primero existencia sin lote, luego lotes vencidos y FEFO | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/SoporteServiciosTest.java` |
| UT-MAS-01 | rechaza ids inválidos o repetidos | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/MotorAsistenteTest.java` |
| UT-MAS-02 | rechaza opciones o temas principales inexistentes, raíz vacía y nodos sin respuesta en español | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/MotorAsistenteTest.java` |
| UT-MAS-03 | los textos caen al español si falta el idioma y las colecciones nulas quedan vacías | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/MotorAsistenteTest.java` |
| UT-MAS-04 | normaliza: minúsculas, sin tildes ni signos, espacios simples; nulo → vacío | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/MotorAsistenteTest.java` |
| UT-MAS-05 | palabras significativas: sin palabras vacías ni repetidas, en orden | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/MotorAsistenteTest.java` |
| UT-MAS-06 | compara palabras: igual, plural/conjugación cercana o misma raíz; no confunde palabras distintas | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/MotorAsistenteTest.java` |
| UT-MAS-07 | una frase de dos palabras vence a una palabra suelta; la respuesta es siempre la misma | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/MotorAsistenteTest.java` |
| UT-MAS-08 | cada frase adicional suma 0,1 y las del otro idioma valen el 80 % | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/MotorAsistenteTest.java` |
| UT-MAS-09 | empates: se ordenan por la posición en la base; sin palabras útiles no hay resultados | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/MotorAsistenteTest.java` |
| UT-MAS-10 | temas principales filtrados por rol, hijos en orden y búsqueda por id | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/MotorAsistenteTest.java` |
| UT-MAS-11 | permisos: roles del nodo, roles propios del enlace y nodos sin enlace | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/MotorAsistenteTest.java` |
| UT-MON-01 | la moneda funcional (COP) siempre aplica tasa 1 y no requiere tasa registrada | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/MonedaServiceTest.java` |
| UT-MON-02 | la tasa vigente se consulta con la fecha del negocio (America/Bogota) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/MonedaServiceTest.java` |
| UT-MON-03 | sin tasa vigente o con la moneda inactiva no se emiten documentos (422) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/MonedaServiceTest.java` |
| UT-MON-04 | registrar tasa: se audita; no se admiten fechas futuras ni tasas para COP | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/MonedaServiceTest.java` |
| UT-MON-05 | conversión entre dos monedas extranjeras pasa por COP y redondea a los decimales de destino | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/MonedaServiceTest.java` |
| UT-MON-06 | la moneda funcional no se desactiva; una extranjera sí, con auditoría | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/MonedaServiceTest.java` |
| UT-PRE-01 | propuesta por recargo: costo $12.000 × 1,30 = $15.600 y no queda vigente | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/PrecioServiceTest.java` |
| UT-PRE-02 | sin costo promedio no se calcula (422); el precio manual exige justificación | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/PrecioServiceTest.java` |
| UT-PRE-03 | aprobar vuelve vigente el precio y registra aprobador y fechas | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/PrecioServiceTest.java` |
| UT-PRE-04 | quien propone no puede aprobar su propia propuesta | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/PrecioServiceTest.java` |
| UT-PRE-05 | una propuesta rechazada no afecta el precio vigente y no se puede aprobar después | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/PrecioServiceTest.java` |
| UT-PRE-06 | el rechazo exige motivo | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/PrecioServiceTest.java` |
| UT-PRE-07 | la propuesta automática no se duplica si el precio vigente ya es el calculado | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/PrecioServiceTest.java` |
| UT-PRE-08 | precio manual sin porcentaje: se calcula el recargo implícito y el motivo se limpia | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/PrecioServiceTest.java` |
| UT-PRE-09 | recargo implícito: sin costo o costo 0 → 0; precio bajo el costo → 0; tope de 99,9999 | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/PrecioServiceTest.java` |
| UT-PRE-10 | el porcentaje de la solicitud tiene prioridad; modo margen calcula P = C / (1 − m) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/PrecioServiceTest.java` |
| UT-PRE-11 | sin porcentaje en el producto ni en la solicitud no se calcula (SIN_PORCENTAJE) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/PrecioServiceTest.java` |
| UT-PRE-12 | producto o propuesta inexistentes responden 404 | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/PrecioServiceTest.java` |
| UT-PRE-13 | propuesta automática: sin porcentaje o sin costo no se propone nada | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/PrecioServiceTest.java` |
| UT-PRE-14 | propuesta automática: se crea cuando no hay vigente igual ni pendiente con el mismo valor | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/PrecioServiceTest.java` |
| UT-PRE-15 | propuesta automática: si ya hay una pendiente con el mismo valor no se duplica | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/PrecioServiceTest.java` |
| UT-PRE-16 | aprobar registra fecha de decisión, correos y el precio anterior en la auditoría | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/PrecioServiceTest.java` |
| UT-PRE-17 | rechazar registra quién decide, cuándo y el motivo recortado | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/PrecioServiceTest.java` |
| UT-PRE-18 | historial paginado (página ≥ 0, tamaño 1–100) con correos consultados una sola vez | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/PrecioServiceTest.java` |
| UT-PRE-19 | consulta por estado: sin estado lista las pendientes; producto inexistente se muestra sin nombre | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/PrecioServiceTest.java` |
| UT-PRO-01 | crear normaliza espacios y crea saldo inicial en cero | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ProductoServiceTest.java` |
| UT-PRO-02 | código de barras duplicado se rechaza (RN-01) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ProductoServiceTest.java` |
| UT-PRO-03 | misma combinación producto+marca+presentación se rechaza (RN-01) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ProductoServiceTest.java` |
| UT-PRO-04 | actualizar el mismo producto con su propio código es válido | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ProductoServiceTest.java` |
| UT-PRO-05 | consultar un producto inexistente lanza 404 | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ProductoServiceTest.java` |
| UT-PRO-06 | no se elimina un producto con existencias | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ProductoServiceTest.java` |
| UT-PRO-07 | conversiones repetidas o iguales a la unidad base se rechazan | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ProductoServiceTest.java` |
| UT-PRO-08 | crear copia y normaliza todos los campos y audita con la etiqueta y el resumen del producto | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ProductoServiceTest.java` |
| UT-PRO-09 | sin garantía no se guardan meses; IVA vacío queda en 0; descripción nula se conserva nula | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ProductoServiceTest.java` |
| UT-PRO-10 | actualizar valida la unicidad, reemplaza todos los campos y audita antes y después | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ProductoServiceTest.java` |
| UT-PRO-11 | activar y desactivar cambian el estado y lo auditan; repetir el mismo estado no hace nada | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ProductoServiceTest.java` |
| UT-PRO-12 | obtener por id y por código de barras (con espacios); código inexistente → 404 | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ProductoServiceTest.java` |
| UT-PRO-13 | listar usa el filtro recortado y una cadena vacía cuando no hay filtro | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ProductoServiceTest.java` |
| UT-PRO-14 | selector: existencias, precio vigente, unidades y vencidos (solo por lote); tamaño acotado a 1–50 | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ProductoServiceTest.java` |
| UT-PRO-15 | catálogo sin conexión: página desde 0, tamaño acotado a 500 y orden por id | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ProductoServiceTest.java` |
| UT-PRO-16 | selección por código: solo productos activos; inactivo o inexistente → 404 | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ProductoServiceTest.java` |
| UT-PRO-17 | eliminar: con reservado o no apto se rechaza; sin existencias borra conversiones, saldo y producto | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ProductoServiceTest.java` |
| UT-PRO-18 | eliminar un producto sin registro de inventario no intenta borrar el saldo | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ProductoServiceTest.java` |
| UT-PRO-19 | configurar unidades reemplaza las conversiones (presentación recortada) y las audita | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ProductoServiceTest.java` |
| UT-PRO-20 | existencias por estado y precio vigente (nulo si no hay uno aprobado) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ProductoServiceTest.java` |
| UT-RES-01 | crear: pasa de disponible a reservado, queda ACTIVA y la respuesta trae producto, cliente y usuario | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ReservaServiceTest.java` |
| UT-RES-02 | se puede reservar exactamente el disponible, pero no más | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ReservaServiceTest.java` |
| UT-RES-03 | producto por lote: las unidades de lotes vencidos no se pueden reservar | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ReservaServiceTest.java` |
| UT-RES-04 | la fecha límite puede ser hoy pero no una fecha pasada | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ReservaServiceTest.java` |
| UT-RES-05 | producto inexistente o inactivo, cliente inexistente o tercero que no es cliente se rechazan | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ReservaServiceTest.java` |
| UT-RES-06 | cancelar devuelve el reservado a disponible, registra fecha y motivo; solo reservas activas | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ReservaServiceTest.java` |
| UT-RES-07 | liberar vencidas: solo libera las que siguen activas al bloquearlas y devuelve cuántas | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ReservaServiceTest.java` |
| UT-RES-08 | identificadores de reservas activas vencidas (para la tarea programada) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ReservaServiceTest.java` |
| UT-RES-09 | liberar una: solo si existe, está activa, venció y tiene creador; el responsable es quien la creó | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ReservaServiceTest.java` |
| UT-RES-10 | listar: filtros por estado, producto y cliente; página desde 0 y tamaño 1–100 | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ReservaServiceTest.java` |
| UT-RES-11 | la respuesta tolera producto, cliente o usuario inexistentes | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ReservaServiceTest.java` |
| UT-TER-01 | crear normaliza campos y limpia los de persona jurídica | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/TerceroServiceTest.java` |
| UT-TER-02 | rechaza una identificación perteneciente a otro tercero | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/TerceroServiceTest.java` |
| UT-TER-03 | actualizar permite conservar la identificación propia | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/TerceroServiceTest.java` |
| UT-TER-04 | actualizar rechaza identificación de otro tercero | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/TerceroServiceTest.java` |
| UT-TER-05 | obtener devuelve el tercero encontrado | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/TerceroServiceTest.java` |
| UT-TER-06 | consultar un tercero inexistente produce 404 | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/TerceroServiceTest.java` |
| UT-TER-07 | listar usa cadena vacía cuando el filtro es nulo | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/TerceroServiceTest.java` |
| UT-TER-08 | listar elimina espacios del filtro | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/TerceroServiceTest.java` |
| UT-TER-09 | eliminar borra y sincroniza el repositorio | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/TerceroServiceTest.java` |
| UT-TER-10 | no elimina si el tercero no existe | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/TerceroServiceTest.java` |
| UT-TER-11 | persona jurídica limpia los campos de persona natural | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/TerceroServiceTest.java` |
| UT-USU-01 | contraseña débil se rechaza y nunca se audita la contraseña | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/SoporteServiciosTest.java` |
| UT-USU-02 | no se puede dejar el sistema sin administradores activos | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/SoporteServiciosTest.java` |
| UT-USU-03 | un administrador no puede desactivarse a sí mismo | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/SoporteServiciosTest.java` |
| UT-USU-04 | el cambio de rol queda auditado como CAMBIO_ROL | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/SoporteServiciosTest.java` |
| UT-VAL-01 | persona natural válida | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ValidacionDtoTest.java` |
| UT-VAL-02 | persona natural sin apellido es inválida (RN-06) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ValidacionDtoTest.java` |
| UT-VAL-03 | persona jurídica con NIT y razón social, sin apellido, es válida (HU-08) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ValidacionDtoTest.java` |
| UT-VAL-04 | persona jurídica sin razón social es inválida (RN-06) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ValidacionDtoTest.java` |
| UT-VAL-05 | un tercero sin rol de cliente ni proveedor es inválido | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ValidacionDtoTest.java` |
| UT-VAL-06 | código de barras: solo 8 a 14 dígitos | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ValidacionDtoTest.java` |
| UT-VAL-07 | se rechaza HTML/script en campos de texto (XSS almacenado) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ValidacionDtoTest.java` |
| UT-VAL-08 | límite de longitud del nombre (150) en el borde | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ValidacionDtoTest.java` |
| UT-VAL-09 | venta sin líneas es inválida | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ValidacionDtoTest.java` |
| UT-VAL-10 | cantidad cero o negativa en línea de venta es inválida | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ValidacionDtoTest.java` |
| UT-VCS-01 | detalle completo: cliente, usuario, productos, devuelto por línea, movimientos del documento y leyenda fiscal | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/VentaConsultaServiceTest.java` |
| UT-VCS-02 | detalle no exportable: sin líneas, sin factura, sin cliente y anulada; leyenda de comprobante interno | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/VentaConsultaServiceTest.java` |
| UT-VCS-03 | factura sin numeración no es exportable; producto o usuario inexistentes no rompen el detalle | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/VentaConsultaServiceTest.java` |
| UT-VCS-04 | detalle de una venta inexistente → 404 | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/VentaConsultaServiceTest.java` |
| UT-VCS-05 | datos de la empresa: los valores vacíos o en blanco se devuelven como nulos | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/VentaConsultaServiceTest.java` |
| UT-VCS-06 | listar: cliente, usuario, numeración, total, moneda y si tiene devoluciones; consultas en caché | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/VentaConsultaServiceTest.java` |
| UT-VCS-07 | listar acota el tamaño mínimo a 1 | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/VentaConsultaServiceTest.java` |
| UT-VCS-08 | filtros: número (recortado, en minúsculas y sin comodines), cliente, estado y rango de fechas | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/VentaConsultaServiceTest.java` |
| UT-VEN-01 | saldo suficiente descuenta una sola vez y emite factura | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/VentaServiceTest.java` |
| UT-VEN-02 | número interno y numeración fiscal son distintos (RN-14) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/VentaServiceTest.java` |
| UT-VEN-03 | saldo insuficiente rechaza con 409 y no modifica inventario ni factura | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/VentaServiceTest.java` |
| UT-VEN-04 | vender exactamente el saldo es válido y deja 0 (nunca negativo) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/VentaServiceTest.java` |
| UT-VEN-05 | auditoría registra valor anterior/nuevo, cantidad negativa y documento | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/VentaServiceTest.java` |
| UT-VEN-06 | sin precio vigente aprobado rechaza con 422 (RN-07) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/VentaServiceTest.java` |
| UT-VEN-07 | conversión de 3 cajas x 100 = 300 unidades (RF-02) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/VentaServiceTest.java` |
| UT-VEN-08 | conversión no configurada se rechaza sin tocar saldos (RF-02) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/VentaServiceTest.java` |
| UT-VEN-09 | un tercero que no es cliente no puede comprar | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/VentaServiceTest.java` |
| UT-VEN-10 | totales: descuento por línea y luego IVA del producto sobre la base | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/VentaServiceTest.java` |
| UT-VEN-11 | descuento mayor al máximo configurado se rechaza (422) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/VentaServiceTest.java` |
| UT-VEN-12 | el cliente no puede fijar impuestos/descuentos globales (importes calculados) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/VentaServiceTest.java` |
| UT-VEN-13 | un producto inactivo no se puede vender | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/VentaServiceTest.java` |
| UT-VEN-14 | reintento con la misma clave de operación devuelve la venta existente sin descontar de nuevo | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/VentaServiceTest.java` |
| UT-VEN-15 | producto por lote: asigna FEFO (primero el que vence antes) y traza un movimiento por lote | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/VentaServiceTest.java` |
| UT-VEN-16 | producto por lote: las unidades de lotes vencidos no se venden (409 STOCK_VENCIDO) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/VentaServiceTest.java` |
| UT-VEN-17 | garantía: la línea registra el fin de garantía según los meses del producto | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/VentaServiceTest.java` |
| UT-VEN-18 | venta con reserva: descuenta del reservado y libera el sobrante a disponible (RN-03) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/VentaServiceTest.java` |
| UT-VEN-19 | venta en USD: precio convertido con la tasa vigente, importes en USD y equivalente en COP | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/VentaServiceTest.java` |
| UT-VEN-20 | sin tasa vigente para la moneda se rechaza (422) sin tocar inventario ni factura | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/VentaServiceTest.java` |

### Unitaria / Componente (frontend)

| ID | Caso | Herramienta | Archivo |
|---|---|---|---|
| FE-AJU-01 | quien solicitó un ajuste no ve los botones para aprobarlo; otro aprobador sí | Vitest + Testing Library | `frontend/src/__tests__/modulos.test.jsx` |
| FE-ASI-01 | al abrirse pide la bienvenida una sola vez y muestra los temas como botones | Vitest + Testing Library | `frontend/src/__tests__/asistente.test.jsx` |
| FE-ASI-02 | envía la pregunta escrita, limpia el campo y muestra la respuesta con su enlace | Vitest + Testing Library | `frontend/src/__tests__/asistente.test.jsx` |
| FE-ASI-03 | elegir un tema envía su id y muestra su título como mensaje del usuario | Vitest + Testing Library | `frontend/src/__tests__/asistente.test.jsx` |
| FE-ASI-04 | el enlace navega a la pantalla y cierra el panel | Vitest + Testing Library | `frontend/src/__tests__/asistente.test.jsx` |
| FE-ASI-05 | error del servidor y falta de conexión se muestran en la conversación | Vitest + Testing Library | `frontend/src/__tests__/asistente.test.jsx` |
| FE-ASI-06 | sin conexión no pide la bienvenida, avisa y deshabilita la escritura | Vitest + Testing Library | `frontend/src/__tests__/asistente.test.jsx` |
| FE-ASI-08 | la pregunta se limita a 300 caracteres | Vitest + Testing Library | `frontend/src/__tests__/asistente.test.jsx` |
| FE-ASI-09 | el botón de la barra superior abre el asistente en cualquier pantalla | Vitest + Testing Library | `frontend/src/__tests__/asistente.test.jsx` |
| FE-CIE-01 | con pendientes bloqueantes el botón de cierre queda deshabilitado y se listan los motivos | Vitest + Testing Library | `frontend/src/__tests__/modulos.test.jsx` |
| FE-CMP-01 | muestra la leyenda de comprobante interno y permite imprimir y descargar | Vitest + Testing Library | `frontend/src/__tests__/ventas.test.jsx` |
| FE-CMP-02 | un documento no exportable bloquea impresión y PDF y explica los motivos | Vitest + Testing Library | `frontend/src/__tests__/ventas.test.jsx` |
| FE-FMT-01 | la fecha sin hora no se desplaza por zona horaria | Vitest + Testing Library | `frontend/src/__tests__/utilidades.test.js` |
| FE-FMT-02 | hoy y mes actual en formato ISO local | Vitest + Testing Library | `frontend/src/__tests__/utilidades.test.js` |
| FE-HLP-01 | la clave de operación cumple el patrón del servidor y es única | Vitest + Testing Library | `frontend/src/__tests__/utilidades.test.js` |
| FE-HLP-02 | producto: el recargo se edita en % y se envía como fracción; vacíos se envían como null | Vitest + Testing Library | `frontend/src/__tests__/utilidades.test.js` |
| FE-HLP-03 | importación: solo .xlsx, no vacío y hasta 5 MB; resumen por estado | Vitest + Testing Library | `frontend/src/__tests__/utilidades.test.js` |
| FE-HLP-04 | devolución: lo devolvible es lo vendido menos lo ya devuelto, nunca negativo | Vitest + Testing Library | `frontend/src/__tests__/utilidades.test.js` |
| FE-HLP-05 | precio: porcentaje a fracción y política de contraseñas | Vitest + Testing Library | `frontend/src/__tests__/utilidades.test.js` |
| FE-I18N-01 | traduce clave con parámetro en español e inglés | Vitest + Testing Library | `frontend/src/__tests__/mensajes.test.js` |
| FE-I18N-02 | mensaje vacío o no textual devuelve undefined | Vitest + Testing Library | `frontend/src/__tests__/mensajes.test.js` |
| FE-I18N-03 | errores de API: credenciales, bloqueo, mensaje del servidor y genérico | Vitest + Testing Library | `frontend/src/__tests__/mensajes.test.js` |
| FE-IMP-01 | los posibles duplicados solo se importan si se marcan y el total a importar se actualiza | Vitest + Testing Library | `frontend/src/__tests__/modulos.test.jsx` |
| FE-IMP-02 | un archivo que no es .xlsx se rechaza antes de subirlo | Vitest + Testing Library | `frontend/src/__tests__/modulos.test.jsx` |
| FE-LIN-01 | cantidad: obligatoria, positiva y con máximo 4 decimales (acepta coma) | Vitest + Testing Library | `frontend/src/__tests__/utilidades.test.js` |
| FE-LIN-02 | porcentaje 0–100 con 2 decimales e importe no negativo | Vitest + Testing Library | `frontend/src/__tests__/utilidades.test.js` |
| FE-LIN-03 | una venta sin líneas o con producto sin precio vigente es inválida | Vitest + Testing Library | `frontend/src/__tests__/utilidades.test.js` |
| FE-LOG-01 | los campos tienen etiqueta accesible y el formulario un nombre | Vitest + Testing Library | `frontend/src/__tests__/LoginPage.test.jsx` |
| FE-LOG-02 | enviar vacío muestra errores y no llama a la API | Vitest + Testing Library | `frontend/src/__tests__/LoginPage.test.jsx` |
| FE-LOG-03 | correo inválido muestra mensaje de formato | Vitest + Testing Library | `frontend/src/__tests__/LoginPage.test.jsx` |
| FE-LOG-04 | login correcto guarda la sesión persistente y navega al inicio | Vitest + Testing Library | `frontend/src/__tests__/LoginPage.test.jsx` |
| FE-LOG-05 | credenciales inválidas muestran alerta accesible y no guardan sesión | Vitest + Testing Library | `frontend/src/__tests__/LoginPage.test.jsx` |
| FE-LOG-06 | el botón mostrar/ocultar contraseña alterna el tipo del campo | Vitest + Testing Library | `frontend/src/__tests__/LoginPage.test.jsx` |
| FE-MON-01 | precio COP → moneda con 4 decimales, equivalente COP con 2 y formato por código ISO | Vitest + Testing Library | `frontend/src/__tests__/monedas.test.jsx` |
| FE-MON-02 | validación de tasa y código ISO | Vitest + Testing Library | `frontend/src/__tests__/monedas.test.jsx` |
| FE-MON-03 | al elegir USD los precios y el total se muestran en USD, con equivalente en COP, y se envía la moneda | Vitest + Testing Library | `frontend/src/__tests__/monedas.test.jsx` |
| FE-MON-04 | el rol Ventas consulta tasas pero no puede registrarlas ni desactivar monedas | Vitest + Testing Library | `frontend/src/__tests__/monedas.test.jsx` |
| FE-MON-05 | Gerencia registra una tasa: fecha futura bloqueada; valor válido se envía como número | Vitest + Testing Library | `frontend/src/__tests__/monedas.test.jsx` |
| FE-MON-06 | el conversor muestra el resultado con las tasas usadas | Vitest + Testing Library | `frontend/src/__tests__/monedas.test.jsx` |
| FE-MON-07 | muestra importes en USD, la tasa aplicada y el equivalente en COP | Vitest + Testing Library | `frontend/src/__tests__/monedas.test.jsx` |
| FE-OFF-01 | sincroniza todas las páginas, guarda la fecha y se borra al cerrar sesión | Vitest + Testing Library | `frontend/src/__tests__/offline_pdf.test.js` |
| FE-OFF-02 | busca por nombre, marca, presentación o código sin distinguir tildes ni mayúsculas | Vitest + Testing Library | `frontend/src/__tests__/offline_pdf.test.js` |
| FE-PDF-01 | sin integración fiscal el PDF lleva la leyenda de comprobante interno | Vitest + Testing Library | `frontend/src/__tests__/offline_pdf.test.js` |
| FE-PDF-02 | con numeración fiscal válida usa la leyenda de factura electrónica | Vitest + Testing Library | `frontend/src/__tests__/offline_pdf.test.js` |
| FE-PDF-03 | nombre de archivo sin caracteres peligrosos | Vitest + Testing Library | `frontend/src/__tests__/offline_pdf.test.js` |
| FE-PER-01 | matriz de acciones | Vitest + Testing Library | `frontend/src/__tests__/utilidades.test.js` |
| FE-PER-02 | el menú oculta módulos sin permiso y no deja grupos vacíos | Vitest + Testing Library | `frontend/src/__tests__/utilidades.test.js` |
| FE-PRE-01 | una propuesta manual sin justificación no se envía | Vitest + Testing Library | `frontend/src/__tests__/modulos.test.jsx` |
| FE-PRE-02 | el rol Inventario no ve acciones de aprobación (solo Gerencia aprueba) | Vitest + Testing Library | `frontend/src/__tests__/modulos.test.jsx` |
| FE-REC-01 | exige factura, lote y vencimiento para productos por lote y no permite superar lo pendiente | Vitest + Testing Library | `frontend/src/__tests__/modulos.test.jsx` |
| FE-REC-02 | recepción parcial: envía solo las líneas con cantidad, con lote y vencimiento | Vitest + Testing Library | `frontend/src/__tests__/modulos.test.jsx` |
| FE-RUT-01 | un rol sin permiso ve el aviso de acceso denegado en lugar de la página | Vitest + Testing Library | `frontend/src/__tests__/modulos.test.jsx` |
| FE-RUT-02 | un rol con permiso ve la página | Vitest + Testing Library | `frontend/src/__tests__/modulos.test.jsx` |
| FE-SEL-01 | un código escaneado + Enter selecciona el producto sin abrir la lista | Vitest + Testing Library | `frontend/src/__tests__/ventas.test.jsx` |
| FE-SEL-02 | código inexistente avisa y no selecciona | Vitest + Testing Library | `frontend/src/__tests__/ventas.test.jsx` |
| FE-SEL-03 | la búsqueda muestra existencias y precio de cada opción | Vitest + Testing Library | `frontend/src/__tests__/ventas.test.jsx` |
| FE-SES-01 | con | Vitest + Testing Library | `frontend/src/__tests__/session.test.js` |
| FE-SES-02 | sin | Vitest + Testing Library | `frontend/src/__tests__/session.test.js` |
| FE-SES-03 | una sesión expirada se descarta (borde exacto incluido) | Vitest + Testing Library | `frontend/src/__tests__/session.test.js` |
| FE-SES-04 | datos corruptos se descartan sin lanzar error | Vitest + Testing Library | `frontend/src/__tests__/session.test.js` |
| FE-SES-05 | borrarSesion limpia ambos almacenamientos | Vitest + Testing Library | `frontend/src/__tests__/session.test.js` |
| FE-TOT-01 | redondeo HALF_UP sin errores de coma flotante | Vitest + Testing Library | `frontend/src/__tests__/utilidades.test.js` |
| FE-TOT-02 | línea: descuento sobre subtotal e IVA sobre la base descontada | Vitest + Testing Library | `frontend/src/__tests__/utilidades.test.js` |
| FE-TOT-03 | suma de líneas y factor de presentación | Vitest + Testing Library | `frontend/src/__tests__/utilidades.test.js` |
| FE-VAL-01 | acepta un producto válido | Vitest + Testing Library | `frontend/src/__tests__/schemas.test.js` |
| FE-VAL-02 | rechaza código de barras con letras o longitud fuera de 8-14 | Vitest + Testing Library | `frontend/src/__tests__/schemas.test.js` |
| FE-VAL-03 | rechaza < y > (XSS) y respeta la longitud máxima en el borde | Vitest + Testing Library | `frontend/src/__tests__/schemas.test.js` |
| FE-VAL-04 | obligatorios: vacío o solo espacios | Vitest + Testing Library | `frontend/src/__tests__/schemas.test.js` |
| FE-VAL-05 | persona natural válida | Vitest + Testing Library | `frontend/src/__tests__/schemas.test.js` |
| FE-VAL-06 | natural exige nombre y apellido | Vitest + Testing Library | `frontend/src/__tests__/schemas.test.js` |
| FE-VAL-07 | jurídica con razón social y sin apellido es válida; sin razón social no | Vitest + Testing Library | `frontend/src/__tests__/schemas.test.js` |
| FE-VAL-08 | exige al menos un rol | Vitest + Testing Library | `frontend/src/__tests__/schemas.test.js` |
| FE-VAL-09 | correo, teléfono e identificación con formato | Vitest + Testing Library | `frontend/src/__tests__/schemas.test.js` |
| FE-VAL-10 | correo válido y contraseña obligatoria | Vitest + Testing Library | `frontend/src/__tests__/schemas.test.js` |
| FE-VAL-11 | venta válida | Vitest + Testing Library | `frontend/src/__tests__/schemas.test.js` |
| FE-VAL-12 | cantidad cero, negativa o con más de 4 decimales es inválida | Vitest + Testing Library | `frontend/src/__tests__/schemas.test.js` |
| FE-VAL-13 | exige al menos una línea | Vitest + Testing Library | `frontend/src/__tests__/schemas.test.js` |
| FE-VEN-01 | sin cliente ni líneas muestra errores y no llama a la API | Vitest + Testing Library | `frontend/src/__tests__/ventas.test.jsx` |
| FE-VEN-02 | vista previa con descuento e IVA y envío con clave de idempotencia | Vitest + Testing Library | `frontend/src/__tests__/ventas.test.jsx` |
| FE-VEN-03 | un error del servidor se muestra y el reintento conserva la misma clave de operación | Vitest + Testing Library | `frontend/src/__tests__/ventas.test.jsx` |
| FE-VEN-04 | sin conexión el registro queda deshabilitado (RN-10) | Vitest + Testing Library | `frontend/src/__tests__/ventas.test.jsx` |

### Unitaria (scripts)

| ID | Caso | Herramienta | Archivo |
|---|---|---|---|
| PY-CI | ci | unittest | `tests/scripts/test_taiga_metricas.py` |
| PY-CONSTRUIR_REPORTE_INCLUY | construir reporte incluye contexto y tres tareas | unittest | `tests/scripts/test_taiga_metricas.py` |
| PY-DEFINICION_DE_METRICAS_E | definicion de metricas es consistente | unittest | `tests/scripts/test_taiga_metricas.py` |
| PY-HUELLA_ESTABLE_Y_DISTINT | huella estable y distinta por etapa | unittest | `tests/scripts/test_taiga_metricas.py` |
| PY-ISSUES | issues | unittest | `tests/scripts/test_taiga_metricas.py` |
| PY-MARKDOWN_INCLUYE_ESTADO | markdown incluye estado | unittest | `tests/scripts/test_taiga_metricas.py` |
| PY-MAYOR_MEJOR_EN_LOS_BORDE | mayor mejor en los bordes | unittest | `tests/scripts/test_taiga_metricas.py` |
| PY-MENOR_MEJOR_EN_LOS_BORDE | menor mejor en los bordes | unittest | `tests/scripts/test_taiga_metricas.py` |
| PY-REPORTAR_FALLO_CREA_ISSU | reportar fallo crea issue tareas y notifica sin duplicar | unittest | `tests/scripts/test_taiga_metricas.py` |
| PY-SIN_DATOS | sin datos | unittest | `tests/scripts/test_taiga_metricas.py` |
| PY-TAREAS_IGNORAN_FUERA_DE_ | tareas ignoran fuera de ventana | unittest | `tests/scripts/test_taiga_metricas.py` |

### Integración / Sistema

| ID | Caso | Herramienta | Archivo |
|---|---|---|---|
| IT-ADM-01 | cierre del mes: bloquea movimientos; la reapertura los permite; solo Gerencia cierra | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/ModulosAdministrativosIT.java` |
| IT-ADM-02 | usuarios: solo Administrador gestiona; política de contraseña; cambio de rol auditado | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/ModulosAdministrativosIT.java` |
| IT-ADM-03 | importación Excel: validar → revisar → confirmar; reprocesar el mismo archivo se rechaza | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/ModulosAdministrativosIT.java` |
| IT-ADM-04 | exportación Excel: neutraliza fórmulas y respeta permisos | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/ModulosAdministrativosIT.java` |
| IT-ADM-05 | conteo físico: no ajusta hasta aprobar; la aprobación genera el ajuste | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/ModulosAdministrativosIT.java` |
| IT-ADM-06 | catálogo para consulta sin conexión: paginado y solo productos activos | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/ModulosAdministrativosIT.java` |
| IT-ADM-07 | migraciones V3/V4: auditoría inmutable y nuevos tipos de movimiento disponibles | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/ModulosAdministrativosIT.java` |
| IT-ADM-08 | monedas: solo Gerencia/Administración registran tasas; venta en USD guarda importes en USD y equivalente en COP | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/ModulosAdministrativosIT.java` |
| IT-ASI-01 | sin sesión el asistente responde 401 | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/AsistenteApiIT.java` |
| IT-ASI-02 | bienvenida con los temas del rol (Ventas no ve Precios; Gerencia sí) | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/AsistenteApiIT.java` |
| IT-ASI-03 | pregunta frecuente en español con enlace, y en inglés con Accept-Language: en | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/AsistenteApiIT.java` |
| IT-ASI-04 | rol sin permiso: aviso y sin enlace (Gerencia no registra ventas, RF-09) | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/AsistenteApiIT.java` |
| IT-ASI-05 | consulta real por código de barras: precio vigente y existencias | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/AsistenteApiIT.java` |
| IT-ASI-06 | validaciones: mensaje vacío 422, tema inválido 400, < > rechazados 400, tema inexistente 404 | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/AsistenteApiIT.java` |
| IT-AUT-01 | credenciales correctas devuelven JWT y rol, sin hash | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/AutenticacionIT.java` |
| IT-AUT-02 | contraseña errónea → 401 con mensaje genérico | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/AutenticacionIT.java` |
| IT-AUT-03 | usuario inexistente responde igual que contraseña errónea (no enumera cuentas) | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/AutenticacionIT.java` |
| IT-AUT-04 | tres fallos seguidos bloquean la cuenta → 429 incluso con la clave correcta | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/AutenticacionIT.java` |
| IT-AUT-05 | cuenta inactiva no inicia sesión → 403 | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/AutenticacionIT.java` |
| IT-AUT-06 | renovar token con sesión vigente (permanencia) | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/AutenticacionIT.java` |
| IT-AUT-07 | validación del cuerpo de login (correo inválido) → 400 | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/AutenticacionIT.java` |
| IT-AUT-08 | mensaje de credenciales inválidas en inglés (i18n) | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/AutenticacionIT.java` |
| IT-FLU-01 | compra → recepción con lote y vencimiento → existencias, lote, costo y auditoría | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/FlujoOperativoIT.java` |
| IT-FLU-02 | precio aprobado → venta con IVA y descuento → detalle persistido → devolución parcial | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/FlujoOperativoIT.java` |
| IT-FLU-03 | ajuste: pendiente sin efecto, aprobado por otro usuario, trazado en movimientos y eventos | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/FlujoOperativoIT.java` |
| IT-PRO-01 | crear y recuperar por código de barras (HU-01) | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/ProductoApiIT.java` |
| IT-PRO-02 | el producto nuevo nace con saldo en cero | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/ProductoApiIT.java` |
| IT-PRO-03 | código de barras duplicado → 409 (RN-01) | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/ProductoApiIT.java` |
| IT-PRO-04 | validación 400 con mensajes en español por defecto | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/ProductoApiIT.java` |
| IT-PRO-05 | los mismos errores en inglés con Accept-Language: en (i18n) | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/ProductoApiIT.java` |
| IT-PRO-06 | el rol Ventas no puede crear productos (403, RF-09) | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/ProductoApiIT.java` |
| IT-PRO-07 | actualizar un producto | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/ProductoApiIT.java` |
| IT-PRO-08 | eliminar: solo Administrador; producto sin movimientos → 204 | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/ProductoApiIT.java` |
| IT-PRO-09 | producto con existencias no se elimina → 409 | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/ProductoApiIT.java` |
| IT-PRO-10 | listado paginado y filtro por texto | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/ProductoApiIT.java` |
| IT-VEN-01 | venta con saldo suficiente descuenta una vez y emite factura (HU-05) | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/VentaApiIT.java` |
| IT-VEN-02 | saldo insuficiente → 409, no cambia inventario ni crea factura | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/VentaApiIT.java` |
| IT-VEN-03 | atomicidad: si una línea falla, ninguna línea descuenta (todo o nada) | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/VentaApiIT.java` |
| IT-VEN-04 | la venta genera movimiento auditado con los 8 campos (RF-08) | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/VentaApiIT.java` |
| IT-VEN-05 | sin precio vigente aprobado → 422 (RN-07) | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/VentaApiIT.java` |
| IT-VEN-06 | venta por presentación: 3 cajas de 100 descuentan 300 unidades (RF-02) | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/VentaApiIT.java` |
| IT-VEN-07 | solo Ventas/Administrador registran ventas; Gerencia consulta (RF-09) | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/VentaApiIT.java` |
| IT-VEN-08 | CONCURRENCIA RN-11: dos ventas de 8 sobre 10 → exactamente una gana, saldo 2 | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/VentaApiIT.java` |
| IT-VEN-09 | CARGA: 20 ventas simultáneas de 1 sobre stock 5 → 5 éxitos, 15 rechazos, saldo 0 (nunca negativo) | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/VentaApiIT.java` |

### Sistema (BD real)

| ID | Caso | Herramienta | Archivo |
|---|---|---|---|
| ST-BD-01 | RN-16: un movimiento de inventario no se puede editar | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/ReglasBaseDatosIT.java` |
| ST-BD-02 | RN-09: una venta no se puede eliminar | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/ReglasBaseDatosIT.java` |
| ST-BD-03 | RN-03: el saldo disponible no puede quedar negativo (CHECK) | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/ReglasBaseDatosIT.java` |
| ST-BD-04 | RN-01: el código de barras es único (UNIQUE) | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/ReglasBaseDatosIT.java` |
| ST-BD-05 | RN-14: el número interno de venta y la numeración fiscal no coinciden | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/ReglasBaseDatosIT.java` |

### Seguridad

| ID | Caso | Herramienta | Archivo |
|---|---|---|---|
| SEG-01 | sin token → 401 en JSON (A07) | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/SeguridadIT.java` |
| SEG-02 | token con payload alterado → 401 | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/SeguridadIT.java` |
| SEG-03 | token basura o con esquema distinto → 401 | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/SeguridadIT.java` |
| SEG-04 | un token válido de un usuario luego desactivado deja de funcionar | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/SeguridadIT.java` |
| SEG-05 | escalamiento horizontal: Ventas no accede a auditoría ni elimina (A01) | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/SeguridadIT.java` |
| SEG-06 | inyección SQL en el filtro de búsqueda no altera ni rompe los datos (A03) | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/SeguridadIT.java` |
| SEG-07 | XSS almacenado: <script> en campos de texto se rechaza (400) | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/SeguridadIT.java` |
| SEG-08 | mass assignment: campos desconocidos en el JSON se rechazan (400) | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/SeguridadIT.java` |
| SEG-09 | JSON mal formado → 400 sin filtrar la traza interna | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/SeguridadIT.java` |
| SEG-10 | cabeceras de seguridad presentes (A05) | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/SeguridadIT.java` |
| SEG-11 | CORS: un origen no permitido es rechazado | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/SeguridadIT.java` |
| SEG-12 | actuator sensible protegido; documentación OpenAPI y health públicos | JUnit 5 + Spring Test (MockMvc) + Testcontainers | `backend/src/test/java/com/ferreteria/inventario/integration/SeguridadIT.java` |

### Aceptación E2E

| ID | Caso | Herramienta | Archivo |
|---|---|---|---|
| E2E-A11Y-01 | enlace | Playwright | `frontend/e2e/criterios-aceptacion.spec.js` |
| E2E-ASI-01 | Ventas pregunta cómo registrar una venta y el enlace la lleva a la pantalla | Playwright | `frontend/e2e/asistente.spec.js` |
| E2E-ASI-02 | consulta el precio y las existencias de un producto por su código | Playwright | `frontend/e2e/asistente.spec.js` |
| E2E-ASI-03 | un tema elegido muestra el aviso de permisos para Gerencia y no ofrece el enlace | Playwright | `frontend/e2e/asistente.spec.js` |
| E2E-ASI-04 | en móvil el panel ocupa la pantalla sin desplazamiento horizontal y se cierra | Playwright | `frontend/e2e/asistente.spec.js` |
| E2E-AUT-01 | login correcto lleva a productos y logout vuelve al login | Playwright | `frontend/e2e/criterios-aceptacion.spec.js` |
| E2E-AUT-02 | credenciales inválidas muestran alerta | Playwright | `frontend/e2e/criterios-aceptacion.spec.js` |
| E2E-AUT-03 | ruta protegida sin sesión redirige al login | Playwright | `frontend/e2e/criterios-aceptacion.spec.js` |
| E2E-AUT-04 | permanencia: con | Playwright | `frontend/e2e/criterios-aceptacion.spec.js` |
| E2E-AUT-05 | sin | Playwright | `frontend/e2e/criterios-aceptacion.spec.js` |
| E2E-FLU-01 | Compras registra una orden con proveedor y producto por código | Playwright | `frontend/e2e/flujos-operativos.spec.js` |
| E2E-FLU-02 | Inventario recibe parcialmente y luego completa la orden | Playwright | `frontend/e2e/flujos-operativos.spec.js` |
| E2E-FLU-03 | Inventario propone precio manual y Gerencia lo aprueba (sin autoaprobación) | Playwright | `frontend/e2e/flujos-operativos.spec.js` |
| E2E-FLU-04 | Ventas vende con lector de código, ve el detalle, el comprobante interno y descarga el PDF | Playwright | `frontend/e2e/flujos-operativos.spec.js` |
| E2E-FLU-05 | no se puede devolver más de lo vendido | Playwright | `frontend/e2e/flujos-operativos.spec.js` |
| E2E-I18N-01 | cambiar a inglés traduce la interfaz y persiste tras recargar | Playwright | `frontend/e2e/criterios-aceptacion.spec.js` |
| E2E-MON-01 | Gerencia registra la tasa USD y Ventas factura en USD con equivalente en COP | Playwright | `frontend/e2e/flujos-operativos.spec.js` |
| E2E-OFF-01 | sin conexión se consulta la copia local del catálogo y no se permiten operaciones | Playwright | `frontend/e2e/flujos-operativos.spec.js` |
| E2E-PRO-01 | crear producto y encontrarlo por código; código duplicado se rechaza (RN-01) | Playwright | `frontend/e2e/criterios-aceptacion.spec.js` |
| E2E-PRO-02 | validación de formulario: código inválido bloquea el envío y muestra el error | Playwright | `frontend/e2e/criterios-aceptacion.spec.js` |
| E2E-RES-01 | en móvil no hay desplazamiento horizontal de la página | Playwright | `frontend/e2e/criterios-aceptacion.spec.js` |
| E2E-ROL-01 | Ventas no ve el botón de crear producto; Gerencia no puede registrar ventas | Playwright | `frontend/e2e/criterios-aceptacion.spec.js` |
| E2E-ROL-02 | Ventas no ve administración y una URL directa muestra acceso denegado | Playwright | `frontend/e2e/flujos-operativos.spec.js` |
| E2E-ROL-03 | Gerencia consulta auditoría y cierres pero no registra ventas | Playwright | `frontend/e2e/flujos-operativos.spec.js` |
| E2E-VEN-01 | saldo insuficiente muestra el error del servidor y no registra la venta | Playwright | `frontend/e2e/criterios-aceptacion.spec.js` |
| SEL-AUT-01 | login correcto muestra el catálogo de productos | Selenium WebDriver | `frontend/e2e-selenium/aceptacion.test.mjs` |
| SEL-AUT-02 | credenciales inválidas muestran alerta con role=alert | Selenium WebDriver | `frontend/e2e-selenium/aceptacion.test.mjs` |
| SEL-PRO-01 | un código de barras inválido bloquea el guardado y muestra el error | Selenium WebDriver | `frontend/e2e-selenium/aceptacion.test.mjs` |
| SEL-ROL-01 | el rol Ventas no ve el botón "Nuevo producto" | Selenium WebDriver | `frontend/e2e-selenium/aceptacion.test.mjs` |
| SEL-SEG-01 | sin sesión, /ventas redirige a /login | Selenium WebDriver | `frontend/e2e-selenium/aceptacion.test.mjs` |

<!-- CASOS:FIN -->

### Pruebas de base de datos (SQL)
Los scripts `database/03_pruebas.sql` (reglas RN-01…RN-16 como pruebas PL/pgSQL, resultados en `test_resultados`) y `database/04_verificaciones.sql` (integridad: debe devolver 0 filas) provienen del análisis y se ejecutan con `database/init-db.sh`.

## Pruebas de estrés y límite (k6) — casos y criterio de aceptación

| ID | Escenario | Criterio de aceptación | Script |
|---|---|---|---|
| NF-CAR-01 | Carga normal: rampa a 50 usuarios simultáneos 5 min (RNF-01) | p95 < 500 ms, p99 < 1 s, errores < 1 % | `carga.js` |
| NF-EST-01 | Estrés: rampa 100→300 usuarios | Degradación gradual; sin 5xx masivos; recuperación al bajar la carga | `carga.js` |
| NF-LIM-01 | Tamaño de página 101 / página negativa / página gigante | 400 / 400 / 200 vacío | `limite.js` |
| NF-LIM-02 | Filtro de 5000 caracteres | Rechazado (400/414), sin 5xx | `limite.js` |
| NF-LIM-03 | Cuerpo JSON de 2 MB | Sin 5xx | `limite.js` |
| NF-LIM-04 | 201 líneas de venta / cantidad 99 999 999 999 | 400 | `limite.js` |
| NF-LIM-05 | 5 logins fallidos seguidos | El 6.º recibe 429 | `limite.js` |
| NF-PIC-01 | Spike 10→400 req/s en 10 s | Errores < 5 %, la API se recupera | `limite.js` |
| NF-CON-01 | 150 intentos de venta de 1 unidad con stock 50 | Exactamente 50 × 201, 100 × 409, saldo final 0, nunca negativo (RN-11) | `venta-concurrente.js` |

Equivalente automático en CI (sin k6): `IT-VEN-08` y `IT-VEN-09`.

## Pendiente por implementar (no incluido en esta entrega)
El alcance entregado cubre autenticación/roles, productos (+unidades), terceros, ventas con factura simulada, inventario y auditoría. Los siguientes módulos del análisis **no tienen API/pantallas ni pruebas todavía**; sus casos deben derivarse de los criterios de aceptación de sus historias de usuario: compras y recepción, aprobación de precios, ajustes y conteos físicos, devoluciones/anulaciones, cierres, reportes y la integración real con el proveedor de facturación DIAN (hoy `FacturacionSimuladaAdapter`).
