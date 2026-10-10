/*
 * Nombre: schemas.js
 * Descripción: Esquemas Yup de validación de formularios (producto, tercero, login, venta) alineados con las reglas del backend.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import * as yup from 'yup';

/** Rechaza < y > (defensa en profundidad frente a XSS almacenado; el backend aplica la misma regla). */
export const SIN_HTML = /^[^<>]*$/;
const CODIGO_BARRAS = /^\d{8,14}$/;
const IDENTIFICACION = /^[A-Za-z0-9-]{4,20}$/;
const TELEFONO = /^[0-9+()\-\s]{5,30}$/;

/**
 * Los mensajes son claves i18n con parámetro opcional ("clave|param"); se traducen al renderizar.
 * @param {number} max - Longitud máxima.
 * @returns {string} Mensaje codificado.
 */
export const msgMax = (max) => `errores.maximo|${max}`;

/**
 * Texto obligatorio, recortado, con longitud máxima y sin HTML.
 * @param {number} max - Longitud máxima.
 * @returns {yup.StringSchema} Esquema.
 */
export const textoObligatorio = (max) =>
  yup.string().trim().required('errores.obligatorio').max(max, msgMax(max)).matches(SIN_HTML, 'errores.sinHtml');

/**
 * Texto opcional, recortado, con longitud máxima y sin HTML.
 * @param {number} max - Longitud máxima.
 * @returns {yup.StringSchema} Esquema.
 */
export const textoOpcional = (max) =>
  yup.string().trim().max(max, msgMax(max)).matches(SIN_HTML, { message: 'errores.sinHtml', excludeEmptyString: true });

/** Esquema del formulario de inicio de sesión. */
export const loginSchema = yup.object({
  correo: yup.string().trim().required('errores.obligatorio').email('errores.correo').max(150, msgMax(150)),
  password: yup.string().required('errores.obligatorio').max(128, msgMax(128)),
});

/** Esquema del formulario de producto (RF-01, RN-01). */
export const productoSchema = yup.object({
  nombre: textoObligatorio(150),
  marca: textoObligatorio(100),
  presentacion: textoObligatorio(100),
  codigoBarras: yup.string().trim().required('errores.obligatorio').matches(CODIGO_BARRAS, 'errores.codigoBarras'),
  unidadBase: textoObligatorio(20),
  tipoRastreo: yup.string().oneOf(['NINGUNO', 'LOTE', 'SERIE']).required('errores.obligatorio'),
  tieneVencimiento: yup.boolean(),
  tieneGarantia: yup.boolean(),
});

/** Esquema del formulario de tercero con reglas condicionales por tipo de persona (RN-06). */
export const terceroSchema = yup.object({
  tipoPersona: yup.string().oneOf(['NATURAL', 'JURIDICA']).required('errores.obligatorio'),
  tipoIdentificacion: yup.string().oneOf(['CC', 'NIT', 'CE', 'PASAPORTE']).required('errores.obligatorio'),
  numeroIdentificacion: yup.string().trim().required('errores.obligatorio').matches(IDENTIFICACION, 'errores.identificacion'),
  primerNombre: yup.string().when('tipoPersona', { is: 'NATURAL', then: () => textoObligatorio(80), otherwise: () => textoOpcional(80) }),
  segundoNombre: textoOpcional(80),
  primerApellido: yup.string().when('tipoPersona', { is: 'NATURAL', then: () => textoObligatorio(80), otherwise: () => textoOpcional(80) }),
  segundoApellido: textoOpcional(80),
  razonSocial: yup.string().when('tipoPersona', { is: 'JURIDICA', then: () => textoObligatorio(200), otherwise: () => textoOpcional(200) }),
  direccion: textoObligatorio(200),
  ciudad: textoObligatorio(80),
  telefono: yup.string().trim().required('errores.obligatorio').matches(TELEFONO, 'errores.telefono'),
  correo: yup.string().trim().required('errores.obligatorio').email('errores.correo').max(150, msgMax(150)),
  cliente: yup.boolean(),
  proveedor: yup.boolean().test('un-rol', 'errores.unRol', function (v) { return Boolean(v || this.parent.cliente); }),
});

/** Esquema del formulario de venta (RF-04). */
export const ventaSchema = yup.object({
  clienteId: yup.number().typeError('errores.obligatorio').required('errores.obligatorio').positive('errores.positivo'),
  lineas: yup.array().of(yup.object({
    productoId: yup.number().typeError('errores.obligatorio').required('errores.obligatorio').positive('errores.positivo'),
    cantidad: yup.number().typeError('errores.obligatorio').required('errores.obligatorio').moreThan(0, 'errores.positivo')
      .test('decimales', 'errores.decimales', (v) => v === undefined || /^\d+(\.\d{1,4})?$/.test(String(v))),
    unidad: textoOpcional(20),
  })).min(1, 'errores.lineas'),
});
