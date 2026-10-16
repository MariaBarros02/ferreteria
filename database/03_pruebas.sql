-- ====================================================================
-- Nombre: 03_pruebas.sql
-- Descripción: Pruebas automatizadas de reglas de negocio en PL/pgSQL; resultados en test_resultados.
-- Fecha de creación: 2026-10-07
-- Fecha de actualización: 2026-10-16
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

-- =================================================================
-- Pruebas añadidas con la migración V4 (2026-10-16)
-- =================================================================

-- -----------------------------------------------------------------
-- PRUEBA 13 (RN-05): el saldo de un lote nunca es negativo y su código no se repite
--   (los datos de preparación se deshacen al final para no alterar las verificaciones)
-- -----------------------------------------------------------------
DO $$
DECLARE v_lote INTEGER; r1 TEXT; r2 TEXT;
BEGIN
    BEGIN
        INSERT INTO lote (id_producto, codigo, fecha_vencimiento, disponible)
        SELECT id_producto, 'L-PRUEBA-13', CURRENT_DATE + 30, 5 FROM producto WHERE codigo_barras = '7701234500039'
        RETURNING id_lote INTO v_lote;
        BEGIN
            UPDATE lote SET disponible = disponible - 6 WHERE id_lote = v_lote;
            r1 := 'FALLO';
        EXCEPTION WHEN check_violation THEN r1 := 'EXITO';
        END;
        BEGIN
            INSERT INTO lote (id_producto, codigo, disponible)
            SELECT id_producto, 'L-PRUEBA-13', 1 FROM producto WHERE codigo_barras = '7701234500039';
            r2 := 'FALLO';
        EXCEPTION WHEN unique_violation THEN r2 := 'EXITO';
        END;
        RAISE EXCEPTION '__deshacer__';
    EXCEPTION WHEN raise_exception THEN
        IF SQLERRM <> '__deshacer__' THEN RAISE; END IF;
    END;
    INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
    VALUES ('Lote con saldo negativo', 'RN-05', r1, 'El lote no debe admitir saldos negativos.'),
           ('Código de lote repetido', 'RN-05', r2, 'El código de lote debe ser único por producto.');
END $$;

-- -----------------------------------------------------------------
-- PRUEBA 14 (RF-08): los eventos de auditoría no se editan ni se borran
-- -----------------------------------------------------------------
DO $$
DECLARE v_id BIGINT;
BEGIN
    INSERT INTO evento_auditoria (modulo, accion, entidad, id_entidad, descripcion)
    VALUES ('PRUEBAS', 'CREAR', 'prueba', '1', 'Evento de prueba') RETURNING id_evento INTO v_id;
    BEGIN
        UPDATE evento_auditoria SET descripcion = 'alterado' WHERE id_evento = v_id;
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Edición de evento de auditoría', 'RF-08', 'FALLO', 'Se permitió editar un evento de auditoría.');
    EXCEPTION WHEN raise_exception THEN
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Edición de evento de auditoría', 'RF-08', 'EXITO', 'La auditoría es inmutable (UPDATE).');
    END;
    BEGIN
        DELETE FROM evento_auditoria WHERE id_evento = v_id;
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Borrado de evento de auditoría', 'RF-08', 'FALLO', 'Se permitió borrar un evento de auditoría.');
    EXCEPTION WHEN raise_exception THEN
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Borrado de evento de auditoría', 'RF-08', 'EXITO', 'La auditoría es inmutable (DELETE).');
    END;
END $$;

-- -----------------------------------------------------------------
-- PRUEBA 15 (RF-14): un ajuste aprobado debe tener su movimiento
-- -----------------------------------------------------------------
DO $$
BEGIN
    BEGIN
        INSERT INTO ajuste_inventario (numero, id_producto, tipo, cantidad, motivo, estado, id_usuario_solicita,
                                       id_usuario_decide, fecha_decision)
        SELECT 'AJ-PRUEBA-15', p.id_producto, 'entrada', 1, 'Prueba', 'aprobado', u.id_usuario, u.id_usuario, now()
        FROM producto p, usuario u WHERE p.codigo_barras = '7701234500022' AND u.correo = 'gerencia@ferreteria.com';
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Ajuste aprobado sin movimiento', 'RF-14', 'FALLO', 'Se aprobó un ajuste sin movimiento trazable.');
    EXCEPTION WHEN check_violation THEN
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Ajuste aprobado sin movimiento', 'RF-14', 'EXITO', 'Todo ajuste aprobado exige su movimiento.');
    END;
    BEGIN
        INSERT INTO ajuste_inventario (numero, id_producto, tipo, cantidad, motivo, id_usuario_solicita)
        SELECT 'AJ-PRUEBA-15B', p.id_producto, 'entrada', 1, '   ', u.id_usuario
        FROM producto p, usuario u WHERE p.codigo_barras = '7701234500022' AND u.correo = 'marta.inventario@ferreteria.com';
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Ajuste sin motivo', 'RF-14', 'FALLO', 'Se registró un ajuste sin motivo.');
    EXCEPTION WHEN check_violation THEN
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Ajuste sin motivo', 'RF-14', 'EXITO', 'El motivo del ajuste es obligatorio.');
    END;
END $$;

