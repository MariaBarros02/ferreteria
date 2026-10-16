-- ====================================================================
-- Nombre: V4__modulos_operativos.sql
-- Descripción: Migración Flyway V4: lotes y vencimientos, totales e impuestos de venta, compras con
--              recepción trazable, precios con proponente/aprobador, reservas, devoluciones con
--              documento, ajustes, sesiones de conteo, cierres con resumen, auditoría general de
--              eventos e importaciones de Excel.
--              Compatible con datos existentes: las columnas nuevas son opcionales o tienen un valor
--              por defecto que conserva el comportamiento anterior; no se inventan fechas de
--              vencimiento ni usuarios para registros históricos.
--              Idempotente (IF NOT EXISTS / verificaciones en pg_constraint), porque database/init-db.sh
--              la aplica por psql y Flyway puede volver a ejecutarla al hacer baseline.
-- Fecha de creación: 2026-10-16
-- Fecha de actualización: 2026-10-16
-- Autor: Equipo Ferretería
-- Versión: 1.0.0
-- ====================================================================

-- ---------------------------------------------------------------------
-- 1. Productos (RF-01, RF-05, RF-07): desactivación, categoría, IVA,
--    incremento para precio propuesto, costo promedio, stock mínimo y garantía
-- ---------------------------------------------------------------------
ALTER TABLE producto ADD COLUMN IF NOT EXISTS activo BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE producto ADD COLUMN IF NOT EXISTS descripcion VARCHAR(500);
ALTER TABLE producto ADD COLUMN IF NOT EXISTS categoria VARCHAR(100);
-- 0 conserva el comportamiento anterior (ventas sin impuesto calculado); se configura por producto.
ALTER TABLE producto ADD COLUMN IF NOT EXISTS porcentaje_iva NUMERIC(5,2) NOT NULL DEFAULT 0
    CHECK (porcentaje_iva >= 0 AND porcentaje_iva <= 100);
-- Recargo sobre costo para la propuesta automática de precio (RN-07). NULL = sin propuesta automática.
ALTER TABLE producto ADD COLUMN IF NOT EXISTS porcentaje_incremento NUMERIC(6,4)
    CHECK (porcentaje_incremento IS NULL OR porcentaje_incremento >= 0);
ALTER TABLE producto ADD COLUMN IF NOT EXISTS costo_promedio NUMERIC(14,4)
    CHECK (costo_promedio IS NULL OR costo_promedio >= 0);
ALTER TABLE producto ADD COLUMN IF NOT EXISTS stock_minimo NUMERIC(14,4)
    CHECK (stock_minimo IS NULL OR stock_minimo >= 0);
ALTER TABLE producto ADD COLUMN IF NOT EXISTS meses_garantia INTEGER
    CHECK (meses_garantia IS NULL OR meses_garantia > 0);

-- Costo promedio inicial: el último costo promedio ya registrado en el historial de precios (dato persistido).
UPDATE producto p SET costo_promedio = x.costo_promedio
FROM (SELECT DISTINCT ON (id_producto) id_producto, costo_promedio
      FROM precio ORDER BY id_producto, id_precio DESC) x
WHERE x.id_producto = p.id_producto AND p.costo_promedio IS NULL;

CREATE INDEX IF NOT EXISTS idx_producto_activo ON producto (activo);

-- ---------------------------------------------------------------------
-- 2. Lotes y vencimientos (RF-05, RN-05). La fecha de vencimiento pertenece
--    al lote, no al producto: una referencia puede tener lotes con fechas distintas.
--    disponible = unidades vendibles o reservadas del lote; danado = no aptas.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS lote (
    id_lote           SERIAL PRIMARY KEY,
    id_producto       INTEGER NOT NULL REFERENCES producto(id_producto),
    codigo            VARCHAR(50) NOT NULL,
    fecha_vencimiento DATE,
    disponible        NUMERIC(14,4) NOT NULL DEFAULT 0 CHECK (disponible >= 0),
    danado            NUMERIC(14,4) NOT NULL DEFAULT 0 CHECK (danado >= 0),
    fecha_ingreso     TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_lote_producto_codigo UNIQUE (id_producto, codigo)
);
CREATE INDEX IF NOT EXISTS idx_lote_vencimiento ON lote (fecha_vencimiento) WHERE disponible > 0;

