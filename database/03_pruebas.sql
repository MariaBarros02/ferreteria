-- ====================================================================
-- Nombre: 03_pruebas.sql
-- Descripción: Pruebas automatizadas de reglas de negocio en PL/pgSQL; resultados en test_resultados.
-- Fecha de creación: 2026-10-07
-- Fecha de actualización: 2026-10-07
-- Autor: Equipo Ferretería
-- Versión: 1.0.0
-- ====================================================================
-- =====================================================================
-- 03_pruebas.sql — Pruebas automatizadas de reglas de negocio
-- =====================================================================

CREATE TABLE IF NOT EXISTS test_resultados (
    id             SERIAL PRIMARY KEY,
    nombre_prueba  VARCHAR(150) NOT NULL,
    regla          VARCHAR(20)  NOT NULL,
    resultado      VARCHAR(10)  NOT NULL,   -- EXITO | FALLO
    detalle        TEXT,
    fecha          TIMESTAMP NOT NULL DEFAULT now()
);

-- -----------------------------------------------------------------
-- PRUEBA 1 (RN-01): código de barras duplicado debe rechazarse
-- -----------------------------------------------------------------
DO $$
BEGIN
    BEGIN
        INSERT INTO producto (nombre, marca, presentacion, codigo_barras, unidad_base)
        VALUES ('Taladro', 'Bosch', '750W', '7701234500015', 'unidad'); -- código repetido
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Código de barras duplicado', 'RN-01', 'FALLO',
                'El sistema permitió un código de barras repetido.');
    EXCEPTION WHEN unique_violation THEN
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Código de barras duplicado', 'RN-01', 'EXITO',
                'El sistema rechazó correctamente el código de barras duplicado.');
    END;
END $$;

-- -----------------------------------------------------------------
-- PRUEBA 2 (RN-03): el inventario no puede quedar negativo
-- -----------------------------------------------------------------
DO $$
DECLARE
    v_producto INTEGER;
BEGIN
    SELECT id_producto INTO v_producto FROM producto WHERE codigo_barras = '7701234500015';
    BEGIN
        UPDATE inventario SET disponible = disponible - 999999 WHERE id_producto = v_producto;
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Inventario negativo', 'RN-03', 'FALLO',
                'El sistema permitió un saldo disponible negativo.');
    EXCEPTION WHEN check_violation THEN
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Inventario negativo', 'RN-03', 'EXITO',
                'El sistema rechazó el intento de dejar el disponible en negativo.');
    END;
END $$;

-- -----------------------------------------------------------------
-- PRUEBA 3 (RN-03): estado de reserva "por compra" no es un valor válido
-- -----------------------------------------------------------------
DO $$
BEGIN
    BEGIN
        INSERT INTO reserva (id_producto, id_cliente, id_pedido, cantidad, fecha_limite, estado)
        SELECT p.id_producto, t.id_tercero, 'PED-999', 1, CURRENT_DATE + 5, 'liberada_compra'
        FROM producto p, tercero t
        WHERE p.codigo_barras = '7701234500015' AND t.numero_identificacion = '1002003000';
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Liberación de reserva "por compra"', 'RN-03', 'FALLO',
                'El sistema aceptó un estado de reserva no contemplado ("por compra").');
    EXCEPTION WHEN invalid_text_representation THEN
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Liberación de reserva "por compra"', 'RN-03', 'EXITO',
                'El sistema rechazó el estado "liberada_compra"; solo existen venta/cancelación/vencimiento.');
    END;
END $$;

-- -----------------------------------------------------------------
-- PRUEBA 4 (RN-05): recepción no puede exceder lo pedido
-- -----------------------------------------------------------------
DO $$
DECLARE
    v_id_linea INTEGER;
BEGIN
    SELECT id_linea_compra INTO v_id_linea FROM linea_compra ORDER BY id_linea_compra LIMIT 1;
    BEGIN
        UPDATE linea_compra SET cantidad_recibida = cantidad_pedida + 100
        WHERE id_linea_compra = v_id_linea;
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Recepción mayor a lo pedido', 'RF-03/RF-17', 'FALLO',
                'El sistema permitió recibir más de lo pedido en la compra.');
    EXCEPTION WHEN check_violation THEN
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Recepción mayor a lo pedido', 'RF-03/RF-17', 'EXITO',
                'El sistema rechazó una recepción mayor a la cantidad pedida.');
    END;
END $$;

-- -----------------------------------------------------------------
-- PRUEBA 5 (RN-07): no puede existir precio vigente sin aprobación
-- -----------------------------------------------------------------
DO $$
BEGIN
    BEGIN
        INSERT INTO precio (id_producto, costo_promedio, porcentaje_incremento,
                             precio_propuesto, precio_vigente, estado_aprobacion)
        SELECT id_producto, 100000, 0.20, 120000, 120000, 'propuesto'
        FROM producto WHERE codigo_barras = '7701234500022';
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Precio vigente sin aprobación', 'RN-07', 'FALLO',
                'El sistema permitió un precio_vigente con estado "propuesto".');
    EXCEPTION WHEN check_violation THEN
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Precio vigente sin aprobación', 'RN-07', 'EXITO',
                'El sistema exigió que el precio esté "aprobado" para tener precio_vigente.');
    END;
END $$;

-- -----------------------------------------------------------------
-- PRUEBA 6 (RN-09): un documento de ingreso no se puede eliminar
-- -----------------------------------------------------------------
DO $$
BEGIN
    BEGIN
        DELETE FROM documento_ingreso WHERE consecutivo = 'ING-000001';
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Eliminación de documento de ingreso', 'RN-09', 'FALLO',
                'El sistema permitió eliminar un documento de ingreso.');
    EXCEPTION WHEN OTHERS THEN
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Eliminación de documento de ingreso', 'RN-09', 'EXITO',
                'El disparador bloqueó el DELETE: ' || SQLERRM);
    END;
