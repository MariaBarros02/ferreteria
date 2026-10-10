/*
 * Nombre: Layout.jsx
 * Descripción: Estructura de página: enlace "saltar al contenido", barra de navegación responsiva, idioma, tema, sesión y aviso sin conexión.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { useEffect, useState } from 'react';
import { Link as RouterLink, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { AppBar, Box, Button, Container, IconButton, Menu, MenuItem, Select, Toolbar, Typography, Alert } from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import Brightness4Icon from '@mui/icons-material/Brightness4';
import LogoutIcon from '@mui/icons-material/Logout';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import { cambiarIdioma } from '../i18n';

const ENLACES = [
  { to: '/productos', clave: 'nav.productos' },
  { to: '/terceros', clave: 'nav.terceros' },
  { to: '/ventas', clave: 'nav.ventas' },
];

/**
 * Layout principal de las páginas autenticadas.
 * @param {{onToggleTema: Function}} props - Alternador de tema claro/oscuro.
 * @returns {JSX.Element} Layout.
 */
export default function Layout({ onToggleTema }) {
  const { t, i18n } = useTranslation();
  const { usuario, logout } = useAuth();
  const navegar = useNavigate();
  const [ancla, setAncla] = useState(null);
  const [enLinea, setEnLinea] = useState(typeof navigator === 'undefined' ? true : navigator.onLine);

  useEffect(() => {
    const on = () => setEnLinea(true);
    const off = () => setEnLinea(false);
    window.addEventListener('online', on);
    window.addEventListener('offline', off);
    return () => { window.removeEventListener('online', on); window.removeEventListener('offline', off); };
  }, []);

  return (
    <>
      <a href="#contenido" className="saltar">{t('app.saltar')}</a>
      <AppBar position="static" component="header">
        <Toolbar sx={{ gap: 1 }}>
          <IconButton color="inherit" edge="start" aria-label={t('app.menu')} aria-haspopup="true"
            sx={{ display: { md: 'none' } }} onClick={(e) => setAncla(e.currentTarget)}>
            <MenuIcon />
          </IconButton>
          <Typography variant="h6" component={RouterLink} to="/" sx={{ color: 'inherit', textDecoration: 'none', flexGrow: { xs: 1, md: 0 }, mr: 2 }}>
            {t('app.titulo')}
          </Typography>
          <Box component="nav" aria-label={t('app.menu')} sx={{ display: { xs: 'none', md: 'flex' }, gap: 1, flexGrow: 1 }}>
            {ENLACES.map((l) => (
              <Button key={l.to} color="inherit" component={NavLink} to={l.to}
                sx={{ '&.active': { textDecoration: 'underline', textUnderlineOffset: 6 } }}>{t(l.clave)}</Button>
            ))}
          </Box>
          <Menu anchorEl={ancla} open={Boolean(ancla)} onClose={() => setAncla(null)}>
            {ENLACES.map((l) => (
              <MenuItem key={l.to} onClick={() => { setAncla(null); navegar(l.to); }}>{t(l.clave)}</MenuItem>
            ))}
          </Menu>
          <Select size="small" value={i18n.language} onChange={(e) => cambiarIdioma(e.target.value)}
            inputProps={{ 'aria-label': t('app.idioma') }} sx={{ color: 'inherit', '.MuiSvgIcon-root': { color: 'inherit' } }}>
            <MenuItem value="es">ES</MenuItem>
            <MenuItem value="en">EN</MenuItem>
          </Select>
          <IconButton color="inherit" aria-label={t('app.tema')} onClick={onToggleTema}><Brightness4Icon /></IconButton>
          <Typography variant="body2" sx={{ display: { xs: 'none', sm: 'block' } }}>{usuario?.nombre}</Typography>
          <IconButton color="inherit" aria-label={t('app.cerrarSesion')} onClick={() => { logout(); navegar('/login'); }}><LogoutIcon /></IconButton>
        </Toolbar>
      </AppBar>
      {!enLinea && <Alert severity="warning" role="status" square>{t('comun.sinConexion')}</Alert>}
      <Container component="main" id="contenido" tabIndex={-1} maxWidth="lg" sx={{ py: 3 }}>
        <Outlet />
      </Container>
    </>
  );
}
