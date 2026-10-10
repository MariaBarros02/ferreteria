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

BEGIN;

-- Roles y usuarios
INSERT INTO rol (nombre) VALUES ('Ventas'), ('Compras'), ('Inventario'), ('Administrador'), ('Gerencia')
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO usuario (nombre, correo, password_hash, id_rol) VALUES
    ('Laura Ventas',     'laura.ventas@ferreteria.com',     'hash_demo', (SELECT id_rol FROM rol WHERE nombre = 'Ventas')),
    ('Carlos Compras',   'carlos.compras@ferreteria.com',   'hash_demo', (SELECT id_rol FROM rol WHERE nombre = 'Compras')),
    ('Marta Inventario', 'marta.inventario@ferreteria.com', 'hash_demo', (SELECT id_rol FROM rol WHERE nombre = 'Inventario')),
    ('Admin Sistema',    'admin@ferreteria.com',            'hash_demo', (SELECT id_rol FROM rol WHERE nombre = 'Administrador')),
    ('Gerente General',  'gerencia@ferreteria.com',         'hash_demo', (SELECT id_rol FROM rol WHERE nombre = 'Gerencia'));

-- Terceros
INSERT INTO tercero (tipo_persona, tipo_identificacion, numero_identificacion,
                      primer_nombre, primer_apellido, direccion, ciudad, telefono, correo,
                      es_cliente, es_proveedor)
VALUES ('natural', 'CC', '1002003000', 'Juan', 'Pérez', 'Cra 10 # 20-30', 'Cali', '3001234567',
        'juan.perez@correo.com', TRUE, FALSE);

INSERT INTO tercero (tipo_persona, tipo_identificacion, numero_identificacion,
                      razon_social, direccion, ciudad, telefono, correo,
                      es_cliente, es_proveedor)
VALUES ('juridica', 'NIT', '900123456', 'Distribuidora Bosch S.A.S.', 'Av. Industrial 45',
        'Bogotá', '6015551234', 'ventas@distribosch.com', FALSE, TRUE);

-- Productos (referencia = producto + marca + presentación)
INSERT INTO producto (nombre, marca, presentacion, codigo_barras, unidad_base, tipo_rastreo, tiene_vencimiento, tiene_garantia)
VALUES
    ('Taladro', 'Bosch', '500W', '7701234500015', 'unidad', 'serie', FALSE, TRUE),
    ('Tornillo', 'Genérico', '1 pulgada', '7701234500022', 'unidad', 'ninguno', FALSE, FALSE),
    ('Pegante', 'Sika', '250ml', '7701234500039', 'unidad', 'lote', TRUE, FALSE);

-- Conversiones (tornillo: caja = 100 unidades, paquete = 10 unidades)
INSERT INTO conversion_unidad (id_producto, unidad_presentacion, factor_a_unidad_base, precision_decimales)
SELECT id_producto, 'caja', 100, 0 FROM producto WHERE codigo_barras = '7701234500022'
UNION ALL
SELECT id_producto, 'paquete', 10, 0 FROM producto WHERE codigo_barras = '7701234500022';

-- Inventario inicial en cero (se alimentará con la recepción de compra)
INSERT INTO inventario (id_producto, disponible, reservado, danado)
SELECT id_producto, 0, 0, 0 FROM producto;

-- Compra al proveedor Bosch: 20 taladros a $500.000 c/u
INSERT INTO compra (id_proveedor, estado)
SELECT id_tercero, 'activo' FROM tercero WHERE numero_identificacion = '900123456';

INSERT INTO linea_compra (id_compra, id_producto, cantidad_pedida, costo_pactado)
SELECT c.id_compra, p.id_producto, 20, 500000
FROM compra c, producto p
WHERE p.codigo_barras = '7701234500015'
ORDER BY c.id_compra DESC LIMIT 1;

-- Documento de ingreso y recepción total de los 20 taladros
INSERT INTO documento_ingreso (consecutivo, estado, factura_proveedor)
VALUES ('ING-000001', 'activo', 'FACT-BOSCH-0001');

INSERT INTO recepcion (id_linea_compra, id_documento_ingreso, cantidad_recibida)
SELECT lc.id_linea_compra, di.id_documento_ingreso, 20
FROM linea_compra lc, documento_ingreso di
WHERE di.consecutivo = 'ING-000001'
ORDER BY lc.id_linea_compra DESC LIMIT 1;

