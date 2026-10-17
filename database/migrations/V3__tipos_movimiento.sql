-- ====================================================================
-- Nombre: V3__tipos_movimiento.sql
-- Descripción: Migración Flyway V3: nuevos tipos de movimiento (reserva, cambio de estado y baja por
--              vencimiento) para auditar todas las transiciones de RF-05/RF-08. Va sola en su archivo
--              porque ALTER TYPE ... ADD VALUE no puede mezclarse con sentencias que usen el valor nuevo.
--              Idempotente (IF NOT EXISTS).
-- Fecha de creación: 2026-10-16
-- Fecha de actualización: 2026-10-16
-- Autor: Equipo Ferretería
-- Versión: 1.0.0
-- ====================================================================
ALTER TYPE tipo_movimiento_enum ADD VALUE IF NOT EXISTS 'reserva';
ALTER TYPE tipo_movimiento_enum ADD VALUE IF NOT EXISTS 'cambio_estado';
ALTER TYPE tipo_movimiento_enum ADD VALUE IF NOT EXISTS 'baja_vencimiento';
