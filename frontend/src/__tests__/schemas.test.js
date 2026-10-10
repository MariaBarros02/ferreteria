/*
 * Nombre: schemas.test.js
 * Descripción: Pruebas unitarias de los esquemas Yup: reglas RN-01/RN-06, límites, anti-HTML y venta.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { describe, expect, it } from 'vitest';
import { loginSchema, productoSchema, terceroSchema, ventaSchema } from '../validation/schemas';

const ok = (schema, v) => schema.isValidSync(v);
const mensajes = (schema, v) => { try { schema.validateSync(v, { abortEarly: false }); return []; } catch (e) { return e.errors; } };

const producto = { nombre: 'Taladro', marca: 'Bosch', presentacion: '500W', codigoBarras: '7701234500015', unidadBase: 'unidad', tipoRastreo: 'SERIE' };
const natural = { tipoPersona: 'NATURAL', tipoIdentificacion: 'CC', numeroIdentificacion: '1002003000', primerNombre: 'Juan', primerApellido: 'Pérez',
  direccion: 'Cra 1', ciudad: 'Cali', telefono: '3001234567', correo: 'a@b.com', cliente: true, proveedor: false };

describe('productoSchema', () => {
  it('FE-VAL-01 acepta un producto válido', () => expect(ok(productoSchema, producto)).toBe(true));
  it('FE-VAL-02 rechaza código de barras con letras o longitud fuera de 8-14', () => {
    for (const c of ['123', '77012345ABCD1', '123456789012345', "7701234500015' OR '1'='1"]) {
      expect(ok(productoSchema, { ...producto, codigoBarras: c })).toBe(false);
    }
    expect(ok(productoSchema, { ...producto, codigoBarras: '12345678' })).toBe(true);
    expect(ok(productoSchema, { ...producto, codigoBarras: '12345678901234' })).toBe(true);
  });
  it('FE-VAL-03 rechaza < y > (XSS) y respeta la longitud máxima en el borde', () => {
    expect(ok(productoSchema, { ...producto, nombre: '<script>alert(1)</script>' })).toBe(false);
    expect(ok(productoSchema, { ...producto, nombre: 'a'.repeat(150) })).toBe(true);
    expect(ok(productoSchema, { ...producto, nombre: 'a'.repeat(151) })).toBe(false);
  });
  it('FE-VAL-04 obligatorios: vacío o solo espacios', () => {
    expect(mensajes(productoSchema, { ...producto, nombre: '   ' })).toContain('errores.obligatorio');
  });
});

describe('terceroSchema (RN-06)', () => {
  it('FE-VAL-05 persona natural válida', () => expect(ok(terceroSchema, natural)).toBe(true));
  it('FE-VAL-06 natural exige nombre y apellido', () => {
    expect(ok(terceroSchema, { ...natural, primerApellido: '' })).toBe(false);
    expect(ok(terceroSchema, { ...natural, primerNombre: '' })).toBe(false);
  });
  it('FE-VAL-07 jurídica con razón social y sin apellido es válida; sin razón social no', () => {
    const j = { ...natural, tipoPersona: 'JURIDICA', tipoIdentificacion: 'NIT', primerNombre: '', primerApellido: '', razonSocial: 'Distribuidora S.A.S.' };
    expect(ok(terceroSchema, j)).toBe(true);
    expect(ok(terceroSchema, { ...j, razonSocial: '' })).toBe(false);
  });
  it('FE-VAL-08 exige al menos un rol', () => {
    expect(mensajes(terceroSchema, { ...natural, cliente: false, proveedor: false })).toContain('errores.unRol');
  });
  it('FE-VAL-09 correo, teléfono e identificación con formato', () => {
    expect(ok(terceroSchema, { ...natural, correo: 'no-correo' })).toBe(false);
    expect(ok(terceroSchema, { ...natural, telefono: 'abc' })).toBe(false);
    expect(ok(terceroSchema, { ...natural, numeroIdentificacion: 'ab' })).toBe(false);
    expect(ok(terceroSchema, { ...natural, numeroIdentificacion: "1; DROP" })).toBe(false);
  });
});

describe('loginSchema', () => {
  it('FE-VAL-10 correo válido y contraseña obligatoria', () => {
    expect(ok(loginSchema, { correo: 'a@b.com', password: 'x' })).toBe(true);
    expect(ok(loginSchema, { correo: 'a@b.com', password: '' })).toBe(false);
    expect(ok(loginSchema, { correo: 'no', password: 'x' })).toBe(false);
  });
});

describe('ventaSchema', () => {
  const venta = { clienteId: 1, lineas: [{ productoId: 1, cantidad: 2, unidad: '' }] };
  it('FE-VAL-11 venta válida', () => expect(ok(ventaSchema, venta)).toBe(true));
  it('FE-VAL-12 cantidad cero, negativa o con más de 4 decimales es inválida', () => {
    for (const c of [0, -1, 1.23456]) expect(ok(ventaSchema, { ...venta, lineas: [{ productoId: 1, cantidad: c, unidad: '' }] })).toBe(false);
    expect(ok(ventaSchema, { ...venta, lineas: [{ productoId: 1, cantidad: 0.5, unidad: '' }] })).toBe(true);
  });
  it('FE-VAL-13 exige al menos una línea', () => expect(ok(ventaSchema, { ...venta, lineas: [] })).toBe(false));
});
