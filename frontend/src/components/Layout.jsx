/*
 * Nombre: Layout.jsx
 * Descripción: Estructura de página: enlace "saltar al contenido", menú lateral agrupado por módulos y filtrado por rol (permanente en escritorio, desplegable en móvil), idioma, tema, sesión, aviso sin conexión y sincronización del catálogo para consulta offline.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
import { useEffect, useMemo, useState } from 'react';
import { Link as RouterLink, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Alert, AppBar, Box, Divider, Drawer, IconButton, List, ListItemButton, ListItemIcon, ListItemText, ListSubheader,
  MenuItem, Select, Toolbar, Tooltip, Typography } from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import Brightness4Icon from '@mui/icons-material/Brightness4';
import LogoutIcon from '@mui/icons-material/Logout';
import PointOfSaleIcon from '@mui/icons-material/PointOfSale';
import ShoppingCartIcon from '@mui/icons-material/ShoppingCart';
import AssignmentReturnIcon from '@mui/icons-material/AssignmentReturn';
import CategoryIcon from '@mui/icons-material/Category';
import InventoryIcon from '@mui/icons-material/Inventory2';
import TuneIcon from '@mui/icons-material/Tune';
import FactCheckIcon from '@mui/icons-material/FactCheck';
import SellIcon from '@mui/icons-material/Sell';
import PeopleIcon from '@mui/icons-material/People';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import HistoryIcon from '@mui/icons-material/History';
import LockClockIcon from '@mui/icons-material/LockClock';
import ManageAccountsIcon from '@mui/icons-material/ManageAccounts';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import { api } from '../api/http';
import { cambiarIdioma } from '../i18n';
import useEnLinea from '../hooks/useEnLinea';
import { borrarCatalogo, sincronizarCatalogo } from '../offline/catalogo';
import { puede, puedeImportar } from '../utils/permisos';

const ANCHO = 248;

/** Menú: grupos con enlaces y la condición de visibilidad por rol (la API sigue siendo la autoridad). */
export const MENU = [
  { grupo: 'nav.grupoOperacion', enlaces: [
    { to: '/ventas', clave: 'nav.ventas', icono: <PointOfSaleIcon />, ver: (r) => puede(r, 'ventas.ver') },
    { to: '/compras', clave: 'nav.compras', icono: <ShoppingCartIcon />, ver: (r) => puede(r, 'compras.ver') },
    { to: '/devoluciones', clave: 'nav.devoluciones', icono: <AssignmentReturnIcon />, ver: (r) => puede(r, 'devoluciones.ver') },
  ] },
  { grupo: 'nav.grupoInventario', enlaces: [
    { to: '/productos', clave: 'nav.productos', icono: <CategoryIcon />, ver: () => true },
    { to: '/inventario', clave: 'nav.inventario', icono: <InventoryIcon />, ver: () => true },
    { to: '/ajustes', clave: 'nav.ajustes', icono: <TuneIcon />, ver: (r) => puede(r, 'ajustes.ver') },
    { to: '/conteos', clave: 'nav.conteos', icono: <FactCheckIcon />, ver: (r) => puede(r, 'conteos.ver') },
    { to: '/precios', clave: 'nav.precios', icono: <SellIcon />, ver: (r) => puede(r, 'precios.ver') },
  ] },
  { grupo: 'nav.grupoTerceros', enlaces: [
    { to: '/terceros', clave: 'nav.terceros', icono: <PeopleIcon />, ver: () => true },
  ] },
  { grupo: 'nav.grupoAdministracion', enlaces: [
    { to: '/importacion', clave: 'nav.importacion', icono: <UploadFileIcon />, ver: (r) => puedeImportar(r) },
    { to: '/auditoria', clave: 'nav.auditoria', icono: <HistoryIcon />, ver: (r) => puede(r, 'auditoria.ver') },
    { to: '/cierres', clave: 'nav.cierres', icono: <LockClockIcon />, ver: (r) => puede(r, 'cierres.gestionar') },
    { to: '/usuarios', clave: 'nav.usuarios', icono: <ManageAccountsIcon />, ver: (r) => puede(r, 'usuarios.gestionar') },
  ] },
];

/** Grupos visibles para un rol (sin grupos vacíos). */
export const menuPara = (rol) => MENU.map((g) => ({ ...g, enlaces: g.enlaces.filter((l) => l.ver(rol)) })).filter((g) => g.enlaces.length);

/**
 * Layout principal de las páginas autenticadas.
 * @param {{onToggleTema: Function}} props - Alternador de tema claro/oscuro.
 * @returns {JSX.Element} Layout.
 */
