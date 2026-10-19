-- ====================================================================
-- Nombre: V1__ddl.sql
-- Descripción: Migración Flyway V1: esquema completo (mismo contenido que 01_ddl.sql, sin control de transacción).
--              Idempotente: tipos, tablas, índices, secuencias y disparadores se crean solo si no existen.
-- Fecha de creación: 2026-10-07
-- Fecha de actualización: 2026-10-19
-- Autor: Equipo Ferretería
-- Versión: 1.1.0
-- ====================================================================
-- =====================================================================
-- 01_ddl.sql — Creación de tipos, tablas, restricciones e índices
-- Sistema de inventario de ferretería
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. Tipos enumerados (representan los estados y catálogos cerrados
--    definidos en las reglas de negocio). PostgreSQL no admite
--    CREATE TYPE IF NOT EXISTS: se verifica en pg_type.
-- ---------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'tipo_persona_enum') THEN
        CREATE TYPE tipo_persona_enum AS ENUM ('natural', 'juridica');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'tipo_identificacion_enum') THEN
        CREATE TYPE tipo_identificacion_enum AS ENUM ('CC', 'NIT', 'CE', 'PASAPORTE');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'tipo_rastreo_enum') THEN
        CREATE TYPE tipo_rastreo_enum AS ENUM ('ninguno', 'lote', 'serie');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'estado_documento_enum') THEN
        CREATE TYPE estado_documento_enum AS ENUM ('activo', 'anulado');                       -- RN-09
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'estado_reserva_enum') THEN
        CREATE TYPE estado_reserva_enum AS ENUM ('activa', 'liberada_venta',
                                                 'liberada_cancelacion',
                                                 'liberada_vencimiento');                     -- RN-03
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'modalidad_factura_enum') THEN
        CREATE TYPE modalidad_factura_enum AS ENUM ('electronica', 'impresa');                 -- RN-14
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'estado_aprobacion_enum') THEN
        CREATE TYPE estado_aprobacion_enum AS ENUM ('propuesto', 'aprobado', 'rechazado');     -- RN-07
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'tipo_movimiento_enum') THEN
        CREATE TYPE tipo_movimiento_enum AS ENUM ('compra', 'venta',
                                                  'devolucion_cliente',
                                                  'devolucion_proveedor',
                                                  'ajuste', 'conteo');                         -- RN-08
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'tipo_devolucion_enum') THEN
        CREATE TYPE tipo_devolucion_enum AS ENUM ('cliente', 'proveedor');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'estado_conteo_enum') THEN
        CREATE TYPE estado_conteo_enum AS ENUM ('pendiente', 'aprobado', 'rechazado');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'estado_cuenta_enum') THEN
        CREATE TYPE estado_cuenta_enum AS ENUM ('activo', 'inactivo');
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- 2. Seguridad: roles, permisos y usuarios (RF-09)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rol (
    id_rol      SERIAL PRIMARY KEY,
    nombre      VARCHAR(50) UNIQUE NOT NULL
);

CREATE TABLE IF NOT EXISTS permiso (
    id_permiso   SERIAL PRIMARY KEY,
    id_rol       INTEGER NOT NULL REFERENCES rol(id_rol),
    modulo       VARCHAR(50) NOT NULL,   -- Compras, Ventas, Inventario, Precios, Usuarios, Auditoria, Cierres
    nivel_acceso VARCHAR(20) NOT NULL,   -- no, ver, consultar, si, proponer, aprobar
    UNIQUE (id_rol, modulo)
);

CREATE TABLE IF NOT EXISTS usuario (
    id_usuario     SERIAL PRIMARY KEY,
    nombre         VARCHAR(150) NOT NULL,
    correo         VARCHAR(150) UNIQUE NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    id_rol         INTEGER NOT NULL REFERENCES rol(id_rol),
    estado         estado_cuenta_enum NOT NULL DEFAULT 'activo'
);

