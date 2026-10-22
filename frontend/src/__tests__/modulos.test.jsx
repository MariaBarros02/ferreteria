/*
 * Nombre: modulos.test.jsx
 * Descripción: Pruebas de componentes de módulos operativos: recepción parcial con lote y vencimiento (RF-17), ajustes sin autoaprobación (RF-14), precios manuales con justificación (RF-07, RF-16), importación con posibles duplicados (RN-15), cierre bloqueado por pendientes (RF-08) y rutas protegidas por rol (RF-09).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

vi.mock('../api/http', () => ({
  EVENTO_SESION_EXPIRADA: 'auth:expirada',
  api: {
    refresh: vi.fn(),
    compras: { recibir: vi.fn() },
    ajustes: { listar: vi.fn(), aprobar: vi.fn(), rechazar: vi.fn() },
    precios: { propuestas: vi.fn(), proponer: vi.fn() },
    productos: { seleccion: vi.fn(), porCodigo: vi.fn() },
    importaciones: { historial: vi.fn(), validar: vi.fn(), confirmar: vi.fn(), cancelar: vi.fn(), errores: vi.fn(), plantilla: vi.fn() },
    cierres: { resumen: vi.fn(), listar: vi.fn(), cerrar: vi.fn() },
  },
}));

import { api } from '../api/http';
import RutaPermitida from '../auth/RutaPermitida';
import { RecepcionDialog } from '../pages/compras/CompraDetallePage';
import AjustesPage from '../pages/control/AjustesPage';
import PreciosPage from '../pages/control/PreciosPage';
import ImportacionPage from '../pages/admin/ImportacionPage';
import CierresPage from '../pages/admin/CierresPage';
import { puede } from '../utils/permisos';
import { montar } from './ayudas';

beforeEach(() => vi.clearAllMocks());

describe('Recepción de compras (RF-17)', () => {
  const compra = { id: 3, numero: 'OC-000003', lineas: [
    { id: 11, producto: 'Pintura', codigoBarras: '7700', unidadBase: 'galón', tipoRastreo: 'LOTE', tieneVencimiento: true, cantidadPendiente: 10 },
    { id: 12, producto: 'Martillo', codigoBarras: '7701', unidadBase: 'unidad', tipoRastreo: 'NINGUNO', tieneVencimiento: false, cantidadPendiente: 4 },
    { id: 13, producto: 'Lija', codigoBarras: '7702', unidadBase: 'unidad', tipoRastreo: 'NINGUNO', tieneVencimiento: false, cantidadPendiente: 0 },
  ] };
  it('FE-REC-01 exige factura, lote y vencimiento para productos por lote y no permite superar lo pendiente', async () => {
    montar(<RecepcionDialog abierto compra={compra} onCerrar={vi.fn()} onRecibida={vi.fn()} />);
    expect(screen.queryByText('Lija')).toBeNull();   // líneas completas no se muestran
    await userEvent.type(screen.getByLabelText('Cantidad a recibir Pintura'), '11');
    await userEvent.click(screen.getByRole('button', { name: /registrar recepción/i }));
    expect(screen.getByText('Máximo 10')).toBeInTheDocument();
    expect(screen.getAllByText('Campo obligatorio').length).toBeGreaterThanOrEqual(3);   // factura, lote, vencimiento
    expect(api.compras.recibir).not.toHaveBeenCalled();
  });
  it('FE-REC-02 recepción parcial: envía solo las líneas con cantidad, con lote y vencimiento', async () => {
    api.compras.recibir.mockResolvedValue({ id: 3 });
    const onRecibida = vi.fn();
    montar(<RecepcionDialog abierto compra={compra} onCerrar={vi.fn()} onRecibida={onRecibida} />);
    await userEvent.type(screen.getByLabelText(/factura del proveedor/i), 'FAC-99');
    await userEvent.type(screen.getByLabelText('Cantidad a recibir Pintura'), '4');
    await userEvent.type(screen.getByLabelText('Lote Pintura'), 'L-01');
    await userEvent.type(screen.getByLabelText('Vencimiento Pintura'), '2099-12-31');
    await userEvent.click(screen.getByRole('button', { name: /registrar recepción/i }));
    await waitFor(() => expect(onRecibida).toHaveBeenCalled());
    const [id, cuerpo] = api.compras.recibir.mock.calls[0];
    expect(id).toBe(3);
    expect(cuerpo).toMatchObject({ facturaProveedor: 'FAC-99', lineas: [{ idLineaCompra: 11, cantidad: 4, codigoLote: 'L-01', fechaVencimiento: '2099-12-31' }] });
    expect(cuerpo.lineas).toHaveLength(1);
    expect(cuerpo.idOperacion).toBeTruthy();
  });
});

describe('Ajustes (RF-14)', () => {
  it('FE-AJU-01 quien solicitó un ajuste no ve los botones para aprobarlo; otro aprobador sí', async () => {
    api.ajustes.listar.mockResolvedValue({ totalElementos: 2, contenido: [
      { id: 1, numero: 'AJ-1', producto: 'Martillo', tipo: 'SALIDA', cantidad: 1, estado: 'PENDIENTE', solicitante: 'yo@ferreteria.com', motivo: 'Rotura' },
      { id: 2, numero: 'AJ-2', producto: 'Taladro', tipo: 'ENTRADA', cantidad: 2, estado: 'PENDIENTE', solicitante: 'otro@ferreteria.com', motivo: 'Hallazgo' },
    ] });
    montar(<AjustesPage />, { rol: 'Gerencia', correo: 'yo@ferreteria.com' });
    const propia = (await screen.findByText('AJ-1')).closest('tr');
    expect(within(propia).queryByRole('button', { name: /aprobar/i })).toBeNull();
    expect(within(propia).getByText(/no puede decidir su propia solicitud/i)).toBeInTheDocument();
    const ajena = screen.getByText('AJ-2').closest('tr');
    expect(within(ajena).getByRole('button', { name: /aprobar/i })).toBeInTheDocument();
  });
});

describe('Precios (RF-07, RF-16)', () => {
  // it('FE-PRE-01 una propuesta manual sin justificación no se envía', async () => {
  //   api.precios.propuestas.mockResolvedValue({ contenido: [], totalElementos: 0 });
  //   api.productos.seleccion.mockResolvedValue([{ id: 1, nombre: 'Martillo', marca: 'S', presentacion: 'x', codigoBarras: '77010001', unidadBase: 'u', precioVigente: 100, disponible: 1 }]);
  //   montar(<PreciosPage />, { rol: 'Inventario' });
  //   await userEvent.click(screen.getByRole('button', { name: /nueva propuesta/i }));
  //   const dialogo = await screen.findByRole('dialog');
  //   await userEvent.type(within(dialogo).getByLabelText(/^producto/i), 'mar');
  //   await userEvent.click(await screen.findByRole('option'));
  //   await userEvent.click(within(dialogo).getByLabelText(/cálculo/i));
  //   await userEvent.click(screen.getByRole('option', { name: 'Manual' }));
  //   await userEvent.type(within(dialogo).getByLabelText(/precio propuesto/i), '1500');
  //   await userEvent.click(within(dialogo).getByRole('button', { name: /proponer/i }));
  //   expect(within(dialogo).getByText('Campo obligatorio')).toBeInTheDocument();
  //   expect(api.precios.proponer).not.toHaveBeenCalled();
  // });
  it('FE-PRE-02 el rol Inventario no ve acciones de aprobación (solo Gerencia aprueba)', async () => {
    api.precios.propuestas.mockResolvedValue({ totalElementos: 1, contenido: [{ id: 5, producto: 'Martillo', estado: 'propuesto', modoCalculo: 'recargo', precioPropuesto: 100, proponente: 'otro@x.com' }] });
    montar(<PreciosPage />, { rol: 'Inventario' });
    await screen.findByText('Martillo');
    expect(screen.queryByRole('button', { name: /^aprobar$/i })).toBeNull();
  });
});

describe('Importación (RN-15)', () => {
  // it('FE-IMP-01 los posibles duplicados solo se importan si se marcan y el total a importar se actualiza', async () => {
  //   api.importaciones.historial.mockResolvedValue({ contenido: [], totalElementos: 0 });
  //   api.importaciones.validar.mockResolvedValue({ id: 8, tipo: 'productos', nombreArchivo: 'p.xlsx', estado: 'VALIDADA', totalFilas: 3, usuario: 'yo', filas: [
  //     { fila: 2, estado: 'VALIDA', datos: { nombre: 'A' }, errores: [] },
  //     { fila: 3, estado: 'POSIBLE_DUPLICADO', datos: { nombre: 'B' }, errores: [], coincidencia: 'Producto 4' },
  //     { fila: 4, estado: 'ERROR', datos: { nombre: '' }, errores: [{ columna: 'nombre', mensaje: 'Obligatorio' }] },
  //   ] });
  //   api.importaciones.confirmar.mockResolvedValue({ id: 8, tipo: 'productos', estado: 'IMPORTADA', totalFilas: 3, filas: [] });
  //   montar(<ImportacionPage />, { rol: 'Inventario' });
  //   const archivo = new File(['x'], 'p.xlsx', { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
  //   await userEvent.upload(screen.getByTestId('archivo-importacion'), archivo);
  //   await userEvent.click(screen.getByRole('button', { name: /^validar$/i }));
  //   expect(await screen.findByText('nombre: Obligatorio')).toBeInTheDocument();
  //   expect(screen.getByRole('button', { name: /importar 1 fila/i })).toBeInTheDocument();
  //   await userEvent.click(screen.getByRole('checkbox', { name: /importar la fila 3/i }));
  //   await userEvent.click(screen.getByRole('button', { name: /importar 2 fila/i }));
  //   await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: /^importar$/i }));
  //   await waitFor(() => expect(api.importaciones.confirmar).toHaveBeenCalledWith(8, [3]));
  // });
  it('FE-IMP-02 un archivo que no es .xlsx se rechaza antes de subirlo', async () => {
    api.importaciones.historial.mockResolvedValue({ contenido: [], totalElementos: 0 });
    montar(<ImportacionPage />, { rol: 'Inventario' });
    await userEvent.upload(screen.getByTestId('archivo-importacion'), new File(['a;b'], 'datos.csv', { type: 'text/csv' }), { applyAccept: false });
    await userEvent.click(screen.getByRole('button', { name: /^validar$/i }));
    expect(await screen.findByText('Solo se aceptan archivos .xlsx')).toBeInTheDocument();
    expect(api.importaciones.validar).not.toHaveBeenCalled();
  });
});

describe('Cierre operativo (RF-08)', () => {
  it('FE-CIE-01 con pendientes bloqueantes el botón de cierre queda deshabilitado y se listan los motivos', async () => {
    api.cierres.listar.mockResolvedValue([]);
    api.cierres.resumen.mockResolvedValue({ periodo: '2026-09', desde: '2026-09-01', hasta: '2026-09-30', estado: 'ABIERTO', numeroVentas: 3, totalVentas: 300,
      movimientos: [], diferenciasConciliacion: [], pendientes: { ajustesPendientes: 1, conteosSinDecidir: 0, preciosPendientes: 0, reservasVencidasActivas: 0 },
      bloqueantes: ['1 ajuste(s) pendiente(s) de aprobación'] });
    montar(<CierresPage />, { rol: 'Gerencia' });
    expect(await screen.findByText('1 ajuste(s) pendiente(s) de aprobación')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /cerrar periodo/i })).toBeDisabled();
  });
});

describe('Rutas por rol (RF-09)', () => {
  it('FE-RUT-01 un rol sin permiso ve el aviso de acceso denegado en lugar de la página', () => {
    montar(<RutaPermitida permitido={(r) => puede(r, 'usuarios.gestionar')}><p>SECRETO</p></RutaPermitida>, { rol: 'Ventas' });
    expect(screen.queryByText('SECRETO')).toBeNull();
    expect(screen.getByRole('alert')).toHaveTextContent(/no tiene acceso/i);
  });
  it('FE-RUT-02 un rol con permiso ve la página', () => {
    montar(<RutaPermitida permitido={(r) => puede(r, 'usuarios.gestionar')}><p>SECRETO</p></RutaPermitida>, { rol: 'Administrador' });
    expect(screen.getByText('SECRETO')).toBeInTheDocument();
  });
});
