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
Total de casos automatizados: **275**.

### Unitaria

| ID | Caso | Herramienta | Archivo |
|---|---|---|---|
| UT-AJU-01 | solicitar deja el ajuste pendiente y no modifica el saldo | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AjusteConteoServiceTest.java` |
| UT-AJU-02 | aprobar aplica el ajuste y registra el movimiento vinculado | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AjusteConteoServiceTest.java` |
| UT-AJU-03 | quien solicita no puede aprobar su propio ajuste | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AjusteConteoServiceTest.java` |
| UT-AJU-04 | una salida mayor al disponible se rechaza (nunca saldo negativo) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AjusteConteoServiceTest.java` |
| UT-AJU-05 | un ajuste rechazado no altera saldos y no puede volver a decidirse | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/AjusteConteoServiceTest.java` |
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
| UT-IMP-01 | la validación clasifica cada fila sin importar nada | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ImportacionServiceTest.java` |
| UT-IMP-02 | faltan columnas obligatorias → 422 con la lista | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ImportacionServiceTest.java` |
| UT-IMP-03 | un archivo que no es .xlsx válido se rechaza | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ImportacionServiceTest.java` |
| UT-IMP-04 | confirmar importa solo válidas y los posibles duplicados aprobados | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ImportacionServiceTest.java` |
| UT-IMP-05 | sin aprobar el posible duplicado, se omite | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ImportacionServiceTest.java` |
| UT-IMP-06 | un archivo idéntico ya importado no se vuelve a procesar | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ImportacionServiceTest.java` |
| UT-IMP-07 | una importación procesada no se confirma de nuevo | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ImportacionServiceTest.java` |
| UT-IMP-08 | exportación: textos que empiezan por = + - @ se neutralizan (inyección de fórmulas) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ImportacionServiceTest.java` |
| UT-INV-01 | alertas: lotes vencidos y por vencer se calculan con fechas reales y el umbral | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/SoporteServiciosTest.java` |
| UT-INV-02 | baja por vencimiento: solo el lote afectado pasa a no apto | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/SoporteServiciosTest.java` |
| UT-INV-03 | un lote no vencido no se da de baja por vencimiento | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/SoporteServiciosTest.java` |
| UT-INV-04 | cambio de estado disponible → dañado mueve ambos saldos; reservado no es destino válido | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/SoporteServiciosTest.java` |
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
| UT-PRE-01 | propuesta por recargo: costo $12.000 × 1,30 = $15.600 y no queda vigente | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/PrecioServiceTest.java` |
| UT-PRE-02 | sin costo promedio no se calcula (422); el precio manual exige justificación | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/PrecioServiceTest.java` |
| UT-PRE-03 | aprobar vuelve vigente el precio y registra aprobador y fechas | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/PrecioServiceTest.java` |
| UT-PRE-04 | quien propone no puede aprobar su propia propuesta | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/PrecioServiceTest.java` |
| UT-PRE-05 | una propuesta rechazada no afecta el precio vigente y no se puede aprobar después | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/PrecioServiceTest.java` |
| UT-PRE-06 | el rechazo exige motivo | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/PrecioServiceTest.java` |
| UT-PRE-07 | la propuesta automática no se duplica si el precio vigente ya es el calculado | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/PrecioServiceTest.java` |
| UT-PRO-01 | crear normaliza espacios y crea saldo inicial en cero | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ProductoServiceTest.java` |
| UT-PRO-02 | código de barras duplicado se rechaza (RN-01) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ProductoServiceTest.java` |
| UT-PRO-03 | misma combinación producto+marca+presentación se rechaza (RN-01) | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ProductoServiceTest.java` |
| UT-PRO-04 | actualizar el mismo producto con su propio código es válido | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ProductoServiceTest.java` |
| UT-PRO-05 | consultar un producto inexistente lanza 404 | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ProductoServiceTest.java` |
| UT-PRO-06 | no se elimina un producto con existencias | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ProductoServiceTest.java` |
| UT-PRO-07 | conversiones repetidas o iguales a la unidad base se rechazan | JUnit 5 + Mockito | `backend/src/test/java/com/ferreteria/inventario/unit/ProductoServiceTest.java` |
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

### Unitaria / Componente (frontend)

| ID | Caso | Herramienta | Archivo |
|---|---|---|---|
| FE-AJU-01 | quien solicitó un ajuste no ve los botones para aprobarlo; otro aprobador sí | Vitest + Testing Library | `frontend/src/__tests__/modulos.test.jsx` |
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