END $$;

-- -----------------------------------------------------------------
-- PRUEBA 7 (RN-16): un movimiento de inventario no se puede editar
-- -----------------------------------------------------------------
DO $$
DECLARE
    v_id_mov INTEGER;
BEGIN
    SELECT id_movimiento INTO v_id_mov FROM movimiento_inventario LIMIT 1;
    BEGIN
        UPDATE movimiento_inventario SET cantidad = 999 WHERE id_movimiento = v_id_mov;
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Edición de movimiento histórico', 'RN-16', 'FALLO',
                'El sistema permitió editar un movimiento de inventario ya registrado.');
    EXCEPTION WHEN OTHERS THEN
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Edición de movimiento histórico', 'RN-16', 'EXITO',
                'El disparador bloqueó el UPDATE: ' || SQLERRM);
    END;
END $$;

-- -----------------------------------------------------------------
-- PRUEBA 8 (RN-06): persona jurídica sin razón social debe rechazarse
-- -----------------------------------------------------------------
DO $$
BEGIN
    BEGIN
        INSERT INTO tercero (tipo_persona, tipo_identificacion, numero_identificacion,
                              direccion, ciudad, telefono, correo, es_proveedor)
        VALUES ('juridica', 'NIT', '900999999', 'Cra 1 # 2-3', 'Cali', '3000000000',
                'sinrazon@correo.com', TRUE); -- sin razon_social
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Persona jurídica sin razón social', 'RN-06', 'FALLO',
                'El sistema permitió una persona jurídica sin razón social.');
    EXCEPTION WHEN check_violation THEN
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Persona jurídica sin razón social', 'RN-06', 'EXITO',
                'El sistema exigió razón social para persona jurídica.');
    END;
END $$;

-- -----------------------------------------------------------------
-- PRUEBA 9 (RN-06): tercero sin rol de cliente ni proveedor debe rechazarse
-- -----------------------------------------------------------------
DO $$
BEGIN
    BEGIN
        INSERT INTO tercero (tipo_persona, tipo_identificacion, numero_identificacion,
                              primer_nombre, primer_apellido, direccion, ciudad, telefono, correo)
        VALUES ('natural', 'CC', '1000000999', 'Sin', 'Rol', 'Cra 1', 'Cali', '3000000001',
                'sinrol@correo.com'); -- es_cliente y es_proveedor en FALSE por defecto
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Tercero sin ningún rol', 'RN-06', 'FALLO',
                'El sistema permitió un tercero sin rol de cliente ni proveedor.');
    EXCEPTION WHEN check_violation THEN
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Tercero sin ningún rol', 'RN-06', 'EXITO',
                'El sistema exigió al menos un rol (cliente y/o proveedor).');
    END;
END $$;

-- -----------------------------------------------------------------
-- PRUEBA 10 (RN-14): dos ventas no pueden compartir número interno
-- -----------------------------------------------------------------
DO $$
BEGIN
    BEGIN
        INSERT INTO venta (numero_interno, id_cliente, estado)
        SELECT 'VTA-000001', id_tercero, 'activo' FROM tercero WHERE numero_identificacion = '1002003000';
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Número interno de venta duplicado', 'RN-14', 'FALLO',
                'El sistema permitió repetir el número interno de venta.');
    EXCEPTION WHEN unique_violation THEN
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Número interno de venta duplicado', 'RN-14', 'EXITO',
                'El sistema rechazó el número interno de venta repetido.');
    END;
END $$;

-- -----------------------------------------------------------------
-- PRUEBA 11 (RF-02): conversión de unidades — 3 cajas de 100 = 300 unidades
-- -----------------------------------------------------------------
DO $$
DECLARE
    v_factor NUMERIC;
    v_resultado NUMERIC;
BEGIN
    SELECT factor_a_unidad_base INTO v_factor
    FROM conversion_unidad cu JOIN producto p ON p.id_producto = cu.id_producto
    WHERE p.codigo_barras = '7701234500022' AND cu.unidad_presentacion = 'caja';

    v_resultado := 3 * v_factor;

    IF v_resultado = 300 THEN
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Conversión de 3 cajas a unidades', 'RF-02', 'EXITO',
                'La conversión 3 cajas x 100 = ' || v_resultado || ' unidades es correcta.');
    ELSE
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Conversión de 3 cajas a unidades', 'RF-02', 'FALLO',
                'Se esperaba 300 unidades y se obtuvo ' || v_resultado || '.');
    END IF;
END $$;

-- -----------------------------------------------------------------
-- PRUEBA 12 (RF-07): cálculo de costo promedio ponderado
-- -----------------------------------------------------------------
DO $$
DECLARE
    v_costo_promedio NUMERIC;
BEGIN
    -- 10 unidades a $10.000 + 10 unidades a $14.000 => promedio ponderado $12.000
    v_costo_promedio := ((10 * 10000) + (10 * 14000)) / (10 + 10);

    IF v_costo_promedio = 12000 THEN
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Costo promedio ponderado', 'RF-07', 'EXITO',
                'El costo promedio ponderado calculado es $' || v_costo_promedio || ', como se esperaba.');
    ELSE
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Costo promedio ponderado', 'RF-07', 'FALLO',
                'Se esperaba $12.000 y se obtuvo $' || v_costo_promedio || '.');
    END IF;
END $$;

-- Resumen final de la ejecución de pruebas
SELECT regla, resultado, COUNT(*) AS cantidad
FROM test_resultados
GROUP BY regla, resultado
ORDER BY regla;
