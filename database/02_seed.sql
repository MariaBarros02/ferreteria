-- ====================================================================
-- Nombre: 02_seed.sql
-- Descripción: Datos de prueba coherentes con el levantamiento (taladros, tornillos, pegante). Las contraseñas 'hash_demo' las reemplaza el backend en el perfil dev.
-- Fecha de creación: 2026-10-07
-- Fecha de actualización: 2026-10-07
-- Autor: Equipo Ferretería
-- Versión: 1.0.0
-- ====================================================================
-- =====================================================================
-- 02_seed.sql — Datos de prueba
-- =====================================================================

-- =====================================================================
-- Nombre: 02_seed.sql
-- Descripción: Datos iniciales idempotentes para Ferretería.
-- Si un registro existe, se conserva; si no, se crea.
-- =====================================================================

BEGIN;

-- ============================================================
-- 1. ROLES
-- ============================================================

INSERT INTO rol (nombre)
SELECT v.nombre
FROM (VALUES
    ('Ventas'),
    ('Compras'),
    ('Inventario'),
    ('Administrador'),
    ('Gerencia')
) AS v(nombre)
WHERE NOT EXISTS (
    SELECT 1 FROM rol r WHERE r.nombre = v.nombre
);

-- ============================================================
-- 2. USUARIOS
-- ============================================================

INSERT INTO usuario (nombre, correo, password_hash, id_rol)
SELECT v.nombre, v.correo, 'hash_demo', r.id_rol
FROM (VALUES
    ('Laura Ventas', 'laura.ventas@ferreteria.com', 'Ventas'),
    ('Carlos Compras', 'carlos.compras@ferreteria.com', 'Compras'),
    ('Marta Inventario', 'marta.inventario@ferreteria.com', 'Inventario'),
    ('Admin Sistema', 'admin@ferreteria.com', 'Administrador'),
    ('Gerente General', 'gerencia@ferreteria.com', 'Gerencia')
) AS v(nombre, correo, rol_nombre)
JOIN rol r ON r.nombre = v.rol_nombre
WHERE NOT EXISTS (
    SELECT 1 FROM usuario u WHERE u.correo = v.correo
);

-- ============================================================
-- 3. TERCEROS
-- ============================================================

INSERT INTO tercero (
    tipo_persona, tipo_identificacion, numero_identificacion,
    primer_nombre, primer_apellido, direccion, ciudad, telefono,
    correo, es_cliente, es_proveedor
)
SELECT
    'natural', 'CC', '1002003000',
    'Juan', 'Pérez', 'Cra 10 # 20-30', 'Cali',
    '3001234567', 'juan.perez@correo.com', TRUE, FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM tercero
    WHERE numero_identificacion = '1002003000'
);

INSERT INTO tercero (
    tipo_persona, tipo_identificacion, numero_identificacion,
    razon_social, direccion, ciudad, telefono, correo,
    es_cliente, es_proveedor
)
SELECT
    'juridica', 'NIT', '900123456',
    'Distribuidora Bosch S.A.S.', 'Av. Industrial 45',
    'Bogotá', '6015551234', 'ventas@distribosch.com',
    FALSE, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM tercero
    WHERE numero_identificacion = '900123456'
);

-- ============================================================
-- 4. PRODUCTOS
-- ============================================================

INSERT INTO producto (
    nombre, marca, presentacion, codigo_barras,
    unidad_base, tipo_rastreo, tiene_vencimiento, tiene_garantia
)
SELECT
    v.nombre,
    v.marca,
    v.presentacion,
    v.codigo_barras,
    v.unidad_base,
    v.tipo_rastreo::tipo_rastreo_enum,
    v.tiene_vencimiento,
    v.tiene_garantia
FROM (VALUES
    ('Taladro', 'Bosch', '500W', '7701234500015',
     'unidad', 'serie', FALSE, TRUE),
    ('Tornillo', 'Genérico', '1 pulgada', '7701234500022',
     'unidad', 'ninguno', FALSE, FALSE),
    ('Pegante', 'Sika', '250ml', '7701234500039',
     'unidad', 'lote', TRUE, FALSE)
) AS v(
    nombre, marca, presentacion, codigo_barras,
    unidad_base, tipo_rastreo, tiene_vencimiento, tiene_garantia
)
WHERE NOT EXISTS (
    SELECT 1 FROM producto p
    WHERE p.codigo_barras = v.codigo_barras
);

