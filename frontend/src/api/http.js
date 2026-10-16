/*
 * Nombre: http.js
 * Descripción: Cliente HTTP (Axios): token Bearer, idioma Accept-Language, bloqueo de escrituras sin conexión (RN-10), manejo de 401 y funciones de todos los módulos de la API.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
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
  timeout: 30000,
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

const datos = (r) => r.data;
const get = (url, params) => http.get(url, { params }).then(datos);
const post = (url, body) => http.post(url, body ?? {}).then(datos);
const put = (url, body) => http.put(url, body).then(datos);
const patch = (url, body) => http.patch(url, body).then(datos);

/** Descarga un archivo binario y devuelve { blob, nombre } (nombre desde Content-Disposition). */
export async function descargar(url, params, nombrePorDefecto = 'archivo.xlsx') {
  let r;
  try {
    r = await http.get(url, { params, responseType: 'blob' });
  } catch (e) {
    // Con responseType 'blob' el error JSON llega como Blob: se convierte para mostrar el mensaje del servidor.
    const d = e?.response?.data;
    if (d && typeof d.text === 'function') {
      try { e.response.data = JSON.parse(await d.text()); } catch { /* no era JSON */ }
    }
    throw e;
  }
  const cd = r.headers?.['content-disposition'] || '';
  const m = /filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/i.exec(cd);
  const nombre = m ? decodeURIComponent(m[1] || m[2]) : nombrePorDefecto;
  return { blob: r.data, nombre };
}

