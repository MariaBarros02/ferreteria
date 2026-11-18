/*
 * Nombre: asistente.test.jsx
 * Descripción: Pruebas del asistente virtual en la interfaz: bienvenida al abrir, preguntas, temas sugeridos, enlaces, errores, modo sin conexión, reinicio y acceso desde la barra superior.
 * Fecha de creación: 2026-11-17
 * Fecha de actualización: 2026-11-17
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useLocation } from 'react-router-dom';

vi.mock('../api/http', () => ({
  EVENTO_SESION_EXPIRADA: 'auth:expirada',
  api: {
    refresh: vi.fn(),
    catalogo: vi.fn(),
    asistente: { inicio: vi.fn(), enviar: vi.fn() },
  },
}));
vi.mock('../offline/catalogo', () => ({
  sincronizarCatalogo: vi.fn(() => Promise.resolve()),
  borrarCatalogo: vi.fn(() => Promise.resolve()),
}));

import { api } from '../api/http';
import AsistenteVirtual, { MAX_PREGUNTA } from '../components/AsistenteVirtual';
import Layout from '../components/Layout';
import { montar } from './ayudas';

const BIENVENIDA = {
  tipo: 'BIENVENIDA', nodo: 'saludo', texto: '¡Hola! Soy el asistente virtual de la ferretería.',
  opciones: [{ id: 'ventas', titulo: 'Ventas' }, { id: 'cuenta', titulo: 'Mi cuenta y la aplicación' }],
};
const REGISTRAR = {
  tipo: 'RESPUESTA', nodo: 'venta-registrar', texto: '1. Abre Ventas → Nueva venta.\n2. Busca y elige el cliente.',
  opciones: [], enlace: { ruta: '/ventas/nueva', texto: 'Abrir Nueva venta' },
};

/** Muestra la ruta actual para comprobar la navegación de los enlaces. */
function Ruta() {
  return <span data-testid="ruta">{useLocation().pathname}</span>;
}

function abrirPanel(props = {}) {
  const onCerrar = vi.fn();
  montar(<><AsistenteVirtual abierto onCerrar={onCerrar} {...props} /><Ruta /></>, { rol: 'Ventas' });
  return onCerrar;
}

const conversacion = () => screen.getByRole('log', { name: /conversación con el asistente/i });

beforeEach(() => {
  vi.clearAllMocks();
  api.asistente.inicio.mockResolvedValue(BIENVENIDA);
  api.asistente.enviar.mockResolvedValue(REGISTRAR);
  Object.defineProperty(navigator, 'onLine', { configurable: true, value: true });
});

