/*
 * Nombre: formato.js
 * Descripción: Formateo de importes en cualquier moneda ISO 4217 (COP por defecto), tasas de cambio, cantidades, fechas locales y fechas UTC del backend.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */

/** Decimales de presentación por moneda (COP sin decimales; el servidor guarda 2). */
const DECIMALES = { COP: 0, CLP: 0, JPY: 0 };

/**
 * Importe con el formato de su moneda ISO 4217 (COP por defecto, RF-23).
 * @param {number|string} n importe
 * @param {number} [decimales] decimales a mostrar (por defecto, los de la moneda)
 * @param {string} [codigo] código de la moneda
 */
export const moneda = (n, decimales, codigo = 'COP') => {
  if (n === null || n === undefined || n === '') return '—';
  const d = decimales ?? DECIMALES[codigo] ?? 2;
  try {
    return new Intl.NumberFormat('es-CO', { style: 'currency', currency: codigo || 'COP', minimumFractionDigits: d, maximumFractionDigits: d }).format(Number(n));
  } catch {
    return `${codigo} ${new Intl.NumberFormat('es-CO', { minimumFractionDigits: d, maximumFractionDigits: d }).format(Number(n))}`;
  }
};

/** Tasa de cambio legible: "1 USD = 4.000,00 COP". */
export const tasaTexto = (codigo, tasa) => (tasa === null || tasa === undefined ? '—'
  : `1 ${codigo} = ${new Intl.NumberFormat('es-CO', { maximumFractionDigits: 6, minimumFractionDigits: 2 }).format(Number(tasa))} COP`);

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
