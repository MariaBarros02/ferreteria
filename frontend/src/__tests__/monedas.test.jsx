/*
 * Nombre: monedas.test.jsx
 * Descripción: Pruebas de monedas y tasas de cambio (RF-23): conversión de precios, formato por moneda, selector de moneda, venta en USD, registro de tasas con permisos y validación, y comprobante con equivalente en COP.
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
    monedas: { listar: vi.fn(), tasas: vi.fn(), registrarTasa: vi.fn(), convertir: vi.fn(), crear: vi.fn(), cambiarEstado: vi.fn() },
  },
}));

import { api } from '../api/http';
import MonedasPage, { errorCodigo, errorTasa } from '../pages/admin/MonedasPage';
import VentaNuevaPage from '../pages/ventas/VentaNuevaPage';
import ComprobantePage from '../pages/ventas/ComprobantePage';
import { moneda, tasaTexto } from '../utils/formato';
import { enCop, precioEnMoneda } from '../utils/totales';
import { montar } from './ayudas';

const COP = { codigo: 'COP', nombre: 'Peso colombiano', simbolo: '$', decimales: 0, activa: true, funcional: true, tasaVigente: 1 };
const USD = { codigo: 'USD', nombre: 'Dólar estadounidense', simbolo: 'US$', decimales: 2, activa: true, funcional: false, tasaVigente: 4000, fechaTasa: '2026-10-16', fuenteTasa: 'TRM' };
const EUR = { codigo: 'EUR', nombre: 'Euro', simbolo: '€', decimales: 2, activa: true, funcional: false, tasaVigente: null };

beforeEach(() => {
  vi.clearAllMocks();
  api.monedas.listar.mockResolvedValue([COP, EUR, USD]);
  api.inventario.reservas.mockResolvedValue({ contenido: [] });
});

describe('conversión y formato (RF-23)', () => {
  it('FE-MON-01 precio COP → moneda con 4 decimales, equivalente COP con 2 y formato por código ISO', () => {
    expect(precioEnMoneda(650000, 4000)).toBe(162.5);
    expect(precioEnMoneda(10000, 3)).toBe(3333.3333);
    expect(precioEnMoneda(1234.5, 1)).toBe(1234.5);
    expect(precioEnMoneda(null, 4000)).toBeNull();
    expect(enCop(386.75, 4000)).toBe(1547000);
    expect(moneda(12.5, undefined, 'USD')).toMatch(/12,50/);
    expect(moneda(12.5, undefined, 'USD')).toMatch(/US\$|USD/);
    expect(moneda(1500)).toMatch(/1\.500/);
    expect(tasaTexto('USD', 4000)).toMatch(/^1 USD = 4\.000,00 COP$/);
  });
  it('FE-MON-02 validación de tasa y código ISO', () => {
    expect(errorTasa('')).toBe('errores.obligatorio');
    expect(errorTasa('0')).toBe('moneda.tasaInvalida');
    expect(errorTasa('4000,1234567')).toBe('moneda.tasaInvalida');
    expect(errorTasa('4123,45')).toBeNull();
    expect(errorCodigo('US')).toBe('moneda.codigoInvalido');
    expect(errorCodigo('clp')).toBeNull();
  });
});

describe('venta en otra moneda', () => {
  it('FE-MON-03 al elegir USD los precios y el total se muestran en USD, con equivalente en COP, y se envía la moneda', async () => {
    api.terceros.seleccion.mockResolvedValue([{ id: 9, nombre: 'Juan Pérez', tipoIdentificacion: 'CC', numeroIdentificacion: '1' }]);
    api.productos.seleccion.mockResolvedValue([]);
    api.productos.porCodigo.mockResolvedValue({ id: 1, nombre: 'Taladro', marca: 'Bosch', presentacion: '500W', codigoBarras: '77010001',
      unidadBase: 'unidad', tipoRastreo: 'NINGUNO', activo: true, porcentajeIva: 19, precioVigente: 650000, disponible: 5, unidades: [] });
    api.ventas.crear.mockResolvedValue({ id: 3, numeroInterno: 'V-3' });
    montar(<VentaNuevaPage />, { rol: 'Ventas', ruta: '/ventas/nueva', patron: '/ventas/nueva', extra: [<Route key="d" path="/ventas/:id" element={<p>DETALLE</p>} />] });
    await userEvent.click(screen.getByLabelText(/moneda del documento/i));
    expect(await screen.findByRole('option', { name: /EUR/ })).toHaveAttribute('aria-disabled', 'true');   // sin tasa
    await userEvent.click(screen.getByRole('option', { name: /USD/ }));
    await userEvent.type(screen.getByLabelText(/^cliente/i), 'juan');
    await userEvent.click(await screen.findByText('Juan Pérez'));
    const producto = screen.getByLabelText(/^producto/i);
    await userEvent.type(producto, '77010001{Enter}');
    await screen.findByText(/Taladro Bosch 500W/);
    // 650.000 / 4.000 = 162,50 USD + IVA 19 % = 193,38 USD ≈ 773.520 COP
    expect(screen.getByTestId('venta-total')).toHaveTextContent(/193,38/);
    expect(screen.getByText(/773\.520/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /registrar venta/i }));
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: /registrar venta/i }));
    await screen.findByText('DETALLE');
    expect(api.ventas.crear.mock.calls[0][0]).toMatchObject({ moneda: 'USD', clienteId: 9 });
  });
});

describe('MonedasPage', () => {
  it('FE-MON-04 el rol Ventas consulta tasas pero no puede registrarlas ni desactivar monedas', async () => {
    montar(<MonedasPage />, { rol: 'Ventas' });
    expect(await screen.findByText(/1 USD = 4\.000,00 COP/)).toBeInTheDocument();
    expect(screen.getByText('Sin tasa vigente')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /registrar tasa/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /nueva moneda/i })).toBeNull();
  });
  it('FE-MON-05 Gerencia registra una tasa: fecha futura bloqueada; valor válido se envía como número', async () => {
    api.monedas.registrarTasa.mockResolvedValue({ id: 1 });
    montar(<MonedasPage />, { rol: 'Gerencia' });
    const fila = (await screen.findByText(/USD · Dólar/)).closest('tr');
    await userEvent.click(within(fila).getByRole('button', { name: /registrar tasa/i }));
    const dialogo = await screen.findByRole('dialog');
    await userEvent.type(within(dialogo).getByLabelText(/COP por 1 USD/i), '4123,45');
    const fechaCampo = within(dialogo).getByLabelText(/fecha de vigencia/i);
    await userEvent.clear(fechaCampo);
    await userEvent.type(fechaCampo, '2999-01-01');
    await userEvent.click(within(dialogo).getByRole('button', { name: /registrar tasa/i }));
    expect(within(dialogo).getByText(/no puede ser posterior a hoy/i)).toBeInTheDocument();
    expect(api.monedas.registrarTasa).not.toHaveBeenCalled();
    await userEvent.clear(fechaCampo);
    await userEvent.type(fechaCampo, '2026-01-02');
    await userEvent.type(within(dialogo).getByLabelText(/fuente/i), 'TRM');
    await userEvent.click(within(dialogo).getByRole('button', { name: /registrar tasa/i }));
    await waitFor(() => expect(api.monedas.registrarTasa).toHaveBeenCalledWith('USD', { tasa: 4123.45, fechaVigencia: '2026-01-02', fuente: 'TRM' }));
  });
  it('FE-MON-06 el conversor muestra el resultado con las tasas usadas', async () => {
    api.monedas.convertir.mockResolvedValue({ monto: 100, desde: 'USD', hacia: 'COP', resultado: 400000, tasaDesde: 4000, tasaHacia: 1, fecha: '2026-10-16' });
    montar(<MonedasPage />, { rol: 'Ventas' });
    await screen.findByText(/1 USD = 4\.000,00 COP/);
    await userEvent.click(screen.getByRole('button', { name: /^convertir$/i }));
    expect(await screen.findByTestId('resultado-conversion')).toHaveTextContent(/400\.000/);
    expect(api.monedas.convertir).toHaveBeenCalledWith(100, 'USD', 'COP');
  });
});

describe('comprobante en moneda extranjera', () => {
  it('FE-MON-07 muestra importes en USD, la tasa aplicada y el equivalente en COP', async () => {
    api.ventas.detalle.mockResolvedValue({
      empresa: { nombre: 'Ferretería' }, cliente: { nombre: 'Juan' }, usuarioNombre: 'Laura', exportable: true, motivosNoExportable: [],
      venta: { id: 7, numeroInterno: 'V-7', estado: 'ACTIVO', fecha: '2026-10-16T15:00:00', moneda: 'USD', tasaCambio: 4000,
        subtotal: 1300000, totalDescuentos: 0, totalImpuestos: 247000, total: 1547000,
        subtotalMoneda: 325, descuentosMoneda: 0, impuestosMoneda: 61.75, totalMoneda: 386.75,
        factura: { numeracionDian: 'FE-1', validaFiscalmente: false },
        lineas: [{ id: 1, productoId: 1, producto: 'Taladro', cantidad: 2, precioAplicado: 650000, precioMoneda: 162.5, descuento: 0, descuentoMoneda: 0,
          impuesto: 247000, impuestoMoneda: 61.75, total: 1547000, totalMoneda: 386.75 }] },
    });
    montar(<ComprobantePage />, { ruta: '/ventas/7/comprobante', patron: '/ventas/:id/comprobante' });
    expect(await screen.findByTestId('comprobante-total')).toHaveTextContent(/386,75/);
    expect(screen.getByTestId('comprobante-equivalente')).toHaveTextContent(/1\.547\.000/);
    expect(screen.getByText(/1 USD = 4\.000,00 COP/)).toBeInTheDocument();
  });
});
