/*
 * Nombre: theme.js
 * Descripción: Construye el tema de MUI desde los tokens (claro/oscuro) con foco visible y respeto de movimiento reducido.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { createTheme } from '@mui/material/styles';
import { colorTokens, radius, spacingUnit, typography, breakpoints } from './tokens';

/**
 * Crea el tema de la aplicación.
 * @param {'light'|'dark'} modo - Modo de color.
 * @returns {import('@mui/material').Theme} Tema de MUI.
 */
export function crearTema(modo = 'light') {
  const c = colorTokens[modo];
  return createTheme({
    palette: {
      mode: modo,
      primary: { main: c.primary, contrastText: c.primaryContrast },
      secondary: { main: c.secondary },
      success: { main: c.success },
      warning: { main: c.warning },
      error: { main: c.error },
      info: { main: c.info },
      background: { default: c.background, paper: c.surface },
      text: { primary: c.textPrimary, secondary: c.textSecondary },
      divider: c.divider,
    },
    spacing: spacingUnit,
    shape: { borderRadius: radius.md },
    breakpoints: { values: breakpoints },
    typography: { fontFamily: typography.fontFamily, fontSize: typography.baseSize - 2, button: { textTransform: 'none' } },
    components: {
      MuiCssBaseline: {
        styleOverrides: {
          ':focus-visible': { outline: `3px solid ${c.focus}`, outlineOffset: 2 },
          '@media (prefers-reduced-motion: reduce)': { '*': { animation: 'none !important', transition: 'none !important' } },
        },
      },
      MuiButton: { defaultProps: { disableElevation: true }, styleOverrides: { root: { minHeight: 44 } } },
      MuiIconButton: { styleOverrides: { root: { minWidth: 44, minHeight: 44 } } },
    },
  });
}
