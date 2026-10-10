/*
 * Nombre: AuthContext.jsx
 * Descripción: Contexto de autenticación: login, logout, sesión persistente y renovación automática del token antes de expirar.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { api, EVENTO_SESION_EXPIRADA } from '../api/http';
import { borrarSesion, guardarSesion, leerSesion } from './session';

const AuthContext = createContext(null);

/**
 * Proveedor de autenticación.
 * @param {{children: import('react').ReactNode}} props - Hijos.
 * @returns {JSX.Element} Proveedor.
 */
export function AuthProvider({ children }) {
  const [sesion, setSesion] = useState(() => leerSesion());
  const [expirada, setExpirada] = useState(false);
  const temporizador = useRef(null);

  const cerrar = useCallback(() => {
    borrarSesion();
    setSesion(null);
  }, []);

  /** Inicia sesión y persiste según "recordar". */
  const login = useCallback(async (correo, password, recordar) => {
    const datos = await api.login({ correo, password });
    guardarSesion(datos, recordar);
    setExpirada(false);
    setSesion(leerSesion());
  }, []);

  // Renovación automática al 80 % de la vigencia (permanencia mientras haya actividad del navegador abierto).
  useEffect(() => {
    clearTimeout(temporizador.current);
    if (!sesion) return undefined;
    const restante = sesion.expiraEn - Date.now();
    temporizador.current = setTimeout(async () => {
      try {
        const datos = await api.refresh();
        guardarSesion(datos, sesion.recordar);
        setSesion(leerSesion());
      } catch {
        cerrar();
      }
    }, Math.max(restante * 0.8, 1000));
    return () => clearTimeout(temporizador.current);
  }, [sesion, cerrar]);

  useEffect(() => {
    const alExpirar = () => { setSesion(null); setExpirada(true); };
    window.addEventListener(EVENTO_SESION_EXPIRADA, alExpirar);
    return () => window.removeEventListener(EVENTO_SESION_EXPIRADA, alExpirar);
  }, []);

  const valor = useMemo(() => ({
    usuario: sesion?.usuario ?? null,
    autenticado: Boolean(sesion),
    sesionExpirada: expirada,
    rol: sesion?.usuario?.rol ?? null,
    login,
    logout: cerrar,
  }), [sesion, expirada, login, cerrar]);

  return <AuthContext.Provider value={valor}>{children}</AuthContext.Provider>;
}

/**
 * Hook de acceso al contexto de autenticación.
 * @returns {{usuario:object|null, autenticado:boolean, sesionExpirada:boolean, rol:string|null, login:Function, logout:Function}} Contexto.
 */
export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth debe usarse dentro de <AuthProvider>');
  return ctx;
}
