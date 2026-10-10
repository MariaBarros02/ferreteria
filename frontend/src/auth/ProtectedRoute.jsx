/*
 * Nombre: ProtectedRoute.jsx
 * Descripción: Ruta protegida: redirige a /login si no hay sesión; opcionalmente restringe por roles (la API es la autoridad final).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext';

/**
 * Protege rutas hijas.
 * @param {{roles?: string[]}} props - Roles permitidos (vacío = cualquier autenticado).
 * @returns {JSX.Element} Outlet o redirección.
 */
export default function ProtectedRoute({ roles }) {
  const { autenticado, rol } = useAuth();
  const ubicacion = useLocation();
  if (!autenticado) return <Navigate to="/login" replace state={{ desde: ubicacion }} />;
  if (roles && !roles.includes(rol)) return <Navigate to="/" replace />;
  return <Outlet />;
}
