-- ====================================================================
-- Nombre: V5__monedas.sql
-- Descripción: Migración Flyway V5: monedas (COP funcional, USD, EUR y otras), tasas de cambio con
--              vigencia e inmutables, y moneda/tasa de ventas y compras (RF-23, RN-17).
--              Compatible con datos existentes: todas las ventas y compras previas quedan en COP con
--              tasa 1 y sus importes en moneda del documento iguales a los importes en COP.
--              El inventario, los costos, los cierres y la auditoría siguen expresados en COP.
--              Idempotente (IF NOT EXISTS / ON CONFLICT / verificaciones en pg_constraint).
-- Fecha de creación: 2026-10-16
-- Fecha de actualización: 2026-10-16
-- Autor: Equipo Ferretería
-- Versión: 1.0.0
-- ====================================================================

-- ---------------------------------------------------------------------
-- 1. Catálogo de monedas (ISO 4217). Una sola moneda funcional (COP).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS moneda (
    codigo     CHAR(3)     PRIMARY KEY CHECK (codigo ~ '^[A-Z]{3}$'),
    nombre     VARCHAR(60) NOT NULL,
    simbolo    VARCHAR(5)  NOT NULL,
    decimales  SMALLINT    NOT NULL DEFAULT 2 CHECK (decimales BETWEEN 0 AND 4),
    activa     BOOLEAN     NOT NULL DEFAULT TRUE,
    funcional  BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_moneda_funcional_activa CHECK (NOT funcional OR activa)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_moneda_funcional ON moneda (funcional) WHERE funcional;

INSERT INTO moneda (codigo, nombre, simbolo, decimales, activa, funcional) VALUES
    ('COP', 'Peso colombiano',       '$',   0, TRUE, TRUE),
    ('USD', 'Dólar estadounidense',  'US$', 2, TRUE, FALSE),
    ('EUR', 'Euro',                  '€',   2, TRUE, FALSE),
    ('MXN', 'Peso mexicano',         'MX$', 2, TRUE, FALSE),
    ('PEN', 'Sol peruano',           'S/',  2, TRUE, FALSE),
    ('BRL', 'Real brasileño',        'R$',  2, TRUE, FALSE)
ON CONFLICT (codigo) DO NOTHING;

-- ---------------------------------------------------------------------
-- 2. Tasas de cambio: pesos colombianos por 1 unidad de la moneda.
--    No se cargan tasas de ejemplo: las registra un usuario autorizado con su fuente
--    (p. ej. TRM del Banco de la República). Inmutables: una corrección es una tasa nueva.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tasa_cambio (
    id_tasa         BIGSERIAL     PRIMARY KEY,
    codigo_moneda   CHAR(3)       NOT NULL REFERENCES moneda (codigo),
    tasa            NUMERIC(18,6) NOT NULL CHECK (tasa > 0),
    fecha_vigencia  DATE          NOT NULL,
    fuente          VARCHAR(100),
    id_usuario      INT           REFERENCES usuario (id_usuario),
    fecha_registro  TIMESTAMP     NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')
);
CREATE INDEX IF NOT EXISTS ix_tasa_cambio_vigencia ON tasa_cambio (codigo_moneda, fecha_vigencia DESC, id_tasa DESC);

CREATE OR REPLACE FUNCTION fn_bloquear_edicion_tasa()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'RF-23: las tasas de cambio no se modifican ni se eliminan; registre una tasa nueva.';
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_bloquear_edicion_tasa ON tasa_cambio;
CREATE TRIGGER trg_bloquear_edicion_tasa
    BEFORE UPDATE OR DELETE ON tasa_cambio
    FOR EACH ROW EXECUTE FUNCTION fn_bloquear_edicion_tasa();

-- La moneda funcional no lleva tasa (siempre vale 1).
CREATE OR REPLACE FUNCTION fn_tasa_moneda_no_funcional()
RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM moneda WHERE codigo = NEW.codigo_moneda AND funcional) THEN
        RAISE EXCEPTION 'RF-23: la moneda funcional % no tiene tasa de cambio.', NEW.codigo_moneda;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS trg_tasa_moneda_no_funcional ON tasa_cambio;
CREATE TRIGGER trg_tasa_moneda_no_funcional
    BEFORE INSERT ON tasa_cambio
    FOR EACH ROW EXECUTE FUNCTION fn_tasa_moneda_no_funcional();

