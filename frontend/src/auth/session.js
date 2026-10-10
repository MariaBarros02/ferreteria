/*
 * Nombre: session.js
 * Descripción: Persistencia de la sesión: localStorage si el usuario elige mantenerla, sessionStorage en caso contrario.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
const CLAVE = 'sesion-inventario';

/**
 * Guarda la sesión.
 * @param {{token:string, usuario:object, expiraEnSegundos:number}} datos - Respuesta de login/refresh.
 * @param {boolean} recordar - true para permanencia entre cierres del navegador.
 * @param {number} [ahora=Date.now()] - Marca de tiempo actual (inyectable en pruebas).
 */
export function guardarSesion(datos, recordar, ahora = Date.now()) {
  const sesion = { token: datos.token, usuario: datos.usuario, expiraEn: ahora + datos.expiraEnSegundos * 1000, recordar };
  const [destino, otro] = recordar ? [localStorage, sessionStorage] : [sessionStorage, localStorage];
  otro.removeItem(CLAVE);
  destino.setItem(CLAVE, JSON.stringify(sesion));
}

/**
 * Lee la sesión vigente; elimina y devuelve null si expiró o está corrupta.
 * @param {number} [ahora=Date.now()] - Marca de tiempo actual.
 * @returns {{token:string, usuario:object, expiraEn:number, recordar:boolean}|null} Sesión o null.
 */
export function leerSesion(ahora = Date.now()) {
  for (const almacen of [localStorage, sessionStorage]) {
    const crudo = almacen.getItem(CLAVE);
    if (!crudo) continue;
    try {
      const s = JSON.parse(crudo);
      if (s && typeof s.token === 'string' && typeof s.expiraEn === 'number' && s.expiraEn > ahora) return s;
    } catch { /* sesión corrupta */ }
    almacen.removeItem(CLAVE);
  }
  return null;
}

/** Elimina la sesión de ambos almacenamientos. */
export function borrarSesion() {
  localStorage.removeItem(CLAVE);
  sessionStorage.removeItem(CLAVE);
}
