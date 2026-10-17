/*
 * Nombre: offline_pdf.test.js
 * Descripción: Pruebas del catálogo sin conexión (sincronización paginada, búsqueda sin tildes, borrado al cerrar sesión) y del PDF del comprobante (leyenda interna vs. fiscal, nombre de archivo seguro).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { describe, expect, it, vi } from 'vitest';
import i18n from '../i18n';
import { borrarCatalogo, buscarLocal, leerCatalogo, sincronizarCatalogo } from '../offline/catalogo';
import { crearComprobantePdf, nombreArchivoComprobante } from '../pdf/comprobante';
import { leyendaComprobante } from '../utils/comprobante';

describe('catálogo sin conexión (RF-12)', () => {
  it('FE-OFF-01 sincroniza todas las páginas, guarda la fecha y se borra al cerrar sesión', async () => {
    const pagina = vi.fn(async (p) => ({ contenido: [{ id: p * 2 + 1, nombre: `P${p}a` }, { id: p * 2 + 2, nombre: `P${p}b` }], totalPaginas: 3 }));
    const r = await sincronizarCatalogo(pagina, () => new Date('2026-10-16T12:00:00Z'));
    expect(pagina).toHaveBeenCalledTimes(3);
    expect(r).toEqual({ total: 6, sincronizado: '2026-10-16T12:00:00.000Z' });
    const local = await leerCatalogo();
    expect(local.productos).toHaveLength(6);
    expect(local.sincronizado).toBe('2026-10-16T12:00:00.000Z');
    await borrarCatalogo();
    expect((await leerCatalogo()).productos).toHaveLength(0);
  });
  it('FE-OFF-02 busca por nombre, marca, presentación o código sin distinguir tildes ni mayúsculas', () => {
    const ps = [{ nombre: 'Martillo', marca: 'Stanley', presentacion: '16oz', codigoBarras: '7701' }, { nombre: 'Tubería PVC', marca: 'Pavco', presentacion: '1/2', codigoBarras: '7702' }];
    expect(buscarLocal(ps, 'tuberia')).toHaveLength(1);
    expect(buscarLocal(ps, 'STAN')).toHaveLength(1);
    expect(buscarLocal(ps, '7702')[0].marca).toBe('Pavco');
    expect(buscarLocal(ps, '  ')).toHaveLength(2);
  });
});

const detalle = (fiscal) => ({
  empresa: { nombre: 'Ferretería Demo', nit: '900123456-7', direccion: 'Calle 1', ciudad: 'Neiva', telefono: '123', correo: 'a@b.co' },
  cliente: { nombre: 'Juan Pérez', tipoIdentificacion: 'CC', numeroIdentificacion: '1002003000', direccion: 'Cra 2', ciudad: 'Neiva', telefono: '300', correo: 'j@p.co' },
  usuarioNombre: 'Laura',
  venta: { id: 7, numeroInterno: 'V-000007', fecha: '2026-10-16T15:00:00', subtotal: 100, totalDescuentos: 0, totalImpuestos: 19, total: 119,
    factura: { numeracionDian: 'FE-10', fechaEmision: '2026-10-16T15:00:00', validaFiscalmente: fiscal },
    lineas: [{ id: 1, productoId: 1, producto: 'Martillo', codigoBarras: '7701', cantidad: 1, unidadBase: 'unidad', precioAplicado: 100, descuento: 0, impuesto: 19, total: 119 }] },
});

describe('comprobante PDF (RF-19)', () => {
  const t = i18n.getFixedT('es');
  it('FE-PDF-01 sin integración fiscal el PDF lleva la leyenda de comprobante interno', () => {
    const salida = crearComprobantePdf(detalle(false), t).output();
    expect(salida).toContain('Comprobante interno de venta');
    expect(salida).toContain('V-000007');
    expect(salida).not.toContain('Representaci');
  });
  it('FE-PDF-02 con numeración fiscal válida usa la leyenda de factura electrónica', () => {
    expect(leyendaComprobante(t, detalle(true).venta)).toMatch(/factura electrónica/i);
    expect(crearComprobantePdf(detalle(true), t).output()).toContain('Representaci');
  });
  it('FE-PDF-03 nombre de archivo sin caracteres peligrosos', () => {
    expect(nombreArchivoComprobante({ numeroInterno: 'V-1/../x' })).toBe('comprobante-V-1____x.pdf');
  });
});
