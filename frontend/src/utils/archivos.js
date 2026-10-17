/*
 * Nombre: archivos.js
 * Descripción: Utilidades del navegador: guardar un Blob como archivo descargable y generar claves de idempotencia.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */

/** Ofrece un Blob como descarga con el nombre indicado. */
export function guardarBlob(blob, nombre) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = nombre;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/** Clave de operación única por envío de formulario (un reintento del mismo envío no duplica la operación). */
export function nuevaClaveOperacion() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID();
  return `op-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
}
