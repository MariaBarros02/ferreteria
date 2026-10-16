/*
 * Nombre: lineas.js
 * Descripción: Validación de líneas de documentos (venta, compra) en el cliente: cantidades positivas con máximo 4 decimales, porcentajes 0–100 y precio vigente. El servidor revalida todo.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
const DECIMAL4 = /^\d+(\.\d{1,4})?$/;
const DECIMAL2 = /^\d+(\.\d{1,2})?$/;

/** Clave de error para una cantidad (o null si es válida). */
export function errorCantidad(v) {
  const s = String(v ?? '').trim().replace(',', '.');
  if (!s) return 'errores.obligatorio';
  if (!DECIMAL4.test(s)) return 'errores.decimales';
  if (Number(s) <= 0) return 'errores.positivo';
  return null;
}

/** Clave de error para un porcentaje opcional 0–100 con 2 decimales. */
export function errorPorcentaje(v) {
  const s = String(v ?? '').trim().replace(',', '.');
  if (!s) return null;
  if (!DECIMAL2.test(s) || Number(s) > 100) return 'errores.porcentaje';
  return null;
}

/** Clave de error para un importe no negativo (costo). */
export function errorImporte(v) {
  const s = String(v ?? '').trim().replace(',', '.');
  if (!s) return 'errores.obligatorio';
  if (!DECIMAL4.test(s)) return 'errores.importe';
  return null;
}

/** Normaliza un número escrito con coma decimal. */
export const numero = (v) => Number(String(v ?? '').trim().replace(',', '.')) || 0;

/**
 * Valida las líneas de una venta. Devuelve un mapa índice → { campo: claveError } y errores generales.
 * @param {object[]} lineas líneas del formulario { producto, cantidad, porcentajeDescuento }
 */
export function validarLineasVenta(lineas) {
  const porLinea = {};
  lineas.forEach((l, i) => {
    const e = {};
    const c = errorCantidad(l.cantidad);
    if (c) e.cantidad = c;
    const d = errorPorcentaje(l.porcentajeDescuento);
    if (d) e.porcentajeDescuento = d;
    if (l.producto && (l.producto.precioVigente === null || l.producto.precioVigente === undefined)) e.producto = 'venta.sinPrecioVigente';
    if (Object.keys(e).length) porLinea[i] = e;
  });
  return { porLinea, sinLineas: lineas.length === 0, valido: lineas.length > 0 && Object.keys(porLinea).length === 0 };
}
