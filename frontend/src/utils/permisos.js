/*
 * Nombre: permisos.js
 * Descripción: Matriz de permisos por rol en la interfaz (espejo de RF-09). Solo oculta o deshabilita acciones: la API es la autoridad final.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
const V = 'Ventas'; const C = 'Compras'; const I = 'Inventario'; const A = 'Administrador'; const G = 'Gerencia';

/** Acción → roles autorizados. */
export const PERMISOS = {
  'productos.escribir': [I, A],
  'productos.eliminar': [A],
  'terceros.escribir': [C, V, A],
  'ventas.ver': [V, I, A, G],
  'ventas.registrar': [V, A],
  'compras.ver': [V, C, I, A, G],
  'compras.registrar': [C, I, A],
  'compras.recibir': [C, I, A],
  'compras.anular': [C, A],
  'compras.anularIngreso': [I, A],
  'precios.ver': [I, A, G],
  'precios.proponer': [I, A],
  'precios.aprobar': [G],
  'inventario.costos': [I, A, G],
  'inventario.transicion': [I, A],
  'inventario.kardex': [I, A, G],
  'reservas.crear': [V, A],
  'reservas.ver': [V, I, A, G],
  'reservas.cancelar': [V, I, A],
  'devoluciones.ver': [V, C, I, A, G],
  'devoluciones.cliente': [V, I, A],
  'devoluciones.proveedor': [C, I, A],
  'ajustes.ver': [I, A, G],
  'ajustes.solicitar': [I, A],
  'ajustes.decidir': [G, A],
  'conteos.ver': [I, A, G],
  'conteos.contar': [I, A],
  'conteos.decidir': [G, A],
  'cierres.gestionar': [G],
  'auditoria.ver': [I, A, G],
  'usuarios.gestionar': [A],
  'importacion.productos': [I, A],
  'importacion.terceros': [C, V, A],
  'exportar.terceros': [C, V, A],
  'exportar.movimientos': [I, A, G],
  'monedas.gestionar': [G, A],
};

/**
 * Indica si un rol puede ejecutar una acción.
 * @param {string|null} rol rol del usuario
 * @param {string} accion clave de PERMISOS
 */
export const puede = (rol, accion) => Boolean(rol && PERMISOS[accion]?.includes(rol));

/** Roles con alguna acción de importación. */
export const puedeImportar = (rol) => puede(rol, 'importacion.productos') || puede(rol, 'importacion.terceros');