-- ============================================================
-- 5. CONVERSIONES DE UNIDADES
-- ============================================================

INSERT INTO conversion_unidad (
    id_producto, unidad_presentacion,
    factor_a_unidad_base, precision_decimales
)
SELECT p.id_producto, v.unidad_presentacion, v.factor, 0
FROM producto p
CROSS JOIN (
    VALUES ('caja', 100), ('paquete', 10)
) AS v(unidad_presentacion, factor)
WHERE p.codigo_barras = '7701234500022'
AND NOT EXISTS (
    SELECT 1
    FROM conversion_unidad cu
    WHERE cu.id_producto = p.id_producto
      AND cu.unidad_presentacion = v.unidad_presentacion
);

-- ============================================================
-- 6. INVENTARIO INICIAL
-- ============================================================

INSERT INTO inventario (id_producto, disponible, reservado, danado)
SELECT p.id_producto, 0, 0, 0
FROM producto p
WHERE NOT EXISTS (
    SELECT 1 FROM inventario i
    WHERE i.id_producto = p.id_producto
);

-- ============================================================
-- 7. COMPRA INICIAL AL PROVEEDOR
-- ============================================================

-- Crear la compra solo si todavía no existe la línea de compra
-- correspondiente al taladro y al proveedor.

INSERT INTO compra (id_proveedor, estado)
SELECT t.id_tercero, 'activo'
FROM tercero t
JOIN producto p ON p.codigo_barras = '7701234500015'
WHERE t.numero_identificacion = '900123456'
AND NOT EXISTS (
    SELECT 1
    FROM compra c
    JOIN linea_compra lc ON lc.id_compra = c.id_compra
    WHERE c.id_proveedor = t.id_tercero
      AND lc.id_producto = p.id_producto
      AND lc.cantidad_pedida = 20
      AND lc.costo_pactado = 500000
);

-- Crear la línea si no existe.

INSERT INTO linea_compra (
    id_compra, id_producto, cantidad_pedida, costo_pactado
)
SELECT c.id_compra, p.id_producto, 20, 500000
FROM compra c
JOIN tercero t ON t.id_tercero = c.id_proveedor
JOIN producto p ON p.codigo_barras = '7701234500015'
WHERE t.numero_identificacion = '900123456'
AND NOT EXISTS (
    SELECT 1
    FROM linea_compra lc
    WHERE lc.id_compra = c.id_compra
      AND lc.id_producto = p.id_producto
      AND lc.cantidad_pedida = 20
      AND lc.costo_pactado = 500000
)
ORDER BY c.id_compra DESC
LIMIT 1;

-- ============================================================
-- 8. DOCUMENTO DE INGRESO Y RECEPCIÓN
-- ============================================================

INSERT INTO documento_ingreso (
    consecutivo, estado, factura_proveedor
)
SELECT 'ING-000001', 'activo', 'FACT-BOSCH-0001'
WHERE NOT EXISTS (
    SELECT 1 FROM documento_ingreso
    WHERE consecutivo = 'ING-000001'
);

INSERT INTO recepcion (
    id_linea_compra, id_documento_ingreso, cantidad_recibida
)
SELECT lc.id_linea_compra, di.id_documento_ingreso, 20
FROM linea_compra lc
JOIN producto p ON p.id_producto = lc.id_producto
JOIN compra c ON c.id_compra = lc.id_compra
JOIN tercero t ON t.id_tercero = c.id_proveedor
CROSS JOIN documento_ingreso di
WHERE p.codigo_barras = '7701234500015'
  AND t.numero_identificacion = '900123456'
  AND lc.cantidad_pedida = 20
  AND lc.costo_pactado = 500000
  AND di.consecutivo = 'ING-000001'
  AND NOT EXISTS (
      SELECT 1 FROM recepcion r
      WHERE r.id_linea_compra = lc.id_linea_compra
        AND r.id_documento_ingreso = di.id_documento_ingreso
  )
ORDER BY lc.id_linea_compra DESC
LIMIT 1;

