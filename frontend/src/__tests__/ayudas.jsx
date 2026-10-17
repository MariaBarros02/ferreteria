/*
 * Nombre: ayudas.jsx
 * Descripción: Utilidades de prueba: monta componentes con sesión de un rol, enrutador en memoria y notificaciones.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../auth/AuthContext';
import { guardarSesion } from '../auth/session';
import { NotifierProvider } from '../components/Notifier';

/**
 * Monta `elemento` en `ruta` con una sesión del rol indicado.
 * @param {JSX.Element} elemento componente
 * @param {{rol?:string, correo?:string, ruta?:string, patron?:string, extra?:JSX.Element[]}} opciones
 */
export function montar(elemento, { rol = 'Administrador', correo = 'yo@ferreteria.com', ruta = '/', patron = '*', extra = [] } = {}) {
  guardarSesion({ token: 't', expiraEnSegundos: 3600, usuario: { nombre: 'Yo', correo, rol } }, false);
  return render(
    <NotifierProvider>
      <MemoryRouter initialEntries={[ruta]}>
        <AuthProvider>
          <Routes>
            <Route path={patron} element={elemento} />
            {extra}
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </NotifierProvider>,
  );
}
