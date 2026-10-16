-- ====================================================================
-- Nombre: 04_verificaciones.sql
-- Descripción: Consultas de solo lectura de integridad de datos: deben devolver cero filas.
-- Fecha de creación: 2026-10-07
-- Fecha de actualización: 2026-10-16
-- Autor: Equipo Ferretería
-- Versión: 1.0.0
-- ====================================================================
-- =====================================================================
-- 04_verificaciones.sql — Verificación de integridad de datos
-- =====================================================================

-- 4.1 Productos sin código de barras o con código vacío (RN-01)
SELECT id_producto, nombre, marca, presentacion
FROM producto
WHERE codigo_barras IS NULL OR TRIM(codigo_barras) = '';

-- 4.2 Inventario con algún saldo negativo (RN-03) — no debería ocurrir por los CHECK,
--     pero se deja como verificación de defensa en profundidad
SELECT * FROM inventario
WHERE disponible < 0 OR reservado < 0 OR danado < 0;

-- 4.3 Reservas activas cuya cantidad supera el disponible + reservado del producto
SELECT r.id_reserva, r.id_producto, r.cantidad, i.disponible, i.reservado
FROM reserva r
JOIN inventario i ON i.id_producto = r.id_producto
WHERE r.estado = 'activa' AND r.cantidad > (i.disponible + i.reservado);

-- 4.4 Líneas de compra cuya cantidad recibida supera la cantidad pedida (RF-03/RF-17)
SELECT * FROM linea_compra WHERE cantidad_recibida > cantidad_pedida;

-- 4.5 Ventas activas sin factura asociada (RF-04)
SELECT v.id_venta, v.numero_interno
FROM venta v
LEFT JOIN factura f ON f.id_venta = v.id_venta
WHERE v.estado = 'activo' AND f.id_factura IS NULL;

-- 4.6 Facturas cuyo número interno de venta coincide con la numeración DIAN (RN-14)
SELECT v.numero_interno, f.numeracion_dian
FROM venta v
JOIN factura f ON f.id_venta = v.id_venta
WHERE v.numero_interno = f.numeracion_dian;

-- 4.7 Precios vigentes sin estado "aprobado" (defensa en profundidad de RN-07)
SELECT * FROM precio WHERE precio_vigente IS NOT NULL AND estado_aprobacion <> 'aprobado';

-- 4.8 Terceros duplicados por número de identificación (no debería ser posible por el UNIQUE,
--     pero se verifica por si se cargaron datos históricos sin esa restricción)
SELECT numero_identificacion, COUNT(*)
FROM tercero
GROUP BY numero_identificacion
HAVING COUNT(*) > 1;

-- 4.9 Terceros marcados como natural con razón social diligenciada (dato inconsistente)
SELECT * FROM tercero WHERE tipo_persona = 'natural' AND razon_social IS NOT NULL;

-- 4.10 Movimientos de tipo "ajuste" sin motivo registrado (RN-08)
SELECT * FROM movimiento_inventario WHERE tipo = 'ajuste' AND (motivo IS NULL OR TRIM(motivo) = '');

-- 4.11 Conteos físicos aprobados sin un movimiento de ajuste vinculado (RF-15)
SELECT * FROM conteo_fisico WHERE estado = 'aprobado' AND diferencia <> 0 AND id_movimiento_ajuste IS NULL;

-- 4.12 Consistencia global de inventario: saldo actual vs. suma de movimientos
--      (el saldo "disponible" debe coincidir con la suma algebraica de los
--       movimientos que afectan disponible para ese producto)
SELECT i.id_producto,
       i.disponible AS saldo_actual,
       COALESCE(SUM(m.cantidad), 0) AS suma_movimientos,
       i.disponible - COALESCE(SUM(m.cantidad), 0) AS diferencia
FROM inventario i
LEFT JOIN movimiento_inventario m ON m.id_producto = i.id_producto AND m.estado_inventario = 'disponible'
GROUP BY i.id_producto, i.disponible
HAVING i.disponible <> COALESCE(SUM(m.cantidad), 0);

-- 4.x (V4) Saldos reservado y dañado conciliados con sus movimientos
SELECT i.id_producto, i.reservado, i.danado
FROM inventario i
WHERE i.reservado <> (SELECT COALESCE(SUM(cantidad), 0) FROM movimiento_inventario m
                      WHERE m.id_producto = i.id_producto AND m.estado_inventario = 'reservado')
   OR i.danado <> (SELECT COALESCE(SUM(cantidad), 0) FROM movimiento_inventario m
                   WHERE m.id_producto = i.id_producto AND m.estado_inventario = 'danado');

-- 4.x (V4) La suma de los lotes no supera el saldo físico del producto (RN-05)
SELECT l.id_producto, SUM(l.disponible) AS lotes_disponible, SUM(l.danado) AS lotes_danado,
       MAX(i.disponible + i.reservado) AS inventario_vendible, MAX(i.danado) AS inventario_danado
FROM lote l JOIN inventario i ON i.id_producto = l.id_producto
GROUP BY l.id_producto
HAVING SUM(l.disponible) > MAX(i.disponible + i.reservado) OR SUM(l.danado) > MAX(i.danado);

-- 4.x (V4) Ventas nuevas cuyo total no cuadra con sus líneas (las históricas sin desglose quedan excluidas)
SELECT v.id_venta, v.total, SUM(l.total) AS suma_lineas
FROM venta v JOIN linea_venta l ON l.id_venta = v.id_venta
WHERE v.id_usuario IS NOT NULL
GROUP BY v.id_venta, v.total
HAVING v.total <> SUM(l.total);

-- 4.x (V4) Devoluciones de cliente que superan lo vendido en la línea
SELECT lv.id_linea_venta, lv.cantidad, SUM(d.cantidad) AS devuelto
FROM devolucion d
JOIN documento_devolucion dd ON dd.id_documento_devolucion = d.id_documento_devolucion AND dd.estado = 'activo'
JOIN linea_venta lv ON lv.id_linea_venta = d.id_linea_venta
GROUP BY lv.id_linea_venta, lv.cantidad
HAVING SUM(d.cantidad) > lv.cantidad;

-- 4.x (V4) Precios aprobados por la misma persona que los propuso (segregación de funciones, RF-16)
SELECT id_precio FROM precio
WHERE estado_aprobacion = 'aprobado' AND usuario_proponente IS NOT NULL AND usuario_proponente = usuario_aprobador;

-- 4.x (V5) Ventas cuyo total en moneda del documento no cuadra con sus líneas
SELECT v.id_venta, v.moneda, v.total_moneda, SUM(l.total_moneda) AS suma_lineas
FROM venta v JOIN linea_venta l ON l.id_venta = v.id_venta
WHERE v.total_moneda IS NOT NULL
GROUP BY v.id_venta, v.moneda, v.total_moneda
HAVING v.total_moneda <> SUM(l.total_moneda);

-- 4.x (V5) Documentos en moneda extranjera sin tasa coherente o en moneda inexistente / no funcional con tasa 1
SELECT 'venta' AS documento, id_venta AS id, moneda, tasa_cambio FROM venta
WHERE moneda <> (SELECT codigo FROM moneda WHERE funcional) AND tasa_cambio = 1
UNION ALL
SELECT 'compra', id_compra, moneda, tasa_cambio FROM compra
WHERE moneda <> (SELECT codigo FROM moneda WHERE funcional) AND tasa_cambio = 1;

-- 4.x (V5) Exactamente una moneda funcional
SELECT COUNT(*) AS monedas_funcionales FROM moneda WHERE funcional HAVING COUNT(*) <> 1;

