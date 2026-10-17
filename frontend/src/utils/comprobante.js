/*
 * Nombre: comprobante.js
 * Descripción: Textos del comprobante de venta (título, leyenda fiscal o interna y nombre del archivo) compartidos por la vista previa y el PDF, sin depender de la librería de PDF.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */

/** Nombre del archivo PDF (solo caracteres seguros). */
export const nombreArchivoComprobante = (venta) =>
  `comprobante-${String(venta?.numeroInterno || venta?.id || 'venta').replace(/[^A-Za-z0-9_-]/g, '_')}.pdf`;

/** Leyenda según la validez fiscal de la numeración (nunca se presenta un comprobante interno como factura electrónica). */
export const leyendaComprobante = (t, venta) =>
  (venta?.factura?.validaFiscalmente ? t('comprobante.leyendaFiscal') : t('comprobante.leyendaInterna'));

/** Título del documento. */
export const tituloComprobante = (t, venta) =>
  (venta?.factura?.validaFiscalmente ? t('comprobante.tituloFiscal') : t('comprobante.tituloInterno'));

/** Une los datos presentes con « · » (los vacíos se omiten: no se imprimen datos inventados ni separadores sueltos). */
export const unir = (...partes) => partes.filter((x) => x !== null && x !== undefined && String(x).trim() !== '').join(' · ');