-- ---------------------------------------------------------------------
-- 3. Movimientos: estado de inventario afectado y lote (RF-05, RF-08)
--    Los movimientos existentes afectaban siempre el saldo disponible.
-- ---------------------------------------------------------------------
ALTER TABLE movimiento_inventario ADD COLUMN IF NOT EXISTS estado_inventario VARCHAR(12) NOT NULL DEFAULT 'disponible'
    CHECK (estado_inventario IN ('disponible', 'reservado', 'danado'));
ALTER TABLE movimiento_inventario ADD COLUMN IF NOT EXISTS id_lote INTEGER REFERENCES lote(id_lote);
CREATE INDEX IF NOT EXISTS idx_movimiento_fecha ON movimiento_inventario (fecha_hora);
CREATE INDEX IF NOT EXISTS idx_movimiento_documento ON movimiento_inventario (documento_relacionado);

-- ---------------------------------------------------------------------
-- 4. Ventas (RF-04, RF-18, RF-19): usuario responsable, totales persistidos,
--    impuestos/descuentos por línea, garantía y clave de idempotencia
-- ---------------------------------------------------------------------
ALTER TABLE venta ADD COLUMN IF NOT EXISTS id_usuario INTEGER REFERENCES usuario(id_usuario);
ALTER TABLE venta ADD COLUMN IF NOT EXISTS subtotal NUMERIC(16,2) CHECK (subtotal IS NULL OR subtotal >= 0);
ALTER TABLE venta ADD COLUMN IF NOT EXISTS total_descuentos NUMERIC(16,2) CHECK (total_descuentos IS NULL OR total_descuentos >= 0);
ALTER TABLE venta ADD COLUMN IF NOT EXISTS total_impuestos NUMERIC(16,2) CHECK (total_impuestos IS NULL OR total_impuestos >= 0);
ALTER TABLE venta ADD COLUMN IF NOT EXISTS total NUMERIC(16,2) CHECK (total IS NULL OR total >= 0);
ALTER TABLE venta ADD COLUMN IF NOT EXISTS observaciones VARCHAR(255);
ALTER TABLE venta ADD COLUMN IF NOT EXISTS id_operacion VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS uq_venta_id_operacion ON venta (id_operacion) WHERE id_operacion IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_venta_fecha ON venta (fecha);
CREATE INDEX IF NOT EXISTS idx_venta_cliente ON venta (id_cliente);

ALTER TABLE linea_venta ADD COLUMN IF NOT EXISTS unidad VARCHAR(20);
ALTER TABLE linea_venta ADD COLUMN IF NOT EXISTS cantidad_presentacion NUMERIC(14,4);
ALTER TABLE linea_venta ADD COLUMN IF NOT EXISTS porcentaje_descuento NUMERIC(5,2) NOT NULL DEFAULT 0
    CHECK (porcentaje_descuento >= 0 AND porcentaje_descuento <= 100);
ALTER TABLE linea_venta ADD COLUMN IF NOT EXISTS porcentaje_iva NUMERIC(5,2) NOT NULL DEFAULT 0
    CHECK (porcentaje_iva >= 0 AND porcentaje_iva <= 100);
ALTER TABLE linea_venta ADD COLUMN IF NOT EXISTS subtotal NUMERIC(16,2) CHECK (subtotal IS NULL OR subtotal >= 0);
ALTER TABLE linea_venta ADD COLUMN IF NOT EXISTS descuento NUMERIC(16,2) NOT NULL DEFAULT 0 CHECK (descuento >= 0);
ALTER TABLE linea_venta ADD COLUMN IF NOT EXISTS impuesto NUMERIC(16,2) NOT NULL DEFAULT 0 CHECK (impuesto >= 0);
ALTER TABLE linea_venta ADD COLUMN IF NOT EXISTS total NUMERIC(16,2) CHECK (total IS NULL OR total >= 0);
ALTER TABLE linea_venta ADD COLUMN IF NOT EXISTS garantia_hasta DATE;

