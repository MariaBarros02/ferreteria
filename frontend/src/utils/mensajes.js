/*
 * Nombre: mensajes.js
 * Descripción: Utilidades para traducir mensajes de validación codificados ("clave|param") y errores de la API.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
/**
 * Traduce un mensaje de Yup codificado como "clave" o "clave|valor".
 * @param {Function} t - Función de traducción de i18next.
 * @param {string|undefined} mensaje - Mensaje codificado.
 * @returns {string|undefined} Texto traducido.
 */
export function traducirValidacion(t, mensaje) {
  if (!mensaje || typeof mensaje !== 'string') return undefined;
  const [clave, valor] = mensaje.split('|');
  return t(clave, { max: valor, defaultValue: mensaje });
}

/**
 * Obtiene un mensaje legible desde un error de Axios, priorizando el idioma del cliente por código de error.
 * @param {Function} t - Función de traducción.
 * @param {any} error - Error capturado.
 * @returns {string} Mensaje para el usuario.
 */
export function mensajeDeError(t, error) {
  if (error?.code === 'OFFLINE') return t('comun.sinConexion');
  const data = error?.response?.data;
  if (data?.codigo === 'CREDENCIALES_INVALIDAS') return t('errores.credenciales');
  if (data?.codigo === 'CUENTA_BLOQUEADA') return t('errores.bloqueada');
  if (data?.mensaje) return data.mensaje; // el backend ya responde en el idioma de Accept-Language
  return t('comun.errorGenerico');
}
