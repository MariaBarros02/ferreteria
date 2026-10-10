/*
 * Nombre: App.jsx
 * Descripción: Componente raíz: tema (claro/oscuro con tokens), proveedores, enrutamiento con rutas protegidas.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { useMemo, useState } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { CssBaseline, ThemeProvider } from '@mui/material';
import { AuthProvider } from './auth/AuthContext';
import ProtectedRoute from './auth/ProtectedRoute';
import Layout from './components/Layout';
import { NotifierProvider } from './components/Notifier';
import LoginPage from './pages/LoginPage';
import ProductosPage from './pages/ProductosPage';
import TercerosPage from './pages/TercerosPage';
import VentasPage from './pages/VentasPage';
import { crearTema } from './theme/theme';

/**
 * Aplicación.
 * @returns {JSX.Element} Árbol de la SPA.
 */
export default function App() {
  const [modo, setModo] = useState(() => {
    try { return localStorage.getItem('tema') || (window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'); }
    catch { return 'light'; }
  });
  const tema = useMemo(() => crearTema(modo), [modo]);
  const alternar = () => setModo((m) => { const n = m === 'light' ? 'dark' : 'light'; try { localStorage.setItem('tema', n); } catch { /* sin almacenamiento */ } return n; });

  return (
    <ThemeProvider theme={tema}>
      <CssBaseline />
      <NotifierProvider>
        <BrowserRouter>
          <AuthProvider>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route element={<ProtectedRoute />}>
                <Route element={<Layout onToggleTema={alternar} />}>
                  <Route index element={<Navigate to="/productos" replace />} />
                  <Route path="/productos" element={<ProductosPage />} />
                  <Route path="/terceros" element={<TercerosPage />} />
                  <Route path="/ventas" element={<VentasPage />} />
                </Route>
              </Route>
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </AuthProvider>
        </BrowserRouter>
      </NotifierProvider>
    </ThemeProvider>
  );
}
