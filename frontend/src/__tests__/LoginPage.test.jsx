/*
 * Nombre: LoginPage.test.jsx
 * Descripción: Pruebas de componente de LoginPage (Testing Library): validación, accesibilidad, éxito y error de credenciales.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

vi.mock('../api/http', () => ({
  api: { login: vi.fn(), refresh: vi.fn() },
  EVENTO_SESION_EXPIRADA: 'auth:expirada',
}));

import { api } from '../api/http';
import { AuthProvider } from '../auth/AuthContext';
import LoginPage from '../pages/LoginPage';

function montar() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<p>INICIO</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('LoginPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('FE-LOG-01 los campos tienen etiqueta accesible y el formulario un nombre', () => {
    montar();
    expect(screen.getByLabelText(/correo electrónico/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^contraseña/i)).toHaveAttribute('type', 'password');
    expect(screen.getByRole('form', { name: /iniciar sesión/i })).toBeInTheDocument();
  });

  it('FE-LOG-02 enviar vacío muestra errores y no llama a la API', async () => {
    montar();
    await userEvent.click(screen.getByRole('button', { name: /entrar/i }));
    expect((await screen.findAllByText('Campo obligatorio')).length).toBe(2);
    expect(api.login).not.toHaveBeenCalled();
  });

  it('FE-LOG-03 correo inválido muestra mensaje de formato', async () => {
    montar();
    await userEvent.type(screen.getByLabelText(/correo electrónico/i), 'no-es-correo');
    await userEvent.click(screen.getByRole('button', { name: /entrar/i }));
    expect(await screen.findByText('Correo electrónico inválido')).toBeInTheDocument();
  });

  it('FE-LOG-04 login correcto guarda la sesión persistente y navega al inicio', async () => {
    api.login.mockResolvedValue({ token: 'jwt', tipo: 'Bearer', expiraEnSegundos: 1800, usuario: { nombre: 'Ana', correo: 'a@b.com', rol: 'Ventas' } });
    montar();
    await userEvent.type(screen.getByLabelText(/correo electrónico/i), 'a@b.com');
    await userEvent.type(screen.getByLabelText(/^contraseña/i), 'Clave-123');
    await userEvent.click(screen.getByLabelText(/mantener la sesión/i));
    await userEvent.click(screen.getByRole('button', { name: /entrar/i }));
    expect(await screen.findByText('INICIO')).toBeInTheDocument();
    expect(api.login).toHaveBeenCalledWith({ correo: 'a@b.com', password: 'Clave-123' });
    expect(localStorage.getItem('sesion-inventario')).toContain('"recordar":true');
  });

  it('FE-LOG-05 credenciales inválidas muestran alerta accesible y no guardan sesión', async () => {
    api.login.mockRejectedValue({ response: { status: 401, data: { codigo: 'CREDENCIALES_INVALIDAS' } } });
    montar();
    await userEvent.type(screen.getByLabelText(/correo electrónico/i), 'a@b.com');
    await userEvent.type(screen.getByLabelText(/^contraseña/i), 'mala');
    await userEvent.click(screen.getByRole('button', { name: /entrar/i }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Credenciales inválidas');
    await waitFor(() => expect(localStorage.getItem('sesion-inventario')).toBeNull());
  });

  it('FE-LOG-06 el botón mostrar/ocultar contraseña alterna el tipo del campo', async () => {
    montar();
    const campo = screen.getByLabelText(/^contraseña/i);
    await userEvent.click(screen.getByRole('button', { name: /mostrar contraseña/i }));
    expect(campo).toHaveAttribute('type', 'text');
  });
});