export default function Layout({ onToggleTema }) {
  const { t, i18n } = useTranslation();
  const { usuario, rol, logout } = useAuth();
  const navegar = useNavigate();
  const ubicacion = useLocation();
  const enLinea = useEnLinea();
  const [movilAbierto, setMovilAbierto] = useState(false);
  const grupos = useMemo(() => menuPara(rol), [rol]);

  // Copia local del catálogo para consulta sin conexión (RF-12): se refresca al entrar y al recuperar la conexión.
  useEffect(() => {
    if (!enLinea || !usuario) return;
    sincronizarCatalogo((p) => api.catalogo(p)).catch(() => { /* se reintenta en la próxima reconexión */ });
  }, [enLinea, usuario]);

  useEffect(() => { setMovilAbierto(false); }, [ubicacion.pathname]);

  const salir = async () => {
    await borrarCatalogo();   // los datos locales pertenecen a la sesión
    logout();
    navegar('/login');
  };

  const menu = (
    <Box component="nav" aria-label={t('app.menu')} sx={{ overflowY: 'auto' }}>
      {grupos.map((g, i) => (
        <List key={g.grupo} dense subheader={<ListSubheader component="div" sx={{ bgcolor: 'transparent', lineHeight: '32px', mt: i ? 1 : 0 }}>{t(g.grupo)}</ListSubheader>}>
          {g.enlaces.map((l) => (
            <ListItemButton key={l.to} component={NavLink} to={l.to}
              sx={{ borderRadius: 1, mx: 1, '&.active': { bgcolor: 'action.selected', fontWeight: 600 } }}>
              <ListItemIcon sx={{ minWidth: 36 }}>{l.icono}</ListItemIcon>
              <ListItemText primary={t(l.clave)} />
            </ListItemButton>
          ))}
        </List>
      ))}
    </Box>
  );

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <a href="#contenido" className="saltar">{t('app.saltar')}</a>
      <AppBar position="fixed" component="header" sx={{ zIndex: (th) => th.zIndex.drawer + 1 }}>
        <Toolbar sx={{ gap: 1 }}>
          <IconButton color="inherit" edge="start" aria-label={t('app.menu')} aria-expanded={movilAbierto}
            sx={{ display: { md: 'none' } }} onClick={() => setMovilAbierto(true)}>
            <MenuIcon />
          </IconButton>
          <Typography variant="h6" component={RouterLink} to="/" noWrap
            sx={{ color: 'inherit', textDecoration: 'none', flexGrow: 1, minWidth: 0 }}>
            {t('app.titulo')}
          </Typography>
          <Select size="small" value={i18n.language} onChange={(e) => cambiarIdioma(e.target.value)}
            inputProps={{ 'aria-label': t('app.idioma') }} sx={{ color: 'inherit', '.MuiSvgIcon-root': { color: 'inherit' } }}>
            <MenuItem value="es">ES</MenuItem>
            <MenuItem value="en">EN</MenuItem>
          </Select>
          <IconButton color="inherit" aria-label={t('app.tema')} onClick={onToggleTema}><Brightness4Icon /></IconButton>
          <Box sx={{ display: { xs: 'none', sm: 'block' }, textAlign: 'right', lineHeight: 1.1 }}>
            <Typography variant="body2" noWrap>{usuario?.nombre}</Typography>
            <Typography variant="caption" sx={{ opacity: 0.85 }}>{rol}</Typography>
          </Box>
          <Tooltip title={t('app.cerrarSesion')}>
            <IconButton color="inherit" aria-label={t('app.cerrarSesion')} onClick={salir}><LogoutIcon /></IconButton>
          </Tooltip>
        </Toolbar>
      </AppBar>
      <Drawer variant="temporary" open={movilAbierto} onClose={() => setMovilAbierto(false)} ModalProps={{ keepMounted: true }}
        sx={{ display: { xs: 'block', md: 'none' }, '& .MuiDrawer-paper': { width: ANCHO } }}>
        <Toolbar><Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{t('app.titulo')}</Typography></Toolbar>
        <Divider />
        {menu}
      </Drawer>
      <Drawer variant="permanent" open
        sx={{ display: { xs: 'none', md: 'block' }, width: ANCHO, flexShrink: 0, '& .MuiDrawer-paper': { width: ANCHO, boxSizing: 'border-box' } }}>
        <Toolbar />
        {menu}
      </Drawer>
      <Box sx={{ flexGrow: 1, minWidth: 0 }}>
        <Toolbar />
        {!enLinea && <Alert severity="warning" role="status" square>{t('comun.sinConexion')}</Alert>}
        <Box component="main" id="contenido" tabIndex={-1} sx={{ p: { xs: 2, sm: 3 }, maxWidth: 1400, mx: 'auto', outline: 'none' }}>
          <Outlet />
        </Box>
      </Box>
    </Box>
  );
}
