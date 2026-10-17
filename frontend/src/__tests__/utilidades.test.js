/*
 * Nombre: utilidades.test.js
 * Descripción: Pruebas unitarias de utilidades: redondeo e importes (paridad con Montos.java), formato de fechas, validación de líneas, permisos por rol, claves de idempotencia, conversiones de formularios y helpers de importación/devolución/precios.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { describe, expect, it } from 'vitest';
import { factor, linea, redondear, totales } from '../utils/totales';
import { fecha, hoyIso, mesActual } from '../utils/formato';
import { errorCantidad, errorImporte, errorPorcentaje, numero, validarLineasVenta } from '../validation/lineas';
import { puede, puedeImportar } from '../utils/permisos';
import { nuevaClaveOperacion } from '../utils/archivos';
import { aFormulario, aPeticion } from '../pages/ProductosPage';
import { errorArchivo, MAX_BYTES, resumenFilas } from '../pages/admin/ImportacionPage';
import { devolvible } from '../pages/devoluciones/DevolucionNuevaPage';
import { aFraccion } from '../pages/control/PreciosPage';
import { CLAVE_SEGURA } from '../pages/admin/UsuariosPage';
import { menuPara } from '../components/Layout';

describe('totales (vista previa de importes)', () => {
  it('FE-TOT-01 redondeo HALF_UP sin errores de coma flotante', () => {
    expect(redondear(1.005)).toBe(1.01);
    expect(redondear(2.675)).toBe(2.68);
    expect(redondear(-1.005)).toBe(-1.01);
    expect(redondear(0)).toBe(0);
    expect(redondear('abc')).toBe(0);
  });
  it('FE-TOT-02 línea: descuento sobre subtotal e IVA sobre la base descontada', () => {
    expect(linea(3, 10000, 10, 19)).toEqual({ subtotal: 30000, descuento: 3000, impuesto: 5130, total: 32130 });
    expect(linea(1, 999.99, 0, 0)).toEqual({ subtotal: 999.99, descuento: 0, impuesto: 0, total: 999.99 });
  });
  it('FE-TOT-03 suma de líneas y factor de presentación', () => {
    const t = totales([linea(2, 100, 0, 19), linea(1, 50, 10, 0)]);
    expect(t).toEqual({ subtotal: 250, descuentos: 5, impuestos: 38, total: 283 });
    const p = { unidades: [{ unidadPresentacion: 'Caja', factor: '12' }] };
    expect(factor(p, 'caja')).toBe(12);
    expect(factor(p, '')).toBe(1);
    expect(factor(p, 'bulto')).toBe(1);
  });
});

describe('formato', () => {
  it('FE-FMT-01 la fecha sin hora no se desplaza por zona horaria', () => {
    expect(fecha('2026-01-01')).toBe(new Date(2026, 0, 1).toLocaleDateString('es-CO'));
    expect(fecha(null)).toBe('—');
  });
  it('FE-FMT-02 hoy y mes actual en formato ISO local', () => {
    const d = new Date(2026, 9, 5, 23, 59);
    expect(hoyIso(d)).toBe('2026-10-05');
    expect(mesActual(d)).toBe('2026-10');
  });
});

describe('validación de líneas', () => {
  it('FE-LIN-01 cantidad: obligatoria, positiva y con máximo 4 decimales (acepta coma)', () => {
    expect(errorCantidad('')).toBe('errores.obligatorio');
    expect(errorCantidad('0')).toBe('errores.positivo');
    expect(errorCantidad('-1')).toBe('errores.decimales');
    expect(errorCantidad('1.23456')).toBe('errores.decimales');
    expect(errorCantidad('1,5')).toBeNull();
    expect(numero('1,5')).toBe(1.5);
  });
  it('FE-LIN-02 porcentaje 0–100 con 2 decimales e importe no negativo', () => {
    expect(errorPorcentaje('')).toBeNull();
    expect(errorPorcentaje('100')).toBeNull();
    expect(errorPorcentaje('100.01')).toBe('errores.porcentaje');
    expect(errorPorcentaje('10.123')).toBe('errores.porcentaje');
    expect(errorImporte('0')).toBeNull();
    expect(errorImporte('-5')).toBe('errores.importe');
  });
  it('FE-LIN-03 una venta sin líneas o con producto sin precio vigente es inválida', () => {
    expect(validarLineasVenta([]).valido).toBe(false);
    const r = validarLineasVenta([{ producto: { precioVigente: null }, cantidad: '1', porcentajeDescuento: '' }]);
    expect(r.porLinea[0].producto).toBe('venta.sinPrecioVigente');
    expect(validarLineasVenta([{ producto: { precioVigente: 10 }, cantidad: '2', porcentajeDescuento: '5' }]).valido).toBe(true);
  });
});

describe('permisos y navegación por rol (RF-09)', () => {
  it('FE-PER-01 matriz de acciones', () => {
    expect(puede('Ventas', 'ventas.registrar')).toBe(true);
    expect(puede('Gerencia', 'ventas.registrar')).toBe(false);
    expect(puede('Gerencia', 'precios.aprobar')).toBe(true);
    expect(puede('Inventario', 'precios.aprobar')).toBe(false);
    expect(puede(null, 'ventas.ver')).toBe(false);
    expect(puede('Ventas', 'accion.inexistente')).toBe(false);
    expect(puedeImportar('Ventas')).toBe(true);
    expect(puedeImportar('Gerencia')).toBe(false);
  });
  it('FE-PER-02 el menú oculta módulos sin permiso y no deja grupos vacíos', () => {
    const rutas = (rol) => menuPara(rol).flatMap((g) => g.enlaces.map((l) => l.to));
    expect(rutas('Ventas')).toEqual(expect.arrayContaining(['/ventas', '/productos', '/importacion']));
    expect(rutas('Ventas')).not.toContain('/usuarios');
    expect(rutas('Ventas')).not.toContain('/cierres');
    expect(rutas('Gerencia')).toContain('/cierres');
    expect(rutas('Administrador')).toContain('/usuarios');
    expect(menuPara('Ventas').every((g) => g.enlaces.length > 0)).toBe(true);
  });
});

describe('formularios y helpers de módulos', () => {
  it('FE-HLP-01 la clave de operación cumple el patrón del servidor y es única', () => {
    const a = nuevaClaveOperacion();
    expect(a).toMatch(/^[A-Za-z0-9_-]{8,64}$/);
    expect(nuevaClaveOperacion()).not.toBe(a);
  });
  it('FE-HLP-02 producto: el recargo se edita en % y se envía como fracción; vacíos se envían como null', () => {
    const f = aFormulario({ id: 1, nombre: 'Taladro', marca: 'B', presentacion: 'x', codigoBarras: '12345678', unidadBase: 'u', tipoRastreo: 'NINGUNO',
      porcentajeIncremento: 0.3, porcentajeIva: 19, stockMinimo: null, mesesGarantia: null, descripcion: null });
    expect(f.porcentajeIncremento).toBe('30');
    expect(f.porcentajeIva).toBe('19');
    expect(f.stockMinimo).toBe('');
    const p = aPeticion({ ...f, porcentajeIncremento: '12,5', descripcion: '  ', tieneGarantia: false, mesesGarantia: '6' });
    expect(p.porcentajeIncremento).toBe(0.125);
    expect(p.descripcion).toBeNull();
    expect(p.mesesGarantia).toBeNull();
    expect(p).not.toHaveProperty('id');
  });
  it('FE-HLP-03 importación: solo .xlsx, no vacío y hasta 5 MB; resumen por estado', () => {
    expect(errorArchivo(null)).toBe('importacion.seleccioneArchivo');
    expect(errorArchivo({ name: 'datos.csv', size: 10 })).toBe('importacion.soloXlsx');
    expect(errorArchivo({ name: 'datos.xlsx', size: MAX_BYTES + 1 })).toBe('importacion.archivoGrande');
    expect(errorArchivo({ name: 'datos.xlsx', size: 0 })).toBe('importacion.archivoVacio');
    expect(errorArchivo({ name: 'DATOS.XLSX', size: MAX_BYTES })).toBeNull();
    expect(resumenFilas([{ estado: 'VALIDA' }, { estado: 'ERROR' }, { estado: 'VALIDA' }])).toEqual({ VALIDA: 2, ERROR: 1 });
  });
  it('FE-HLP-04 devolución: lo devolvible es lo vendido menos lo ya devuelto, nunca negativo', () => {
    expect(devolvible({ cantidad: '5', cantidadDevuelta: '2' })).toBe(3);
    expect(devolvible({ cantidad: '5', cantidadDevuelta: null })).toBe(5);
    expect(devolvible({ cantidad: '5', cantidadDevuelta: '7' })).toBe(0);
  });
  it('FE-HLP-05 precio: porcentaje a fracción y política de contraseñas', () => {
    expect(aFraccion('30')).toBe(0.3);
    expect(aFraccion('12,5')).toBe(0.125);
    expect(CLAVE_SEGURA.test('Ferreteria2026')).toBe(true);
    expect(CLAVE_SEGURA.test('corta1A')).toBe(false);
    expect(CLAVE_SEGURA.test('sinmayusculas123')).toBe(false);
  });
});