describe('Asistente virtual', () => {
  it('FE-ASI-01 al abrirse pide la bienvenida una sola vez y muestra los temas como botones', async () => {
    abrirPanel();
    expect(screen.getByRole('heading', { name: 'Asistente virtual' })).toBeInTheDocument();
    expect(await within(conversacion()).findByText(BIENVENIDA.texto)).toBeInTheDocument();
    const temas = screen.getByRole('group', { name: /temas sugeridos/i });
    expect(within(temas).getByRole('button', { name: 'Ventas' })).toBeInTheDocument();
    expect(within(temas).getByRole('button', { name: 'Mi cuenta y la aplicación' })).toBeInTheDocument();
    expect(api.asistente.inicio).toHaveBeenCalledTimes(1);
  });

  it('FE-ASI-02 envía la pregunta escrita, limpia el campo y muestra la respuesta con su enlace', async () => {
    const u = userEvent.setup();
    abrirPanel();
    await within(conversacion()).findByText(BIENVENIDA.texto);
    const enviar = screen.getByRole('button', { name: /enviar pregunta/i });
    expect(enviar).toBeDisabled();
    const campo = screen.getByLabelText(/escribe tu pregunta/i);
    const pregunta = '  ¿Cómo registro una venta?  ';
    await u.type(campo, pregunta);
    expect(screen.getByText(`${pregunta.length}/${MAX_PREGUNTA}`)).toBeInTheDocument();
    await u.click(enviar);
    expect(api.asistente.enviar).toHaveBeenCalledWith({ texto: '¿Cómo registro una venta?' });
    expect(within(conversacion()).getByText('¿Cómo registro una venta?')).toBeInTheDocument();
    expect(campo).toHaveValue('');
    expect(await within(conversacion()).findByText(/1\. Abre Ventas → Nueva venta\./)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Abrir Nueva venta' })).toHaveAttribute('href', '/ventas/nueva');
  });

  it('FE-ASI-03 elegir un tema envía su id y muestra su título como mensaje del usuario', async () => {
    const u = userEvent.setup();
    abrirPanel();
    await u.click(await screen.findByRole('button', { name: 'Ventas' }));
    expect(api.asistente.enviar).toHaveBeenCalledWith({ nodo: 'ventas' });
    const usuario = screen.getAllByTestId('mensaje-usuario');
    expect(usuario).toHaveLength(1);
    expect(usuario[0]).toHaveTextContent('Ventas');
    expect(await within(conversacion()).findByText(/Abre Ventas/)).toBeInTheDocument();
  });

  it('FE-ASI-04 el enlace navega a la pantalla y cierra el panel', async () => {
    const u = userEvent.setup();
    const onCerrar = abrirPanel();
    await u.type(await screen.findByLabelText(/escribe tu pregunta/i), 'registrar venta{Enter}');
    await u.click(await screen.findByRole('link', { name: 'Abrir Nueva venta' }));
    expect(screen.getByTestId('ruta')).toHaveTextContent('/ventas/nueva');
    expect(onCerrar).toHaveBeenCalled();
  });

  it('FE-ASI-05 error del servidor y falta de conexión se muestran en la conversación', async () => {
    const u = userEvent.setup();
    api.asistente.enviar.mockRejectedValueOnce({ response: { data: { codigo: 'MENSAJE_VACIO', mensaje: 'Escriba una pregunta o elija un tema' } } });
    api.asistente.enviar.mockRejectedValueOnce({ code: 'OFFLINE' });
    abrirPanel();
    const campo = await screen.findByLabelText(/escribe tu pregunta/i);
    await u.type(campo, 'hola{Enter}');
    expect(await within(conversacion()).findByText('Escriba una pregunta o elija un tema')).toBeInTheDocument();
    await u.type(campo, 'hola otra vez{Enter}');
    expect(await within(conversacion()).findByText(/el asistente necesita conexión a internet/i)).toBeInTheDocument();
  });

  it('FE-ASI-06 sin conexión no pide la bienvenida, avisa y deshabilita la escritura', () => {
    Object.defineProperty(navigator, 'onLine', { configurable: true, value: false });
    abrirPanel();
    expect(api.asistente.inicio).not.toHaveBeenCalled();
    expect(screen.getByRole('status')).toHaveTextContent(/necesita conexión/i);
    expect(screen.getByLabelText(/escribe tu pregunta/i)).toBeDisabled();
  });

  it('FE-ASI-07 "Nueva conversación" borra los mensajes y vuelve a saludar; "Menú principal" pide el tema saludo', async () => {
    const u = userEvent.setup();
    abrirPanel();
    await u.click(await screen.findByRole('button', { name: 'Ventas' }));
    await within(conversacion()).findByText(/Abre Ventas/);
    await u.click(screen.getByRole('button', { name: 'Nueva conversación' }));
    await waitFor(() => expect(api.asistente.inicio).toHaveBeenCalledTimes(2));
    await within(conversacion()).findByText(BIENVENIDA.texto);
    expect(screen.queryAllByTestId('mensaje-usuario')).toHaveLength(0);
    await u.click(screen.getByRole('button', { name: 'Menú principal' }));
    expect(api.asistente.enviar).toHaveBeenLastCalledWith({ nodo: 'saludo' });
  });

  it('FE-ASI-08 la pregunta se limita a 300 caracteres', async () => {
    const u = userEvent.setup();
    abrirPanel();
    const campo = await screen.findByLabelText(/escribe tu pregunta/i);
    await u.click(campo);
    await u.paste('x'.repeat(MAX_PREGUNTA + 50));
    expect(campo.value).toHaveLength(MAX_PREGUNTA);
  });

  it('FE-ASI-09 el botón de la barra superior abre el asistente en cualquier pantalla', async () => {
    const u = userEvent.setup();
    montar(<Routes><Route element={<Layout onToggleTema={() => {}} />}><Route path="*" element={<p>Contenido</p>} /></Route></Routes>,
      { rol: 'Ventas' });
    expect(screen.queryByRole('heading', { name: 'Asistente virtual' })).not.toBeInTheDocument();
    await u.click(screen.getByRole('button', { name: /abrir el asistente virtual/i }));
    expect(await screen.findByRole('heading', { name: 'Asistente virtual' })).toBeInTheDocument();
    expect(await screen.findByText(BIENVENIDA.texto)).toBeInTheDocument();
    await u.click(screen.getByRole('button', { name: /cerrar el asistente/i }));
    await waitFor(() => expect(screen.queryByRole('heading', { name: 'Asistente virtual' })).not.toBeInTheDocument());
  });
});