-- ---------------------------------------------------------------------
-- 3. Terceros (RF-06 / RN-06)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tercero (
    id_tercero            SERIAL PRIMARY KEY,
    tipo_persona          tipo_persona_enum NOT NULL,
    tipo_identificacion   tipo_identificacion_enum NOT NULL,
    numero_identificacion VARCHAR(20) UNIQUE NOT NULL,
    primer_nombre         VARCHAR(80),
    segundo_nombre        VARCHAR(80),
    primer_apellido       VARCHAR(80),
    segundo_apellido      VARCHAR(80),
    razon_social          VARCHAR(200),
    direccion             VARCHAR(200) NOT NULL,
    ciudad                VARCHAR(80)  NOT NULL,
    telefono              VARCHAR(30)  NOT NULL,
    correo                VARCHAR(150) NOT NULL,
    es_cliente            BOOLEAN NOT NULL DEFAULT FALSE,
    es_proveedor          BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_tercero_rol
        CHECK (es_cliente OR es_proveedor),
    -- RN-06: persona natural exige nombre/apellido; jurídica exige razón social
    CONSTRAINT chk_tercero_natural
        CHECK (tipo_persona <> 'natural'
               OR (primer_nombre IS NOT NULL AND primer_apellido IS NOT NULL)),
    CONSTRAINT chk_tercero_juridica
        CHECK (tipo_persona <> 'juridica' OR razon_social IS NOT NULL)
);

-- ---------------------------------------------------------------------
-- 4. Productos, unidades e inventario (RF-01, RF-02, RN-01, RN-02, RN-03)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS producto (
    id_producto      SERIAL PRIMARY KEY,
    nombre           VARCHAR(150) NOT NULL,
    marca            VARCHAR(100) NOT NULL,
    presentacion     VARCHAR(100) NOT NULL,
    codigo_barras    VARCHAR(50)  UNIQUE NOT NULL,      -- RN-01
    unidad_base      VARCHAR(20)  NOT NULL,
    tipo_rastreo     tipo_rastreo_enum NOT NULL DEFAULT 'ninguno', -- RN-05
    tiene_vencimiento BOOLEAN NOT NULL DEFAULT FALSE,
    tiene_garantia   BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (nombre, marca, presentacion)                -- RN-01: referencia única
);

CREATE TABLE IF NOT EXISTS conversion_unidad (
    id_conversion       SERIAL PRIMARY KEY,
    id_producto         INTEGER NOT NULL REFERENCES producto(id_producto),
    unidad_presentacion VARCHAR(20) NOT NULL,
    factor_a_unidad_base NUMERIC(14,4) NOT NULL CHECK (factor_a_unidad_base > 0),
    precision_decimales  INTEGER NOT NULL DEFAULT 0 CHECK (precision_decimales >= 0),
    UNIQUE (id_producto, unidad_presentacion)
);

CREATE TABLE IF NOT EXISTS inventario (
    id_producto INTEGER PRIMARY KEY REFERENCES producto(id_producto),
    disponible  NUMERIC(14,4) NOT NULL DEFAULT 0 CHECK (disponible >= 0),  -- RN-03
    reservado   NUMERIC(14,4) NOT NULL DEFAULT 0 CHECK (reservado  >= 0),  -- RN-03
    danado      NUMERIC(14,4) NOT NULL DEFAULT 0 CHECK (danado     >= 0)   -- RN-03
);

CREATE TABLE IF NOT EXISTS reserva (
    id_reserva   SERIAL PRIMARY KEY,
    id_producto  INTEGER NOT NULL REFERENCES producto(id_producto),
    id_cliente   INTEGER NOT NULL REFERENCES tercero(id_tercero),
    id_pedido    VARCHAR(50) NOT NULL,
    cantidad     NUMERIC(14,4) NOT NULL CHECK (cantidad > 0),
    fecha_limite DATE NOT NULL,
    estado       estado_reserva_enum NOT NULL DEFAULT 'activa'            -- RN-03
);