-- ---------------------------------------------------------------------
-- 3. Ventas: moneda del documento, tasa aplicada e importes en esa moneda.
--    Las columnas existentes (subtotal, total, …) siguen en COP.
-- ---------------------------------------------------------------------
ALTER TABLE venta ADD COLUMN IF NOT EXISTS moneda CHAR(3) NOT NULL DEFAULT 'COP';
ALTER TABLE venta ADD COLUMN IF NOT EXISTS tasa_cambio NUMERIC(18,6) NOT NULL DEFAULT 1;
ALTER TABLE venta ADD COLUMN IF NOT EXISTS subtotal_moneda NUMERIC(16,2);
ALTER TABLE venta ADD COLUMN IF NOT EXISTS descuentos_moneda NUMERIC(16,2);
ALTER TABLE venta ADD COLUMN IF NOT EXISTS impuestos_moneda NUMERIC(16,2);
ALTER TABLE venta ADD COLUMN IF NOT EXISTS total_moneda NUMERIC(16,2);
UPDATE venta SET subtotal_moneda = subtotal, descuentos_moneda = total_descuentos,
                 impuestos_moneda = total_impuestos, total_moneda = total
WHERE total_moneda IS NULL AND moneda = 'COP';

ALTER TABLE linea_venta ADD COLUMN IF NOT EXISTS precio_moneda NUMERIC(14,4);
ALTER TABLE linea_venta ADD COLUMN IF NOT EXISTS subtotal_moneda NUMERIC(16,2);
ALTER TABLE linea_venta ADD COLUMN IF NOT EXISTS descuento_moneda NUMERIC(16,2);
ALTER TABLE linea_venta ADD COLUMN IF NOT EXISTS impuesto_moneda NUMERIC(16,2);
ALTER TABLE linea_venta ADD COLUMN IF NOT EXISTS total_moneda NUMERIC(16,2);
UPDATE linea_venta lv SET precio_moneda = lv.precio_aplicado, subtotal_moneda = lv.subtotal,
                          descuento_moneda = lv.descuento, impuesto_moneda = lv.impuesto, total_moneda = lv.total
FROM venta v
WHERE v.id_venta = lv.id_venta AND v.moneda = 'COP' AND lv.total_moneda IS NULL;

-- ---------------------------------------------------------------------
-- 4. Compras: moneda del proveedor y tasa pactada en la orden. El costo que
--    alimenta el costo promedio (costo_pactado) sigue en COP por unidad base.
-- ---------------------------------------------------------------------
ALTER TABLE compra ADD COLUMN IF NOT EXISTS moneda CHAR(3) NOT NULL DEFAULT 'COP';
ALTER TABLE compra ADD COLUMN IF NOT EXISTS tasa_cambio NUMERIC(18,6) NOT NULL DEFAULT 1;
ALTER TABLE linea_compra ADD COLUMN IF NOT EXISTS costo_moneda NUMERIC(14,4);
UPDATE linea_compra lc SET costo_moneda = lc.costo_pactado
FROM compra c
WHERE c.id_compra = lc.id_compra AND c.moneda = 'COP' AND lc.costo_moneda IS NULL;

-- ---------------------------------------------------------------------
-- 5. Restricciones (agregadas solo si no existen)
-- ---------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_venta_moneda') THEN
        ALTER TABLE venta ADD CONSTRAINT fk_venta_moneda FOREIGN KEY (moneda) REFERENCES moneda (codigo);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_venta_tasa_positiva') THEN
        ALTER TABLE venta ADD CONSTRAINT chk_venta_tasa_positiva CHECK (tasa_cambio > 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_venta_cop_tasa_uno') THEN
        ALTER TABLE venta ADD CONSTRAINT chk_venta_cop_tasa_uno CHECK (moneda <> 'COP' OR tasa_cambio = 1);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_compra_moneda') THEN
        ALTER TABLE compra ADD CONSTRAINT fk_compra_moneda FOREIGN KEY (moneda) REFERENCES moneda (codigo);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_compra_tasa_positiva') THEN
        ALTER TABLE compra ADD CONSTRAINT chk_compra_tasa_positiva CHECK (tasa_cambio > 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_compra_cop_tasa_uno') THEN
        ALTER TABLE compra ADD CONSTRAINT chk_compra_cop_tasa_uno CHECK (moneda <> 'COP' OR tasa_cambio = 1);
    END IF;
END $$;

-- Permiso del nuevo módulo en la matriz de roles (RF-09): lectura para todos, gestión para Gerencia y Administrador.
INSERT INTO permiso (id_rol, modulo, nivel_acceso)
SELECT r.id_rol, 'Monedas', CASE WHEN r.nombre IN ('Gerencia', 'Administrador') THEN 'si' ELSE 'consultar' END
FROM rol r
ON CONFLICT (id_rol, modulo) DO NOTHING;
