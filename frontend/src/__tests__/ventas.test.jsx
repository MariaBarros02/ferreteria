/*
 * Nombre: ventas.test.jsx
 * Descripción: Pruebas de componentes de ventas: selector de productos con lector de código (RF-11), registro de venta con selectores, vista previa de totales, confirmación e idempotencia (RF-04), y comprobante con leyenda y bloqueo de exportación (RF-19).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route } from 'react-router-dom';

vi.mock('../api/http', () => ({
  EVENTO_SESION_EXPIRADA: 'auth:expirada',
  api: {
    refresh: vi.fn(),
    productos: { seleccion: vi.fn(), porCodigo: vi.fn() },
    terceros: { seleccion: vi.fn() },
    ventas: { crear: vi.fn(), detalle: vi.fn() },
    inventario: { reservas: vi.fn() },
  },
}));

import { api } from '../api/http';
import ProductoSelector from '../components/ProductoSelector';
import VentaNuevaPage from '../pages/ventas/VentaNuevaPage';
import ComprobantePage from '../pages/ventas/ComprobantePage';
import { montar } from './ayudas';

const MARTILLO = { id: 1, nombre: 'Martillo', marca: 'Stanley', presentacion: '16oz', codigoBarras: '77010001', unidadBase: 'unidad',
  tipoRastreo: 'NINGUNO', activo: true, porcentajeIva: 19, precioVigente: 10000, disponible: 5, reservado: 0, vencidoSinBaja: 0, unidades: [] };
const CLIENTE = { id: 9, nombre: 'Juan Pérez', tipoIdentificacion: 'CC', numeroIdentificacion: '1002003000', ciudad: 'Neiva', telefono: '300', direccion: 'Cra 1', correo: 'j@p.co' };

beforeEach(() => {
  vi.clearAllMocks();
  api.inventario.reservas.mockResolvedValue({ contenido: [] });
});

describe('ProductoSelector', () => {
  it('FE-SEL-01 un código escaneado + Enter selecciona el producto sin abrir la lista', async () => {
    api.productos.porCodigo.mockResolvedValue(MARTILLO);
    api.productos.seleccion.mockResolvedValue([]);
    const onCambio = vi.fn();
    montar(<ProductoSelector valor={null} onCambio={onCambio} />);
    const campo = screen.getByLabelText(/producto/i);
    await userEvent.type(campo, '77010001{Enter}');
    await waitFor(() => expect(onCambio).toHaveBeenCalledWith(MARTILLO));
    expect(api.productos.porCodigo).toHaveBeenCalledWith('77010001');
  });
  it('FE-SEL-02 código inexistente avisa y no selecciona', async () => {
    api.productos.porCodigo.mockRejectedValue({ response: { status: 404, data: {} } });
    api.productos.seleccion.mockResolvedValue([]);
    const onCambio = vi.fn();
    montar(<ProductoSelector valor={null} onCambio={onCambio} />);
    await userEvent.type(screen.getByLabelText(/producto/i), '99999999{Enter}');
    expect(await screen.findByText(/no hay un producto activo con el código 99999999/i)).toBeInTheDocument();
    expect(onCambio).not.toHaveBeenCalled();
  });
  it('FE-SEL-03 la búsqueda muestra existencias y precio de cada opción', async () => {
    api.productos.seleccion.mockResolvedValue([MARTILLO]);
    montar(<ProductoSelector valor={null} onCambio={vi.fn()} />);
    await userEvent.type(screen.getByLabelText(/producto/i), 'mart');
    const opcion = await screen.findByRole('option');
    expect(within(opcion).getByText(/Martillo Stanley 16oz/)).toBeInTheDocument();
    expect(within(opcion).getByText(/Disponible: 5 unidad/)).toBeInTheDocument();
    expect(api.productos.seleccion).toHaveBeenCalledWith('mart', 15);
  });
});

describe('VentaNuevaPage (RF-04)', () => {
  const llenar = async () => {
    api.terceros.seleccion.mockResolvedValue([CLIENTE]);
    api.productos.porCodigo.mockResolvedValue(MARTILLO);
    api.productos.seleccion.mockResolvedValue([]);
    montar(<VentaNuevaPage />, { rol: 'Ventas', ruta: '/ventas/nueva', patron: '/ventas/nueva', extra: [<Route key="d" path="/ventas/:id" element={<p>DETALLE</p>} />] });
    await userEvent.type(screen.getByLabelText(/^cliente/i), 'juan');
    await userEvent.click(await screen.findByText('Juan Pérez'));
    const producto = screen.getByLabelText(/^producto/i);
    await userEvent.type(producto, '77010001{Enter}');
    await screen.findByText(/Martillo Stanley 16oz/);
    await userEvent.type(producto, '77010001{Enter}');   // segundo escaneo: suma una unidad
    await waitFor(() => expect(screen.getByLabelText('Cantidad 1')).toHaveValue('2'));
  };

  it('FE-VEN-01 sin cliente ni líneas muestra errores y no llama a la API', async () => {
    montar(<VentaNuevaPage />, { rol: 'Ventas' });
    await userEvent.click(screen.getByRole('button', { name: /registrar venta/i }));
    expect(screen.getAllByText('Campo obligatorio').length).toBeGreaterThan(0);
    expect(screen.getByText('Agregue al menos una línea')).toBeInTheDocument();
    expect(api.ventas.crear).not.toHaveBeenCalled();
  });

  it('FE-VEN-02 vista previa con descuento e IVA y envío con clave de idempotencia', async () => {
    api.ventas.crear.mockResolvedValue({ id: 55, numeroInterno: 'V-55' });
    await llenar();
    await userEvent.type(screen.getByLabelText('Desc. (%) 1'), '10');
    // 2 × 10 000 = 20 000; −10 % = 18 000; IVA 19 % = 3 420; total 21 420
    expect(screen.getByTestId('venta-total')).toHaveTextContent(/21\.420/);
    await userEvent.click(screen.getByRole('button', { name: /registrar venta/i }));
    const dialogo = await screen.findByRole('dialog');
    expect(within(dialogo).getByText(/Juan Pérez/)).toBeInTheDocument();
    await userEvent.click(within(dialogo).getByRole('button', { name: /registrar venta/i }));
    await screen.findByText('DETALLE');
    const cuerpo = api.ventas.crear.mock.calls[0][0];
    expect(cuerpo).toMatchObject({ clienteId: 9, lineas: [{ productoId: 1, cantidad: 2, porcentajeDescuento: 10 }] });
    expect(cuerpo.idOperacion).toMatch(/^[A-Za-z0-9_-]{8,64}$/);
    expect(cuerpo).not.toHaveProperty('impuestos');   // los importes los calcula el servidor
  });

  it('FE-VEN-03 un error del servidor se muestra y el reintento conserva la misma clave de operación', async () => {
    api.ventas.crear.mockRejectedValueOnce({ response: { data: { mensaje: 'Saldo insuficiente para Martillo' } } });
    await llenar();
    await userEvent.click(screen.getByRole('button', { name: /registrar venta/i }));
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: /registrar venta/i }));
    expect(await screen.findByText('Saldo insuficiente para Martillo')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());   // el diálogo terminó de cerrarse
    api.ventas.crear.mockResolvedValueOnce({ id: 56, numeroInterno: 'V-56' });
    await userEvent.click(screen.getByRole('button', { name: /registrar venta/i }));
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: /registrar venta/i }));
    await screen.findByText('DETALLE');
    expect(api.ventas.crear.mock.calls[1][0].idOperacion).toBe(api.ventas.crear.mock.calls[0][0].idOperacion);
  });

  it('FE-VEN-04 sin conexión el registro queda deshabilitado (RN-10)', () => {
    const original = Object.getOwnPropertyDescriptor(window.navigator, 'onLine');
    Object.defineProperty(window.navigator, 'onLine', { configurable: true, get: () => false });
    try {
      montar(<VentaNuevaPage />, { rol: 'Ventas' });
      expect(screen.getByRole('button', { name: /registrar venta/i })).toBeDisabled();
      expect(screen.getByText(/sin conexión no se pueden registrar ventas/i)).toBeInTheDocument();
    } finally {
      if (original) Object.defineProperty(window.navigator, 'onLine', original); else delete window.navigator.onLine;
    }
  });
});

describe('ComprobantePage (RF-19)', () => {
  const base = {
    empresa: { nombre: 'Ferretería Demo', nit: '900' }, cliente: { nombre: 'Juan Pérez' }, usuarioNombre: 'Laura',
    venta: { id: 7, numeroInterno: 'V-7', estado: 'ACTIVO', fecha: '2026-10-16T15:00:00', subtotal: 100, totalDescuentos: 0, totalImpuestos: 0, total: 100,
      factura: { numeracionDian: 'FE-1', validaFiscalmente: false }, lineas: [{ id: 1, productoId: 1, producto: 'Martillo', cantidad: 1, precioAplicado: 100, descuento: 0, impuesto: 0, total: 100 }] },
  };
  it('FE-CMP-01 muestra la leyenda de comprobante interno y permite imprimir y descargar', async () => {
    api.ventas.detalle.mockResolvedValue({ ...base, exportable: true, motivosNoExportable: [] });
    montar(<ComprobantePage />, { ruta: '/ventas/7/comprobante', patron: '/ventas/:id/comprobante' });
    expect(await screen.findByTestId('leyenda-comprobante')).toHaveTextContent(/no constituye factura electrónica/i);
    expect(screen.getByRole('heading', { name: /comprobante interno de venta/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /descargar pdf/i })).toBeEnabled();
    expect(screen.getByRole('button', { name: /imprimir/i })).toBeEnabled();
  });
  it('FE-CMP-02 un documento no exportable bloquea impresión y PDF y explica los motivos', async () => {
    api.ventas.detalle.mockResolvedValue({ ...base, venta: { ...base.venta, estado: 'ANULADO' }, exportable: false, motivosNoExportable: ['La venta está anulada.'] });
    montar(<ComprobantePage />, { ruta: '/ventas/7/comprobante', patron: '/ventas/:id/comprobante' });
    expect(await screen.findByText('La venta está anulada.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /descargar pdf/i })).toBeDisabled();
    expect(screen.getByRole('button', { name: /imprimir/i })).toBeDisabled();
  });
});