UPDATE linea_compra lc
SET cantidad_recibida = 20
FROM compra c, tercero t, producto p
WHERE c.id_compra = lc.id_compra
  AND t.id_tercero = c.id_proveedor
  AND p.id_producto = lc.id_producto
  AND t.numero_identificacion = '900123456'
  AND p.codigo_barras = '7701234500015'
  AND lc.cantidad_pedida = 20
  AND lc.costo_pactado = 500000
  AND lc.cantidad_recibida < 20
  AND EXISTS (
      SELECT 1
      FROM recepcion r
      JOIN documento_ingreso di
        ON di.id_documento_ingreso = r.id_documento_ingreso
      WHERE r.id_linea_compra = lc.id_linea_compra
        AND di.consecutivo = 'ING-000001'
  );

-- ============================================================
-- 9. INVENTARIO Y AUDITORÍA DE LA RECEPCIÓN
-- ============================================================

-- Aumentar el inventario solo si el movimiento no se ha registrado.

UPDATE inventario i
SET disponible = i.disponible + 20
FROM producto p
WHERE p.id_producto = i.id_producto
  AND p.codigo_barras = '7701234500015'
  AND NOT EXISTS (
      SELECT 1
      FROM movimiento_inventario mi
      WHERE mi.tipo = 'compra'
        AND mi.id_producto = p.id_producto
        AND mi.documento_relacionado = 'ING-000001'
  );

INSERT INTO movimiento_inventario (
    tipo, id_producto, cantidad, id_usuario, ip,
    documento_relacionado, valor_anterior, valor_nuevo, motivo
)
SELECT
    'compra', p.id_producto, 20, u.id_usuario,
    '192.168.1.10'::inet, 'ING-000001', 0, 20,
    'Recepción de compra al proveedor Bosch'
FROM producto p
CROSS JOIN usuario u
WHERE p.codigo_barras = '7701234500015'
  AND u.correo = 'marta.inventario@ferreteria.com'
  AND NOT EXISTS (
      SELECT 1
      FROM movimiento_inventario mi
      WHERE mi.tipo = 'compra'
        AND mi.id_producto = p.id_producto
        AND mi.documento_relacionado = 'ING-000001'
  );

-- ============================================================
-- 10. PRECIO DEL TALADRO
-- ============================================================

INSERT INTO precio (
    id_producto, costo_promedio, porcentaje_incremento,
    precio_propuesto, precio_vigente, estado_aprobacion,
    usuario_aprobador, fecha_vigencia
)
SELECT
    p.id_producto, 500000, 0.30, 650000, 650000,
    'aprobado', u.id_usuario, now()
FROM producto p
CROSS JOIN usuario u
WHERE p.codigo_barras = '7701234500015'
  AND u.correo = 'gerencia@ferreteria.com'
  AND NOT EXISTS (
      SELECT 1 FROM precio pr
      WHERE pr.id_producto = p.id_producto
        AND pr.costo_promedio = 500000
        AND pr.precio_vigente = 650000
  );

-- ============================================================
-- 11. VENTA AL CLIENTE JUAN PÉREZ
-- ============================================================

INSERT INTO venta (numero_interno, id_cliente, estado)
SELECT 'VTA-000001', t.id_tercero, 'activo'
FROM tercero t
WHERE t.numero_identificacion = '1002003000'
AND NOT EXISTS (
    SELECT 1 FROM venta v
    WHERE v.numero_interno = 'VTA-000001'
);

INSERT INTO linea_venta (
    id_venta, id_producto, cantidad, precio_aplicado
)
SELECT v.id_venta, p.id_producto, 2, 650000
FROM venta v
CROSS JOIN producto p
WHERE v.numero_interno = 'VTA-000001'
  AND p.codigo_barras = '7701234500015'
  AND NOT EXISTS (
      SELECT 1 FROM linea_venta lv
      WHERE lv.id_venta = v.id_venta
        AND lv.id_producto = p.id_producto
        AND lv.cantidad = 2
        AND lv.precio_aplicado = 650000
  );

INSERT INTO factura (
    id_venta, numeracion_dian, modalidad, impuestos, descuentos
)
SELECT v.id_venta, 'DIAN-SETP-0001', 'electronica', 0, 0
FROM venta v
WHERE v.numero_interno = 'VTA-000001'
AND NOT EXISTS (
    SELECT 1 FROM factura f
    WHERE f.id_venta = v.id_venta
       OR f.numeracion_dian = 'DIAN-SETP-0001'
);

-- Descontar las unidades una sola vez.