export const api = {
  login: (d) => post('/api/auth/login', d),
  refresh: () => post('/api/auth/refresh'),
  productos: {
    listar: (params) => get('/api/productos', params),
    crear: (d) => post('/api/productos', d),
    actualizar: (id, d) => put(`/api/productos/${id}`, d),
    eliminar: (id) => http.delete(`/api/productos/${id}`),
    cambiarEstado: (id, activo) => patch(`/api/productos/${id}/estado`, { activo }),
    seleccion: (q, tamano = 15) => get('/api/productos/seleccion', { q, tamano }),
    porCodigo: (codigo) => get(`/api/productos/seleccion/codigo/${encodeURIComponent(codigo)}`),
    unidades: (id) => get(`/api/productos/${id}/unidades`),
    configurarUnidades: (id, d) => put(`/api/productos/${id}/unidades`, d),
  },
  terceros: {
    listar: (params) => get('/api/terceros', params),
    crear: (d) => post('/api/terceros', d),
    actualizar: (id, d) => put(`/api/terceros/${id}`, d),
    eliminar: (id) => http.delete(`/api/terceros/${id}`),
    seleccion: (q, rol = 'cliente', tamano = 15) => get('/api/terceros/seleccion', { q, rol, tamano }),
  },
  ventas: {
    listar: (params) => get('/api/ventas', params),
    crear: (d) => post('/api/ventas', d),
    obtener: (id) => get(`/api/ventas/${id}`),
    detalle: (id) => get(`/api/ventas/${id}/detalle`),
  },
  compras: {
    listar: (params) => get('/api/compras', params),
    crear: (d) => post('/api/compras', d),
    obtener: (id) => get(`/api/compras/${id}`),
    recibir: (id, d) => post(`/api/compras/${id}/recepciones`, d),
    anular: (id, motivo) => post(`/api/compras/${id}/anular`, { motivo }),
    anularIngreso: (idDocumento, motivo) => post(`/api/compras/ingresos/${idDocumento}/anular`, { motivo }),
  },
  precios: {
    propuestas: (params) => get('/api/precios/propuestas', params),
    proponer: (d) => post('/api/precios/propuestas', d),
    aprobar: (id) => post(`/api/precios/propuestas/${id}/aprobar`),
    rechazar: (id, motivo) => post(`/api/precios/propuestas/${id}/rechazar`, { motivo }),
    historial: (idProducto, params) => get(`/api/precios/productos/${idProducto}`, params),
  },
  inventario: {
    existencias: (params) => get('/api/inventario/existencias', params),
    alertas: (dias) => get('/api/inventario/alertas', dias ? { dias } : undefined),
    lotes: (idProducto) => get(`/api/inventario/productos/${idProducto}/lotes`),
    movimientos: (idProducto, params) => get(`/api/inventario/productos/${idProducto}/movimientos`, params),
    cambiarEstado: (d) => post('/api/inventario/cambios-estado', d),
    bajaLote: (idLote) => post(`/api/inventario/lotes/${idLote}/baja-vencimiento`),
    reservas: (params) => get('/api/inventario/reservas', params),
    reservar: (d) => post('/api/inventario/reservas', d),
    cancelarReserva: (id, motivo) => post(`/api/inventario/reservas/${id}/cancelar`, { motivo }),
    liberarVencidas: () => post('/api/inventario/reservas/liberar-vencidas'),
  },
  devoluciones: {
    listar: (params) => get('/api/devoluciones', params),
    obtener: (id) => get(`/api/devoluciones/${id}`),
    cliente: (d) => post('/api/devoluciones/clientes', d),
    proveedor: (d) => post('/api/devoluciones/proveedores', d),
  },
  ajustes: {
    listar: (params) => get('/api/ajustes', params),
    solicitar: (d) => post('/api/ajustes', d),
    aprobar: (id) => post(`/api/ajustes/${id}/aprobar`),
    rechazar: (id, motivo) => post(`/api/ajustes/${id}/rechazar`, { motivo }),
  },
  conteos: {
    listar: (params) => get('/api/conteos', params),
    obtener: (id) => get(`/api/conteos/${id}`),
    abrir: (d) => post('/api/conteos', d),
    registrar: (id, d) => post(`/api/conteos/${id}/registros`, d),
    cerrar: (id) => post(`/api/conteos/${id}/cerrar`),
    aprobar: (id) => post(`/api/conteos/${id}/aprobar`),
    rechazar: (id, motivo) => post(`/api/conteos/${id}/rechazar`, { motivo }),
  },
  cierres: {
    resumen: (periodo) => get('/api/cierres/resumen', { periodo }),
    listar: () => get('/api/cierres'),
    cerrar: (d) => post('/api/cierres', d),
    reabrir: (periodo, motivo) => post(`/api/cierres/${periodo}/reabrir`, { motivo }),
  },
  auditoria: {
    eventos: (params) => get('/api/auditoria/eventos', params),
    evento: (id) => get(`/api/auditoria/eventos/${id}`),
    movimientos: (params) => get('/api/auditoria/movimientos', params),
    catalogo: () => get('/api/auditoria/catalogo'),
  },
  usuarios: {
    listar: (params) => get('/api/usuarios', params),
    crear: (d) => post('/api/usuarios', d),
    actualizar: (id, d) => put(`/api/usuarios/${id}`, d),
    clave: (id, password) => post(`/api/usuarios/${id}/clave`, { password }),
    roles: () => get('/api/usuarios/roles'),
  },
  importaciones: {
    validar: (tipo, archivo) => {
      const fd = new FormData();
      fd.append('archivo', archivo);
      return http.post(`/api/importaciones/${tipo}/validar`, fd, { headers: { 'Content-Type': 'multipart/form-data' } }).then(datos);
    },
    historial: (params) => get('/api/importaciones', params),
    obtener: (id) => get(`/api/importaciones/${id}`),
    confirmar: (id, importarPosiblesDuplicados) => post(`/api/importaciones/${id}/confirmar`, { importarPosiblesDuplicados }),
    cancelar: (id) => post(`/api/importaciones/${id}/cancelar`),
    plantilla: (tipo) => descargar(`/api/importaciones/plantillas/${tipo}`, undefined, `plantilla-${tipo}.xlsx`),
    errores: (id) => descargar(`/api/importaciones/${id}/errores`, undefined, `importacion-${id}-errores.xlsx`),
  },
  monedas: {
    listar: (soloActivas = false) => get('/api/monedas', { soloActivas }),
    crear: (d) => post('/api/monedas', d),
    cambiarEstado: (codigo, activo) => patch(`/api/monedas/${codigo}/estado`, { activo }),
    tasas: (codigo, params) => get(`/api/monedas/${codigo}/tasas`, params),
    registrarTasa: (codigo, d) => post(`/api/monedas/${codigo}/tasas`, d),
    convertir: (monto, desde, hacia) => get('/api/monedas/conversion', { monto, desde, hacia }),
  },
  exportar: (que, params) => descargar(`/api/exportaciones/${que}`, params, `${que}.xlsx`),
  catalogo: (pagina, tamano = 500) => get('/api/catalogo/sincronizacion', { pagina, tamano }),
};
