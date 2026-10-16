/*
 * Nombre: formato.js
 * Descripción: Formateo de moneda (COP), cantidades, fechas locales y fechas UTC del backend.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */

/** Moneda colombiana sin decimales (los importes se guardan con 2 decimales en el servidor). */
export const moneda = (n, decimales = 0) => (n === null || n === undefined || n === '' ? '—'
  : new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', minimumFractionDigits: decimales, maximumFractionDigits: decimales }).format(Number(n)));

/** Cantidad con hasta 4 decimales, sin ceros sobrantes. */
export const cantidad = (n) => (n === null || n === undefined || n === '' ? '—'
  : new Intl.NumberFormat('es-CO', { maximumFractionDigits: 4 }).format(Number(n)));

/** Porcentaje legible (19 → "19 %"). */
export const porcentaje = (n) => (n === null || n === undefined ? '—' : `${cantidad(n)} %`);

/** Fecha 'AAAA-MM-DD' (sin zona) en formato local, sin desplazar el día. */
export function fecha(iso) {
  if (!iso) return '—';
  const [a, m, d] = String(iso).slice(0, 10).split('-').map(Number);
  return new Date(a, m - 1, d).toLocaleDateString('es-CO');
}

/** Fecha y hora UTC del backend ('2026-10-16T15:00:00') en la zona del navegador. */
export function fechaHora(isoUtc) {
  if (!isoUtc) return '—';
  const s = String(isoUtc);
  const conZona = /[zZ]|[+-]\d\d:?\d\d$/.test(s) ? s : `${s}Z`;
  return new Date(conZona).toLocaleString('es-CO');
}

/** Día local de hoy en formato AAAA-MM-DD. */
export function hoyIso(ahora = new Date()) {
  const p = (x) => String(x).padStart(2, '0');
  return `${ahora.getFullYear()}-${p(ahora.getMonth() + 1)}-${p(ahora.getDate())}`;
}

/** Mes actual AAAA-MM. */
export const mesActual = (ahora = new Date()) => hoyIso(ahora).slice(0, 7);
