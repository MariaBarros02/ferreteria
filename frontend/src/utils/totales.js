/*
 * Nombre: totales.js
 * Descripción: Vista previa de importes de una venta/compra con las mismas reglas del servidor (Montos.java). El total definitivo lo calcula siempre el servidor.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */

/** Redondeo HALF_UP a 2 decimales (desplazamiento decimal en texto para evitar errores de coma flotante: 1.005 → 1.01). */
export const redondear = (v) => {
  const n = Number(v) || 0;
  const s = Math.sign(n) || 1;
  const abs = Math.abs(n);
  const texto = String(abs);
  const r = texto.includes('e') ? Math.round(abs * 100) / 100 : Number(`${Math.round(Number(`${texto}e2`))}e-2`);
  return s * r;
};

/**
 * Importes de una línea: subtotal = cantidad × precio; descuento sobre subtotal; IVA sobre (subtotal − descuento).
 * @param {number} cantidadBase cantidad en unidad base
 * @param {number} precio precio unitario antes de impuestos
 * @param {number} pctDescuento porcentaje 0–100
 * @param {number} pctIva porcentaje 0–100
 */
export function linea(cantidadBase, precio, pctDescuento = 0, pctIva = 0) {
  const subtotal = redondear(Number(cantidadBase || 0) * Number(precio || 0));
  const descuento = redondear((subtotal * Number(pctDescuento || 0)) / 100);
  const impuesto = redondear(((subtotal - descuento) * Number(pctIva || 0)) / 100);
  return { subtotal, descuento, impuesto, total: redondear(subtotal - descuento + impuesto) };
}

/** Suma de líneas ya redondeadas. */
export function totales(lineas) {
  return lineas.reduce((t, l) => ({
    subtotal: redondear(t.subtotal + l.subtotal), descuentos: redondear(t.descuentos + l.descuento),
    impuestos: redondear(t.impuestos + l.impuesto), total: redondear(t.total + l.total),
  }), { subtotal: 0, descuentos: 0, impuestos: 0, total: 0 });
}

/** Factor de conversión de una presentación (1 para la unidad base). */
export function factor(producto, unidad) {
  if (!unidad || !producto) return 1;
  const c = (producto.unidades || []).find((u) => u.unidadPresentacion.toLowerCase() === unidad.toLowerCase());
  return c ? Number(c.factor) : 1;
}