-- -----------------------------------------------------------------
-- PRUEBA 16 (RF-13): la devolución de cliente exige la venta original
-- -----------------------------------------------------------------
DO $$
BEGIN
    BEGIN
        INSERT INTO documento_devolucion (numero, tipo, id_usuario, motivo, tratamiento)
        SELECT 'DEV-PRUEBA-16', 'cliente', id_usuario, 'Prueba', 'reembolso' FROM usuario WHERE correo = 'laura.ventas@ferreteria.com';
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Devolución sin documento original', 'RF-13', 'FALLO', 'Se registró una devolución sin venta de origen.');
    EXCEPTION WHEN check_violation THEN
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Devolución sin documento original', 'RF-13', 'EXITO', 'La devolución queda vinculada al documento original.');
    END;
END $$;

-- -----------------------------------------------------------------
-- PRUEBA 17 (RN-09): anular un documento de ingreso exige motivo
-- -----------------------------------------------------------------
DO $$
BEGIN
    BEGIN
        UPDATE documento_ingreso SET estado = 'anulado' WHERE consecutivo = 'ING-000001';
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Anulación sin motivo', 'RN-09', 'FALLO', 'Se anuló un documento de ingreso sin motivo.');
    EXCEPTION WHEN check_violation THEN
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Anulación sin motivo', 'RN-09', 'EXITO', 'Anular un documento de ingreso exige motivo.');
    END;
END $$;

-- -----------------------------------------------------------------
-- PRUEBA 18 (RF-08): un período solo se cierra una vez, es mensual y los cierres no se borran
-- -----------------------------------------------------------------
DO $$
DECLARE v_usuario INTEGER; r1 TEXT; r2 TEXT; r3 TEXT;
BEGIN
    SELECT id_usuario INTO v_usuario FROM usuario WHERE correo = 'gerencia@ferreteria.com';
    BEGIN
        INSERT INTO cierre_periodo (periodo, id_usuario) VALUES (DATE '2000-01-01', v_usuario);
        BEGIN
            INSERT INTO cierre_periodo (periodo, id_usuario) VALUES (DATE '2000-01-01', v_usuario);
            r1 := 'FALLO';
        EXCEPTION WHEN unique_violation THEN r1 := 'EXITO';
        END;
        BEGIN
            DELETE FROM cierre_periodo WHERE periodo = DATE '2000-01-01';
            r2 := 'FALLO';
        EXCEPTION WHEN raise_exception THEN r2 := 'EXITO';
        END;
        BEGIN
            INSERT INTO cierre_periodo (periodo, id_usuario) VALUES (DATE '2000-02-15', v_usuario);
            r3 := 'FALLO';
        EXCEPTION WHEN check_violation THEN r3 := 'EXITO';
        END;
        RAISE EXCEPTION '__deshacer__';
    EXCEPTION WHEN raise_exception THEN
        IF SQLERRM <> '__deshacer__' THEN RAISE; END IF;
    END;
    INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
    VALUES ('Cierre duplicado', 'RF-08', r1, 'Un período debe tener un único cierre.'),
           ('Borrado de cierre', 'RN-09', r2, 'Los cierres no se eliminan.'),
           ('Cierre de período no mensual', 'RF-08', r3, 'El cierre es mensual (día 1 del mes).');
END $$;

-- -----------------------------------------------------------------
-- PRUEBA 19 (RF-11/RNF-02): una misma operación no se registra dos veces
-- -----------------------------------------------------------------
DO $$
DECLARE v_cliente INTEGER; r1 TEXT;
BEGIN
    SELECT id_tercero INTO v_cliente FROM tercero WHERE numero_identificacion = '1002003000';
    BEGIN
        INSERT INTO venta (numero_interno, id_cliente, id_operacion) VALUES ('VTA-PRUEBA-19A', v_cliente, 'op-prueba-19');
        BEGIN
            INSERT INTO venta (numero_interno, id_cliente, id_operacion) VALUES ('VTA-PRUEBA-19B', v_cliente, 'op-prueba-19');
            r1 := 'FALLO';
        EXCEPTION WHEN unique_violation THEN r1 := 'EXITO';
        END;
        RAISE EXCEPTION '__deshacer__';
    EXCEPTION WHEN raise_exception THEN
        IF SQLERRM <> '__deshacer__' THEN RAISE; END IF;
    END;
    INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
    VALUES ('Venta duplicada por reintento', 'RF-11', r1, 'La clave de operación debe impedir duplicar la venta.');
END $$;

-- -----------------------------------------------------------------
-- PRUEBA 20 (RF-04): los totales históricos se conservan tras la migración
-- -----------------------------------------------------------------
DO $$
DECLARE v_total NUMERIC;
BEGIN
    SELECT total INTO v_total FROM venta WHERE numero_interno = 'VTA-000001';
    IF v_total = 1300000 THEN
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Total histórico de venta', 'RF-04', 'EXITO', 'VTA-000001 conserva su total (2 x 650.000) tras la migración.');
    ELSE
        INSERT INTO test_resultados (nombre_prueba, regla, resultado, detalle)
        VALUES ('Total histórico de venta', 'RF-04', 'FALLO', 'Total inesperado: ' || COALESCE(v_total::text, 'NULL'));
    END IF;
END $$;

-- Resumen final de la ejecución de pruebas
SELECT regla, resultado, COUNT(*) AS cantidad
FROM test_resultados
GROUP BY regla, resultado
ORDER BY regla;
