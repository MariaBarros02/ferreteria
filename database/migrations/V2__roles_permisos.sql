-- ====================================================================
-- Nombre: V2__roles_permisos.sql
-- Descripción: Migración Flyway V2: roles y matriz de permisos por rol (RF-09). Idempotente.
-- Fecha de creación: 2026-10-07
-- Fecha de actualización: 2026-10-07
-- Autor: Equipo Ferretería
-- Versión: 1.0.0
-- ====================================================================
INSERT INTO rol (nombre) VALUES ('Ventas'), ('Compras'), ('Inventario'), ('Administrador'), ('Gerencia')
ON CONFLICT (nombre) DO NOTHING;

-- Matriz RF-09: módulo x nivel de acceso por rol
INSERT INTO permiso (id_rol, modulo, nivel_acceso)
SELECT r.id_rol, m.modulo, m.nivel
FROM rol r
JOIN (VALUES
  ('Ventas','Compras','ver'),('Ventas','Ventas','si'),('Ventas','Inventario','consultar'),('Ventas','Precios','no'),('Ventas','Usuarios','no'),('Ventas','Auditoria','no'),('Ventas','Cierres','no'),
  ('Compras','Compras','si'),('Compras','Ventas','no'),('Compras','Inventario','consultar'),('Compras','Precios','no'),('Compras','Usuarios','no'),('Compras','Auditoria','no'),('Compras','Cierres','no'),
  ('Inventario','Compras','si'),('Inventario','Ventas','consultar'),('Inventario','Inventario','si'),('Inventario','Precios','proponer'),('Inventario','Usuarios','no'),('Inventario','Auditoria','consultar'),('Inventario','Cierres','no'),
  ('Administrador','Compras','si'),('Administrador','Ventas','si'),('Administrador','Inventario','si'),('Administrador','Precios','segun_autorizacion'),('Administrador','Usuarios','si'),('Administrador','Auditoria','si'),('Administrador','Cierres','no'),
  ('Gerencia','Compras','consultar'),('Gerencia','Ventas','consultar'),('Gerencia','Inventario','consultar'),('Gerencia','Precios','aprobar'),('Gerencia','Usuarios','no'),('Gerencia','Auditoria','si'),('Gerencia','Cierres','si')
) AS m(rol, modulo, nivel) ON m.rol = r.nombre
ON CONFLICT (id_rol, modulo) DO NOTHING;
