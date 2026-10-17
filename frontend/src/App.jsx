/*
 * Nombre: App.jsx
 * Descripción: Componente raíz: tema (claro/oscuro con tokens), proveedores, enrutamiento con rutas protegidas por sesión y por permiso de rol para todos los módulos.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 2.0.0
 */
import { lazy, Suspense, useMemo, useState } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Box, CircularProgress, CssBaseline, ThemeProvider } from '@mui/material';
import { AuthProvider } from './auth/AuthContext';
import ProtectedRoute from './auth/ProtectedRoute';
import RutaPermitida from './auth/RutaPermitida';
import Layout from './components/Layout';
import { NotifierProvider } from './components/Notifier';
import LoginPage from './pages/LoginPage';
import ProductosPage from './pages/ProductosPage';
import { crearTema } from './theme/theme';
import { puede, puedeImportar } from './utils/permisos';

// Carga diferida por ruta: cada módulo se descarga al visitarlo (paquete inicial más liviano).
const TercerosPage = lazy(() => import('./pages/TercerosPage'));
const VentasPage = lazy(() => import('./pages/ventas/VentasPage'));
const VentaNuevaPage = lazy(() => import('./pages/ventas/VentaNuevaPage'));
const VentaDetallePage = lazy(() => import('./pages/ventas/VentaDetallePage'));
const ComprobantePage = lazy(() => import('./pages/ventas/ComprobantePage'));
const ComprasPage = lazy(() => import('./pages/compras/ComprasPage'));
const CompraNuevaPage = lazy(() => import('./pages/compras/CompraNuevaPage'));
const CompraDetallePage = lazy(() => import('./pages/compras/CompraDetallePage'));
const InventarioPage = lazy(() => import('./pages/inventario/InventarioPage'));
const AjustesPage = lazy(() => import('./pages/control/AjustesPage'));
const ConteosPage = lazy(() => import('./pages/control/ConteosPage'));
const ConteoDetallePage = lazy(() => import('./pages/control/ConteoDetallePage'));
const PreciosPage = lazy(() => import('./pages/control/PreciosPage'));
const DevolucionesPage = lazy(() => import('./pages/devoluciones/DevolucionesPage'));
const DevolucionNuevaPage = lazy(() => import('./pages/devoluciones/DevolucionNuevaPage'));
const DevolucionDetallePage = lazy(() => import('./pages/devoluciones/DevolucionDetallePage'));
const AuditoriaPage = lazy(() => import('./pages/admin/AuditoriaPage'));
const CierresPage = lazy(() => import('./pages/admin/CierresPage'));
const ImportacionPage = lazy(() => import('./pages/admin/ImportacionPage'));
const UsuariosPage = lazy(() => import('./pages/admin/UsuariosPage'));
const MonedasPage = lazy(() => import('./pages/admin/MonedasPage'));

/** Indicador mientras se descarga el código de una página (carga diferida por ruta). */
function Cargando() {
  const { t } = useTranslation();
  return <Box sx={{ py: 6, textAlign: 'center' }}><CircularProgress aria-label={t('comun.cargando')} /></Box>;
}

const con = (accion) => (rol) => puede(rol, accion);

/** Rutas autenticadas: [ruta, componente, condición de rol]. */
export const RUTAS = [
  ['/productos', ProductosPage, () => true],
  ['/terceros', TercerosPage, () => true],
  ['/inventario', InventarioPage, () => true],
  ['/ventas', VentasPage, con('ventas.ver')],
  ['/ventas/nueva', VentaNuevaPage, con('ventas.registrar')],
  ['/ventas/:id', VentaDetallePage, con('ventas.ver')],
  ['/ventas/:id/comprobante', ComprobantePage, con('ventas.ver')],
  ['/compras', ComprasPage, con('compras.ver')],
  ['/compras/nueva', CompraNuevaPage, con('compras.registrar')],
  ['/compras/:id', CompraDetallePage, con('compras.ver')],
  ['/devoluciones', DevolucionesPage, con('devoluciones.ver')],
  ['/devoluciones/nueva', DevolucionNuevaPage, con('devoluciones.cliente')],
  ['/devoluciones/:id', DevolucionDetallePage, con('devoluciones.ver')],
  ['/ajustes', AjustesPage, con('ajustes.ver')],
  ['/conteos', ConteosPage, con('conteos.ver')],
  ['/conteos/:id', ConteoDetallePage, con('conteos.ver')],
  ['/precios', PreciosPage, con('precios.ver')],
  ['/auditoria', AuditoriaPage, con('auditoria.ver')],
  ['/cierres', CierresPage, con('cierres.gestionar')],
  ['/usuarios', UsuariosPage, con('usuarios.gestionar')],
  ['/importacion', ImportacionPage, puedeImportar],
  ['/monedas', MonedasPage, () => true],
];

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
                  {RUTAS.map(([ruta, Pagina, permitido]) => (
                    <Route key={ruta} path={ruta} element={<RutaPermitida permitido={permitido}><Suspense fallback={<Cargando />}><Pagina /></Suspense></RutaPermitida>} />
                  ))}
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