UPDATE inventario i
SET disponible = i.disponible - 2
FROM producto p
WHERE p.id_producto = i.id_producto
  AND p.codigo_barras = '7701234500015'
  AND NOT EXISTS (
      SELECT 1
      FROM movimiento_inventario mi
      WHERE mi.tipo = 'venta'
        AND mi.id_producto = p.id_producto
        AND mi.documento_relacionado = 'VTA-000001'
  )
  AND EXISTS (
      SELECT 1 FROM venta v
      JOIN linea_venta lv ON lv.id_venta = v.id_venta
      WHERE v.numero_interno = 'VTA-000001'
        AND lv.id_producto = p.id_producto
        AND lv.cantidad = 2
        AND lv.precio_aplicado = 650000
  );

INSERT INTO movimiento_inventario (
    tipo, id_producto, cantidad, id_usuario, ip,
    documento_relacionado, valor_anterior, valor_nuevo, motivo
)
SELECT
    'venta', p.id_producto, -2, u.id_usuario,
    '192.168.1.20'::inet, 'VTA-000001', 20, 18,
    'Venta a cliente Juan Pérez'
FROM producto p
CROSS JOIN usuario u
WHERE p.codigo_barras = '7701234500015'
  AND u.correo = 'laura.ventas@ferreteria.com'
  AND EXISTS (
      SELECT 1 FROM venta v
      JOIN linea_venta lv ON lv.id_venta = v.id_venta
      WHERE v.numero_interno = 'VTA-000001'
        AND lv.id_producto = p.id_producto
        AND lv.cantidad = 2
        AND lv.precio_aplicado = 650000
  )
  AND NOT EXISTS (
      SELECT 1
      FROM movimiento_inventario mi
      WHERE mi.tipo = 'venta'
        AND mi.id_producto = p.id_producto
        AND mi.documento_relacionado = 'VTA-000001'
  );

-- ============================================================
-- 12. TORNILLOS: ENTRADA DE 300 UNIDADES
-- ============================================================

UPDATE inventario i
SET disponible = i.disponible + 300
FROM producto p
WHERE p.id_producto = i.id_producto
  AND p.codigo_barras = '7701234500022'
  AND NOT EXISTS (
      SELECT 1
      FROM movimiento_inventario mi
      WHERE mi.tipo = 'compra'
        AND mi.id_producto = p.id_producto
        AND mi.documento_relacionado = 'ING-000002'
  );

INSERT INTO movimiento_inventario (
    tipo, id_producto, cantidad, id_usuario, ip,
    documento_relacionado, valor_anterior, valor_nuevo, motivo
)
SELECT
    'compra', p.id_producto, 300, u.id_usuario,
    '192.168.1.10'::inet, 'ING-000002', 0, 300,
    'Recepción de 3 cajas de tornillos (100 c/u)'
FROM producto p
CROSS JOIN usuario u
WHERE p.codigo_barras = '7701234500022'
  AND u.correo = 'marta.inventario@ferreteria.com'
  AND NOT EXISTS (
      SELECT 1
      FROM movimiento_inventario mi
      WHERE mi.tipo = 'compra'
        AND mi.id_producto = p.id_producto
        AND mi.documento_relacionado = 'ING-000002'
  );

-- ============================================================
-- 13. TORNILLOS: SALIDA DE 17 UNIDADES
-- ============================================================

UPDATE inventario i
SET disponible = i.disponible - 17
FROM producto p
WHERE p.id_producto = i.id_producto
  AND p.codigo_barras = '7701234500022'
  AND NOT EXISTS (
      SELECT 1
      FROM movimiento_inventario mi
      WHERE mi.tipo = 'venta'
        AND mi.id_producto = p.id_producto
        AND mi.documento_relacionado = 'VTA-000002'
  );

INSERT INTO movimiento_inventario (
    tipo, id_producto, cantidad, id_usuario, ip,
    documento_relacionado, valor_anterior, valor_nuevo, motivo
)
SELECT
    'venta', p.id_producto, -17, u.id_usuario,
    '192.168.1.20'::inet, 'VTA-000002', 300, 283,
    'Venta de 17 tornillos'
FROM producto p
CROSS JOIN usuario u
WHERE p.codigo_barras = '7701234500022'
  AND u.correo = 'laura.ventas@ferreteria.com'
  AND NOT EXISTS (
      SELECT 1
      FROM movimiento_inventario mi
      WHERE mi.tipo = 'venta'
        AND mi.id_producto = p.id_producto
        AND mi.documento_relacionado = 'VTA-000002'
  );

COMMIT;