-- Totales históricos a partir de los valores persistidos (precio aplicado en su momento, nunca el precio actual).
UPDATE linea_venta SET subtotal = ROUND(cantidad * precio_aplicado, 2) WHERE subtotal IS NULL;
UPDATE linea_venta SET total = subtotal WHERE total IS NULL;
UPDATE venta v SET subtotal = x.subtotal
FROM (SELECT id_venta, SUM(subtotal) AS subtotal FROM linea_venta GROUP BY id_venta) x
WHERE x.id_venta = v.id_venta AND v.subtotal IS NULL;
UPDATE venta v SET total_descuentos = COALESCE(f.descuentos, 0), total_impuestos = COALESCE(f.impuestos, 0)
FROM factura f WHERE f.id_venta = v.id_venta AND v.total_descuentos IS NULL;
UPDATE venta SET total_descuentos = 0 WHERE total_descuentos IS NULL;
UPDATE venta SET total_impuestos = 0 WHERE total_impuestos IS NULL;
UPDATE venta SET subtotal = 0 WHERE subtotal IS NULL;
UPDATE venta SET total = GREATEST(subtotal - total_descuentos + total_impuestos, 0) WHERE total IS NULL;

-- Asignación de lotes consumidos por cada línea (para devolver al lote correcto).
CREATE TABLE IF NOT EXISTS linea_venta_lote (
    id_linea_venta INTEGER NOT NULL REFERENCES linea_venta(id_linea_venta),
    id_lote        INTEGER NOT NULL REFERENCES lote(id_lote),
    cantidad       NUMERIC(14,4) NOT NULL CHECK (cantidad > 0),
    PRIMARY KEY (id_linea_venta, id_lote)
);

-- ---------------------------------------------------------------------
-- 5. Compras y recepción (RF-03, RF-17)
-- ---------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS seq_numero_compra START WITH 1;
ALTER TABLE compra ADD COLUMN IF NOT EXISTS numero VARCHAR(30);
ALTER TABLE compra ADD COLUMN IF NOT EXISTS id_usuario INTEGER REFERENCES usuario(id_usuario);
ALTER TABLE compra ADD COLUMN IF NOT EXISTS observaciones VARCHAR(255);
ALTER TABLE compra ADD COLUMN IF NOT EXISTS id_operacion VARCHAR(64);
ALTER TABLE compra ADD COLUMN IF NOT EXISTS fecha_anulacion TIMESTAMP;
UPDATE compra SET numero = 'OC-' || LPAD(id_compra::text, 6, '0') WHERE numero IS NULL;
-- El consecutivo continúa después del mayor id ya numerado (solo avanza; nunca retrocede al reaplicar).
SELECT setval('seq_numero_compra', x.m)
FROM (SELECT COALESCE(MAX(id_compra), 0) AS m FROM compra) x, seq_numero_compra s
WHERE x.m > 0 AND x.m > CASE WHEN s.is_called THEN s.last_value ELSE 0 END;
CREATE UNIQUE INDEX IF NOT EXISTS uq_compra_numero ON compra (numero);
CREATE UNIQUE INDEX IF NOT EXISTS uq_compra_id_operacion ON compra (id_operacion) WHERE id_operacion IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_compra_proveedor ON compra (id_proveedor);

ALTER TABLE linea_compra ADD COLUMN IF NOT EXISTS unidad VARCHAR(20);
ALTER TABLE linea_compra ADD COLUMN IF NOT EXISTS cantidad_presentacion NUMERIC(14,4);
ALTER TABLE linea_compra ADD COLUMN IF NOT EXISTS costo_presentacion NUMERIC(14,4);
ALTER TABLE linea_compra ADD COLUMN IF NOT EXISTS porcentaje_iva NUMERIC(5,2) NOT NULL DEFAULT 0
    CHECK (porcentaje_iva >= 0 AND porcentaje_iva <= 100);
ALTER TABLE linea_compra ADD COLUMN IF NOT EXISTS porcentaje_descuento NUMERIC(5,2) NOT NULL DEFAULT 0
    CHECK (porcentaje_descuento >= 0 AND porcentaje_descuento <= 100);