-- ---------------------------------------------------------------------
-- 5. Compras y recepciones (RF-03, RF-17, RN-09)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS compra (
    id_compra   SERIAL PRIMARY KEY,
    id_proveedor INTEGER NOT NULL REFERENCES tercero(id_tercero),
    fecha        TIMESTAMP NOT NULL DEFAULT now(),
    estado       estado_documento_enum NOT NULL DEFAULT 'activo',
    motivo_anulacion VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS linea_compra (
    id_linea_compra   SERIAL PRIMARY KEY,
    id_compra         INTEGER NOT NULL REFERENCES compra(id_compra),
    id_producto       INTEGER NOT NULL REFERENCES producto(id_producto),
    cantidad_pedida   NUMERIC(14,4) NOT NULL CHECK (cantidad_pedida > 0),
    cantidad_recibida NUMERIC(14,4) NOT NULL DEFAULT 0 CHECK (cantidad_recibida >= 0),
    costo_pactado     NUMERIC(14,4) NOT NULL CHECK (costo_pactado >= 0),
    CONSTRAINT chk_recepcion_no_excede
        CHECK (cantidad_recibida <= cantidad_pedida)                     -- RF-03/RF-17
);

CREATE TABLE IF NOT EXISTS documento_ingreso (
    id_documento_ingreso SERIAL PRIMARY KEY,
    consecutivo          VARCHAR(30) UNIQUE NOT NULL,
    estado                estado_documento_enum NOT NULL DEFAULT 'activo', -- RN-09
    factura_proveedor     VARCHAR(100) NOT NULL,
    motivo_anulacion      VARCHAR(255),
    fecha                 TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS recepcion (
    id_recepcion         SERIAL PRIMARY KEY,
    id_linea_compra      INTEGER NOT NULL REFERENCES linea_compra(id_linea_compra),
    id_documento_ingreso INTEGER NOT NULL REFERENCES documento_ingreso(id_documento_ingreso),
    cantidad_recibida    NUMERIC(14,4) NOT NULL CHECK (cantidad_recibida > 0),
    fecha                TIMESTAMP NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- 6. Ventas y facturación (RF-04, RN-11, RN-14)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS venta (
    id_venta      SERIAL PRIMARY KEY,
    numero_interno VARCHAR(30) UNIQUE NOT NULL,          -- RN-14
    id_cliente    INTEGER NOT NULL REFERENCES tercero(id_tercero),
    fecha         TIMESTAMP NOT NULL DEFAULT now(),
    estado        estado_documento_enum NOT NULL DEFAULT 'activo'
);

CREATE TABLE IF NOT EXISTS linea_venta (
    id_linea_venta  SERIAL PRIMARY KEY,
    id_venta        INTEGER NOT NULL REFERENCES venta(id_venta),
    id_producto     INTEGER NOT NULL REFERENCES producto(id_producto),
    cantidad        NUMERIC(14,4) NOT NULL CHECK (cantidad > 0),
    precio_aplicado NUMERIC(14,4) NOT NULL CHECK (precio_aplicado >= 0)
);

CREATE TABLE IF NOT EXISTS factura (
    id_factura      SERIAL PRIMARY KEY,
    id_venta        INTEGER UNIQUE NOT NULL REFERENCES venta(id_venta),
    numeracion_dian VARCHAR(30) UNIQUE,                   -- RN-14: distinta del numero_interno
    modalidad       modalidad_factura_enum NOT NULL DEFAULT 'electronica',
    impuestos       NUMERIC(14,4) NOT NULL DEFAULT 0 CHECK (impuestos >= 0),
    descuentos      NUMERIC(14,4) NOT NULL DEFAULT 0 CHECK (descuentos >= 0),
    fecha_emision   TIMESTAMP NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- 7. Precios (RF-07, RF-16, RN-07)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS precio (
    id_precio            SERIAL PRIMARY KEY,
    id_producto          INTEGER NOT NULL REFERENCES producto(id_producto),
    costo_promedio       NUMERIC(14,4) NOT NULL CHECK (costo_promedio >= 0),
    porcentaje_incremento NUMERIC(6,4) NOT NULL CHECK (porcentaje_incremento >= 0),
    precio_propuesto     NUMERIC(14,4) NOT NULL CHECK (precio_propuesto >= 0),
    precio_vigente       NUMERIC(14,4) CHECK (precio_vigente >= 0),
    estado_aprobacion    estado_aprobacion_enum NOT NULL DEFAULT 'propuesto',
    usuario_aprobador    INTEGER REFERENCES usuario(id_usuario),
    fecha_propuesta      TIMESTAMP NOT NULL DEFAULT now(),
    fecha_vigencia       TIMESTAMP,
    -- RN-07: no puede haber precio vigente sin aprobación
    CONSTRAINT chk_precio_vigente_requiere_aprobacion
        CHECK (precio_vigente IS NULL OR estado_aprobacion = 'aprobado')
);

-- ---------------------------------------------------------------------
-- 8. Movimientos de inventario y auditoría (RF-08, RN-08, RN-16, RNF-06)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS movimiento_inventario (
    id_movimiento          SERIAL PRIMARY KEY,
    tipo                   tipo_movimiento_enum NOT NULL,
    id_producto            INTEGER NOT NULL REFERENCES producto(id_producto),
    cantidad               NUMERIC(14,4) NOT NULL,          -- con signo: + entrada, - salida
    fecha_hora             TIMESTAMP NOT NULL DEFAULT now(),
    id_usuario             INTEGER NOT NULL REFERENCES usuario(id_usuario),
    ip                     INET,
    documento_relacionado  VARCHAR(100) NOT NULL,
    valor_anterior         NUMERIC(14,4) NOT NULL,
    valor_nuevo            NUMERIC(14,4) NOT NULL,
    motivo                 VARCHAR(255),
    id_movimiento_original INTEGER REFERENCES movimiento_inventario(id_movimiento) -- RN-16
);

CREATE TABLE IF NOT EXISTS devolucion (
    id_devolucion       SERIAL PRIMARY KEY,
    tipo                tipo_devolucion_enum NOT NULL,
    id_documento_origen VARCHAR(50) NOT NULL,
    id_producto         INTEGER NOT NULL REFERENCES producto(id_producto),
    cantidad            NUMERIC(14,4) NOT NULL CHECK (cantidad > 0),
    motivo              VARCHAR(255) NOT NULL,
    id_movimiento       INTEGER NOT NULL REFERENCES movimiento_inventario(id_movimiento)
);

CREATE TABLE IF NOT EXISTS conteo_fisico (
    id_conteo          SERIAL PRIMARY KEY,
    id_producto        INTEGER NOT NULL REFERENCES producto(id_producto),
    cantidad_contada   NUMERIC(14,4) NOT NULL CHECK (cantidad_contada >= 0),
    diferencia         NUMERIC(14,4) NOT NULL,
    fecha              TIMESTAMP NOT NULL DEFAULT now(),
    estado             estado_conteo_enum NOT NULL DEFAULT 'pendiente',
    id_usuario         INTEGER NOT NULL REFERENCES usuario(id_usuario),
    id_movimiento_ajuste INTEGER REFERENCES movimiento_inventario(id_movimiento)
);

CREATE TABLE IF NOT EXISTS cierre_periodo (
    id_cierre    SERIAL PRIMARY KEY,
    periodo      DATE NOT NULL UNIQUE,
    fecha_cierre TIMESTAMP NOT NULL DEFAULT now(),
    id_usuario   INTEGER NOT NULL REFERENCES usuario(id_usuario),
    estado       VARCHAR(20) NOT NULL DEFAULT 'cerrado'
);

-- ---------------------------------------------------------------------
-- 9. Índices de apoyo a las consultas más frecuentes
-- ---------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_movimiento_producto_fecha ON movimiento_inventario (id_producto, fecha_hora);
CREATE INDEX IF NOT EXISTS idx_linea_venta_producto      ON linea_venta (id_producto);
CREATE INDEX IF NOT EXISTS idx_linea_compra_producto     ON linea_compra (id_producto);
CREATE INDEX IF NOT EXISTS idx_reserva_producto_estado   ON reserva (id_producto, estado);
CREATE INDEX IF NOT EXISTS idx_precio_producto_estado    ON precio (id_producto, estado_aprobacion);

-- ---------------------------------------------------------------------
-- 10. Disparadores que hacen cumplir RN-09 (nunca eliminar documentos)
--     y RN-16 (nunca editar un movimiento histórico).
--     Las funciones se reemplazan; cada disparador se borra antes de crearse.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_bloquear_borrado()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        'RN-09: no se permite eliminar registros de %. Use el estado "anulado".',
        TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_bloquear_borrado_compra ON compra;
CREATE TRIGGER trg_bloquear_borrado_compra
    BEFORE DELETE ON compra
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_borrado();

DROP TRIGGER IF EXISTS trg_bloquear_borrado_venta ON venta;
CREATE TRIGGER trg_bloquear_borrado_venta
    BEFORE DELETE ON venta
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_borrado();

DROP TRIGGER IF EXISTS trg_bloquear_borrado_doc_ingreso ON documento_ingreso;
CREATE TRIGGER trg_bloquear_borrado_doc_ingreso
    BEFORE DELETE ON documento_ingreso
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_borrado();

DROP TRIGGER IF EXISTS trg_bloquear_borrado_factura ON factura;
CREATE TRIGGER trg_bloquear_borrado_factura
    BEFORE DELETE ON factura
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_borrado();

CREATE OR REPLACE FUNCTION fn_bloquear_edicion_movimiento()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        'RN-16: un movimiento de inventario no se edita ni se elimina; registre una nota de ajuste.';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_bloquear_update_movimiento ON movimiento_inventario;
CREATE TRIGGER trg_bloquear_update_movimiento
    BEFORE UPDATE OR DELETE ON movimiento_inventario
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_edicion_movimiento();

-- ---------------------------------------------------------------------
-- 11. Secuencias de numeración (RN-14: número interno de venta y numeración
--     fiscal simulada son campos distintos). Inician en 1000 para no chocar
--     con los consecutivos del seed (VTA-000001, VTA-000002).
--     IF NOT EXISTS conserva el valor actual si la secuencia ya existe.
-- ---------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS seq_numero_venta    START WITH 1000 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS seq_numeracion_dian START WITH 1000 INCREMENT BY 1;