UPDATE linea_compra SET cantidad_recibida = 20
WHERE id_linea_compra = (SELECT MAX(id_linea_compra) FROM linea_compra);

-- Efecto de la recepción sobre el inventario (+20 taladros disponibles)
UPDATE inventario SET disponible = disponible + 20
WHERE id_producto = (SELECT id_producto FROM producto WHERE codigo_barras = '7701234500015');

INSERT INTO movimiento_inventario (tipo, id_producto, cantidad, id_usuario, ip,
                                    documento_relacionado, valor_anterior, valor_nuevo, motivo)
SELECT 'compra', p.id_producto, 20, u.id_usuario, '192.168.1.10'::inet,
       'ING-000001', 0, 20, 'Recepción de compra al proveedor Bosch'
FROM producto p, usuario u
WHERE p.codigo_barras = '7701234500015' AND u.correo = 'marta.inventario@ferreteria.com';

-- Precio propuesto y aprobado (costo promedio $500.000, margen 30% => $650.000)
INSERT INTO precio (id_producto, costo_promedio, porcentaje_incremento, precio_propuesto,
                     precio_vigente, estado_aprobacion, usuario_aprobador, fecha_vigencia)
SELECT p.id_producto, 500000, 0.30, 650000, 650000, 'aprobado', u.id_usuario, now()
FROM producto p, usuario u
WHERE p.codigo_barras = '7701234500015' AND u.correo = 'gerencia@ferreteria.com';

-- Venta de 2 taladros al cliente Juan Pérez
INSERT INTO venta (numero_interno, id_cliente, estado)
SELECT 'VTA-000001', id_tercero, 'activo' FROM tercero WHERE numero_identificacion = '1002003000';

INSERT INTO linea_venta (id_venta, id_producto, cantidad, precio_aplicado)
SELECT v.id_venta, p.id_producto, 2, 650000
FROM venta v, producto p
WHERE v.numero_interno = 'VTA-000001' AND p.codigo_barras = '7701234500015';

INSERT INTO factura (id_venta, numeracion_dian, modalidad, impuestos, descuentos)
SELECT id_venta, 'DIAN-SETP-0001', 'electronica', 0, 0
FROM venta WHERE numero_interno = 'VTA-000001';

UPDATE inventario SET disponible = disponible - 2
WHERE id_producto = (SELECT id_producto FROM producto WHERE codigo_barras = '7701234500015');

INSERT INTO movimiento_inventario (tipo, id_producto, cantidad, id_usuario, ip,
                                    documento_relacionado, valor_anterior, valor_nuevo, motivo)
SELECT 'venta', p.id_producto, -2, u.id_usuario, '192.168.1.20'::inet,
       'VTA-000001', 20, 18, 'Venta a cliente Juan Pérez'
FROM producto p, usuario u
WHERE p.codigo_barras = '7701234500015' AND u.correo = 'laura.ventas@ferreteria.com';

-- Tornillos: simular la entrada de 3 cajas (300 unidades) y la venta de 17,
-- registrando también sus movimientos de auditoría (mismo patrón que el taladro).
UPDATE inventario SET disponible = disponible + 300
WHERE id_producto = (SELECT id_producto FROM producto WHERE codigo_barras = '7701234500022');

INSERT INTO movimiento_inventario (tipo, id_producto, cantidad, id_usuario, ip,
                                    documento_relacionado, valor_anterior, valor_nuevo, motivo)
SELECT 'compra', p.id_producto, 300, u.id_usuario, '192.168.1.10'::inet,
       'ING-000002', 0, 300, 'Recepción de 3 cajas de tornillos (100 c/u)'
FROM producto p, usuario u
WHERE p.codigo_barras = '7701234500022' AND u.correo = 'marta.inventario@ferreteria.com';

UPDATE inventario SET disponible = disponible - 17
WHERE id_producto = (SELECT id_producto FROM producto WHERE codigo_barras = '7701234500022');

INSERT INTO movimiento_inventario (tipo, id_producto, cantidad, id_usuario, ip,
                                    documento_relacionado, valor_anterior, valor_nuevo, motivo)
SELECT 'venta', p.id_producto, -17, u.id_usuario, '192.168.1.20'::inet,
       'VTA-000002', 300, 283, 'Venta de 17 tornillos'
FROM producto p, usuario u
WHERE p.codigo_barras = '7701234500022' AND u.correo = 'laura.ventas@ferreteria.com';

COMMIT;