-- Documento de ingreso: consecutivo propio (RF-17). Inicia en 1000 para no chocar con ING-000001 del seed.
CREATE SEQUENCE IF NOT EXISTS seq_documento_ingreso START WITH 1000;
ALTER TABLE documento_ingreso ADD COLUMN IF NOT EXISTS id_compra INTEGER REFERENCES compra(id_compra);
ALTER TABLE documento_ingreso ADD COLUMN IF NOT EXISTS id_usuario INTEGER REFERENCES usuario(id_usuario);
ALTER TABLE documento_ingreso ADD COLUMN IF NOT EXISTS observaciones VARCHAR(255);
ALTER TABLE documento_ingreso ADD COLUMN IF NOT EXISTS id_operacion VARCHAR(64);
ALTER TABLE documento_ingreso ADD COLUMN IF NOT EXISTS fecha_anulacion TIMESTAMP;
ALTER TABLE documento_ingreso ADD COLUMN IF NOT EXISTS id_usuario_anulacion INTEGER REFERENCES usuario(id_usuario);
CREATE UNIQUE INDEX IF NOT EXISTS uq_documento_ingreso_operacion ON documento_ingreso (id_operacion) WHERE id_operacion IS NOT NULL;
UPDATE documento_ingreso d SET id_compra = x.id_compra
FROM (SELECT DISTINCT ON (r.id_documento_ingreso) r.id_documento_ingreso, lc.id_compra
      FROM recepcion r JOIN linea_compra lc ON lc.id_linea_compra = r.id_linea_compra
      ORDER BY r.id_documento_ingreso, r.id_recepcion) x
WHERE x.id_documento_ingreso = d.id_documento_ingreso AND d.id_compra IS NULL;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_documento_ingreso_anulacion') THEN
        ALTER TABLE documento_ingreso ADD CONSTRAINT chk_documento_ingreso_anulacion
            CHECK (estado <> 'anulado' OR (motivo_anulacion IS NOT NULL AND length(trim(motivo_anulacion)) > 0)); -- RN-09
    END IF;
END $$;

ALTER TABLE recepcion ADD COLUMN IF NOT EXISTS id_lote INTEGER REFERENCES lote(id_lote);
ALTER TABLE recepcion ADD COLUMN IF NOT EXISTS costo_unitario NUMERIC(14,4) CHECK (costo_unitario IS NULL OR costo_unitario >= 0);
ALTER TABLE recepcion ADD COLUMN IF NOT EXISTS id_movimiento INTEGER REFERENCES movimiento_inventario(id_movimiento);
CREATE INDEX IF NOT EXISTS idx_recepcion_documento ON recepcion (id_documento_ingreso);

-- ---------------------------------------------------------------------
-- 6. Precios (RF-07, RF-16): quién propone, cómo se calculó y decisión
-- ---------------------------------------------------------------------
ALTER TABLE precio ADD COLUMN IF NOT EXISTS usuario_proponente INTEGER REFERENCES usuario(id_usuario);
ALTER TABLE precio ADD COLUMN IF NOT EXISTS modo_calculo VARCHAR(10)
    CHECK (modo_calculo IS NULL OR modo_calculo IN ('recargo', 'margen', 'manual'));
ALTER TABLE precio ADD COLUMN IF NOT EXISTS motivo VARCHAR(255);
ALTER TABLE precio ADD COLUMN IF NOT EXISTS motivo_rechazo VARCHAR(255);
ALTER TABLE precio ADD COLUMN IF NOT EXISTS fecha_decision TIMESTAMP;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_precio_aprobado_con_aprobador') THEN
        -- Los aprobados históricos sin aprobador quedan cubiertos con NOT VALID (no se reescribe historia).
        ALTER TABLE precio ADD CONSTRAINT chk_precio_aprobado_con_aprobador
            CHECK (estado_aprobacion <> 'aprobado' OR usuario_aprobador IS NOT NULL) NOT VALID;
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- 7. Reservas (RF-05, RN-03)
-- ---------------------------------------------------------------------
ALTER TABLE reserva ADD COLUMN IF NOT EXISTS id_usuario INTEGER REFERENCES usuario(id_usuario);
ALTER TABLE reserva ADD COLUMN IF NOT EXISTS fecha_creacion TIMESTAMP NOT NULL DEFAULT now();
ALTER TABLE reserva ADD COLUMN IF NOT EXISTS fecha_liberacion TIMESTAMP;
ALTER TABLE reserva ADD COLUMN IF NOT EXISTS id_venta INTEGER REFERENCES venta(id_venta);
ALTER TABLE reserva ADD COLUMN IF NOT EXISTS motivo_liberacion VARCHAR(255);

