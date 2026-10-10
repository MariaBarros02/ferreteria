/*
 * Nombre: http.js
 * Descripción: Cliente HTTP (Axios): token Bearer, idioma Accept-Language, bloqueo de escrituras sin conexión (RN-10) y manejo de 401.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import axios from 'axios';
import i18n from '../i18n';
import { leerSesion, borrarSesion } from '../auth/session';

/** Evento emitido cuando el servidor rechaza el token (sesión expirada). */
export const EVENTO_SESION_EXPIRADA = 'auth:expirada';

/** Instancia compartida; JSON como formato de intercambio. */
export const http = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '',
  headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
  timeout: 15000,
});

http.interceptors.request.use((config) => {
  const metodo = (config.method || 'get').toLowerCase();
  if (metodo !== 'get' && typeof navigator !== 'undefined' && navigator.onLine === false) {
    const e = new Error('offline');
    e.code = 'OFFLINE'; // RN-10: sin conexión no se confirman transacciones
    return Promise.reject(e);
  }
  const s = leerSesion();
  if (s) config.headers.Authorization = `Bearer ${s.token}`;
  config.headers['Accept-Language'] = i18n.language;
  return config;
});

http.interceptors.response.use(
  (r) => r,
  (error) => {
    const url = error.config?.url || '';
    if (error.response?.status === 401 && !url.includes('/api/auth/login')) {
      borrarSesion();
      window.dispatchEvent(new Event(EVENTO_SESION_EXPIRADA));
    }
    return Promise.reject(error);
  },
);

/**
 * Llamadas a la API agrupadas por recurso.
 */
export const api = {
  login: (datos) => http.post('/api/auth/login', datos).then((r) => r.data),
  refresh: () => http.post('/api/auth/refresh').then((r) => r.data),
  productos: {
    listar: (params) => http.get('/api/productos', { params }).then((r) => r.data),
    crear: (d) => http.post('/api/productos', d).then((r) => r.data),
    actualizar: (id, d) => http.put(`/api/productos/${id}`, d).then((r) => r.data),
    eliminar: (id) => http.delete(`/api/productos/${id}`),
  },
  terceros: {
    listar: (params) => http.get('/api/terceros', { params }).then((r) => r.data),
    crear: (d) => http.post('/api/terceros', d).then((r) => r.data),
    actualizar: (id, d) => http.put(`/api/terceros/${id}`, d).then((r) => r.data),
    eliminar: (id) => http.delete(`/api/terceros/${id}`),
  },
  ventas: {
    listar: (params) => http.get('/api/ventas', { params }).then((r) => r.data),
    crear: (d) => http.post('/api/ventas', d).then((r) => r.data),
  },
};