-- ---------------------------------------------------------------------
-- 8. Devoluciones (RF-13): documento propio vinculado al original; la factura
--    original nunca se modifica.
-- ---------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS seq_devolucion START WITH 1;
CREATE TABLE IF NOT EXISTS documento_devolucion (
    id_documento_devolucion SERIAL PRIMARY KEY,
    numero        VARCHAR(30) UNIQUE NOT NULL,
    tipo          tipo_devolucion_enum NOT NULL,
    id_venta      INTEGER REFERENCES venta(id_venta),
    id_compra     INTEGER REFERENCES compra(id_compra),
    fecha         TIMESTAMP NOT NULL DEFAULT now(),
    id_usuario    INTEGER NOT NULL REFERENCES usuario(id_usuario),
    motivo        VARCHAR(255) NOT NULL,
    tratamiento   VARCHAR(25) NOT NULL
        CHECK (tratamiento IN ('reembolso', 'saldo_favor', 'cambio', 'nota_credito_proveedor', 'ninguno')),
    total         NUMERIC(16,2) NOT NULL DEFAULT 0 CHECK (total >= 0),
    estado        estado_documento_enum NOT NULL DEFAULT 'activo',
    observaciones VARCHAR(255),
    id_operacion  VARCHAR(64),
    CONSTRAINT chk_devolucion_origen CHECK (
        (tipo = 'cliente' AND id_venta IS NOT NULL AND id_compra IS NULL)
        OR (tipo = 'proveedor' AND id_compra IS NOT NULL AND id_venta IS NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_devolucion_operacion ON documento_devolucion (id_operacion) WHERE id_operacion IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_documento_devolucion_venta ON documento_devolucion (id_venta);

ALTER TABLE devolucion ADD COLUMN IF NOT EXISTS id_documento_devolucion INTEGER REFERENCES documento_devolucion(id_documento_devolucion);
ALTER TABLE devolucion ADD COLUMN IF NOT EXISTS id_linea_venta INTEGER REFERENCES linea_venta(id_linea_venta);
ALTER TABLE devolucion ADD COLUMN IF NOT EXISTS id_linea_compra INTEGER REFERENCES linea_compra(id_linea_compra);
ALTER TABLE devolucion ADD COLUMN IF NOT EXISTS id_lote INTEGER REFERENCES lote(id_lote);
-- Estado del producto devuelto por el cliente: solo 'apto' vuelve a disponible.
ALTER TABLE devolucion ADD COLUMN IF NOT EXISTS estado_producto VARCHAR(10)
    CHECK (estado_producto IS NULL OR estado_producto IN ('apto', 'danado', 'vencido'));
-- Saldo de origen en devoluciones a proveedor.
ALTER TABLE devolucion ADD COLUMN IF NOT EXISTS origen_estado VARCHAR(12)
    CHECK (origen_estado IS NULL OR origen_estado IN ('disponible', 'danado'));
ALTER TABLE devolucion ADD COLUMN IF NOT EXISTS valor NUMERIC(16,2) CHECK (valor IS NULL OR valor >= 0);
CREATE INDEX IF NOT EXISTS idx_devolucion_linea_venta ON devolucion (id_linea_venta);
CREATE INDEX IF NOT EXISTS idx_devolucion_linea_compra ON devolucion (id_linea_compra);

-- ---------------------------------------------------------------------
-- 9. Ajustes de inventario (RF-14, RN-08, RN-16): solicitud → aprobación → movimiento
-- ---------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS seq_ajuste START WITH 1;
CREATE TABLE IF NOT EXISTS ajuste_inventario (
    id_ajuste              SERIAL PRIMARY KEY,
    numero                 VARCHAR(30) UNIQUE NOT NULL,
    id_producto            INTEGER NOT NULL REFERENCES producto(id_producto),
    id_lote                INTEGER REFERENCES lote(id_lote),
    tipo                   VARCHAR(8) NOT NULL CHECK (tipo IN ('entrada', 'salida')),
    cantidad               NUMERIC(14,4) NOT NULL CHECK (cantidad > 0),
    motivo                 VARCHAR(255) NOT NULL CHECK (length(trim(motivo)) > 0),
    observaciones          VARCHAR(500),
    estado                 VARCHAR(10) NOT NULL DEFAULT 'pendiente'
        CHECK (estado IN ('pendiente', 'aprobado', 'rechazado')),
    id_usuario_solicita    INTEGER NOT NULL REFERENCES usuario(id_usuario),
    fecha_solicitud        TIMESTAMP NOT NULL DEFAULT now(),
    id_usuario_decide      INTEGER REFERENCES usuario(id_usuario),
    fecha_decision         TIMESTAMP,
    motivo_rechazo         VARCHAR(255),
    saldo_sistema          NUMERIC(14,4),
    id_movimiento          INTEGER REFERENCES movimiento_inventario(id_movimiento),
    id_movimiento_original INTEGER REFERENCES movimiento_inventario(id_movimiento),
    id_conteo              INTEGER REFERENCES conteo_fisico(id_conteo),
    id_operacion           VARCHAR(64),
    CONSTRAINT chk_ajuste_aprobado_con_movimiento CHECK (estado <> 'aprobado' OR id_movimiento IS NOT NULL),
    CONSTRAINT chk_ajuste_decision CHECK (estado = 'pendiente' OR (id_usuario_decide IS NOT NULL AND fecha_decision IS NOT NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_ajuste_operacion ON ajuste_inventario (id_operacion) WHERE id_operacion IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ajuste_estado ON ajuste_inventario (estado);

-- ---------------------------------------------------------------------
-- 10. Conteos físicos por sesión (RF-15): el conteo nunca ajusta solo
-- ---------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS seq_conteo START WITH 1;
CREATE TABLE IF NOT EXISTS sesion_conteo (
    id_sesion          SERIAL PRIMARY KEY,
    numero             VARCHAR(30) UNIQUE NOT NULL,
    estado             VARCHAR(12) NOT NULL DEFAULT 'abierta'
        CHECK (estado IN ('abierta', 'cerrada', 'aprobada', 'rechazada')),
    observaciones      VARCHAR(500),
    id_usuario         INTEGER NOT NULL REFERENCES usuario(id_usuario),
    fecha_inicio       TIMESTAMP NOT NULL DEFAULT now(),
    fecha_cierre       TIMESTAMP,
    id_usuario_decide  INTEGER REFERENCES usuario(id_usuario),
    fecha_decision     TIMESTAMP,
    motivo_rechazo     VARCHAR(255)
);
ALTER TABLE conteo_fisico ADD COLUMN IF NOT EXISTS id_sesion INTEGER REFERENCES sesion_conteo(id_sesion);
ALTER TABLE conteo_fisico ADD COLUMN IF NOT EXISTS cantidad_sistema NUMERIC(14,4);
ALTER TABLE conteo_fisico ADD COLUMN IF NOT EXISTS observacion VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_conteo_sesion ON conteo_fisico (id_sesion);
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_conteo_sesion_producto') THEN
        ALTER TABLE conteo_fisico ADD CONSTRAINT uq_conteo_sesion_producto UNIQUE (id_sesion, id_producto);
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- 11. Cierres mensuales (RF-08): constancia con totales del período y reapertura auditada
-- ---------------------------------------------------------------------
ALTER TABLE cierre_periodo ADD COLUMN IF NOT EXISTS total_ventas NUMERIC(16,2);
ALTER TABLE cierre_periodo ADD COLUMN IF NOT EXISTS numero_ventas INTEGER;
ALTER TABLE cierre_periodo ADD COLUMN IF NOT EXISTS total_devoluciones NUMERIC(16,2);
ALTER TABLE cierre_periodo ADD COLUMN IF NOT EXISTS total_compras_recibidas NUMERIC(16,2);
ALTER TABLE cierre_periodo ADD COLUMN IF NOT EXISTS numero_movimientos INTEGER;
ALTER TABLE cierre_periodo ADD COLUMN IF NOT EXISTS observaciones VARCHAR(500);
ALTER TABLE cierre_periodo ADD COLUMN IF NOT EXISTS resumen JSONB;
ALTER TABLE cierre_periodo ADD COLUMN IF NOT EXISTS fecha_reapertura TIMESTAMP;
ALTER TABLE cierre_periodo ADD COLUMN IF NOT EXISTS id_usuario_reapertura INTEGER REFERENCES usuario(id_usuario);
ALTER TABLE cierre_periodo ADD COLUMN IF NOT EXISTS motivo_reapertura VARCHAR(255);
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_cierre_estado') THEN
        ALTER TABLE cierre_periodo ADD CONSTRAINT chk_cierre_estado CHECK (estado IN ('cerrado', 'reabierto'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_cierre_primer_dia') THEN
        ALTER TABLE cierre_periodo ADD CONSTRAINT chk_cierre_primer_dia CHECK (EXTRACT(DAY FROM periodo) = 1);
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- 12. Auditoría general de eventos (RF-08, RF-20, RNF-06). Inmutable.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS evento_auditoria (
    id_evento      BIGSERIAL PRIMARY KEY,
    fecha_hora     TIMESTAMP NOT NULL DEFAULT now(),
    id_usuario     INTEGER REFERENCES usuario(id_usuario),
    correo_usuario VARCHAR(150),
    ip             VARCHAR(45),
    modulo         VARCHAR(30) NOT NULL,
    accion         VARCHAR(40) NOT NULL,
    entidad        VARCHAR(40) NOT NULL,
    id_entidad     VARCHAR(60),
    descripcion    VARCHAR(500) NOT NULL,
    detalle        JSONB,
    id_operacion   VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_evento_fecha ON evento_auditoria (fecha_hora DESC);
CREATE INDEX IF NOT EXISTS idx_evento_modulo_accion ON evento_auditoria (modulo, accion);
CREATE INDEX IF NOT EXISTS idx_evento_entidad ON evento_auditoria (entidad, id_entidad);
CREATE INDEX IF NOT EXISTS idx_evento_usuario ON evento_auditoria (id_usuario);

CREATE OR REPLACE FUNCTION fn_bloquear_edicion_auditoria()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'RF-08: los eventos de auditoría no se modifican ni se eliminan.';
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_bloquear_edicion_evento ON evento_auditoria;
CREATE TRIGGER trg_bloquear_edicion_evento
    BEFORE UPDATE OR DELETE ON evento_auditoria
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_edicion_auditoria();

-- ---------------------------------------------------------------------
-- 13. Importaciones de Excel (RF-10, RN-15): validación previa, revisión y
--     consolidación; el hash evita reprocesar el mismo archivo.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS importacion (
    id_importacion  SERIAL PRIMARY KEY,
    tipo            VARCHAR(20) NOT NULL CHECK (tipo IN ('productos', 'terceros')),
    nombre_archivo  VARCHAR(255) NOT NULL,
    hash_archivo    CHAR(64) NOT NULL,
    estado          VARCHAR(12) NOT NULL DEFAULT 'validada'
        CHECK (estado IN ('validada', 'importada', 'cancelada')),
    total_filas     INTEGER NOT NULL DEFAULT 0,
    filas_validas   INTEGER NOT NULL DEFAULT 0,
    filas_error     INTEGER NOT NULL DEFAULT 0,
    filas_duplicadas INTEGER NOT NULL DEFAULT 0,
    filas_importadas INTEGER,
    filas           JSONB NOT NULL,
    id_usuario      INTEGER NOT NULL REFERENCES usuario(id_usuario),
    fecha_carga     TIMESTAMP NOT NULL DEFAULT now(),
    fecha_proceso   TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_importacion_hash_importada
    ON importacion (tipo, hash_archivo) WHERE estado = 'importada';

-- ---------------------------------------------------------------------
-- 14. RN-09: los documentos nuevos tampoco se eliminan
-- ---------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_bloquear_borrado_devolucion ON documento_devolucion;
CREATE TRIGGER trg_bloquear_borrado_devolucion BEFORE DELETE ON documento_devolucion
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_borrado();
DROP TRIGGER IF EXISTS trg_bloquear_borrado_ajuste ON ajuste_inventario;
CREATE TRIGGER trg_bloquear_borrado_ajuste BEFORE DELETE ON ajuste_inventario
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_borrado();
DROP TRIGGER IF EXISTS trg_bloquear_borrado_sesion_conteo ON sesion_conteo;
CREATE TRIGGER trg_bloquear_borrado_sesion_conteo BEFORE DELETE ON sesion_conteo
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_borrado();
DROP TRIGGER IF EXISTS trg_bloquear_borrado_cierre ON cierre_periodo;
CREATE TRIGGER trg_bloquear_borrado_cierre BEFORE DELETE ON cierre_periodo
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_borrado();
