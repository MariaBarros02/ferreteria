/*
 * Nombre: tokens.js
 * Descripción: Tokens de diseño (colores, espaciado, tipografía, radios). Única fuente de verdad; contraste verificado WCAG AA.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
/**
 * Tokens de diseño. Los pares texto/fondo cumplen contraste >= 4.5:1 (WCAG 2.1 AA).
 * @module theme/tokens
 */
export const colorTokens = {
  light: {
    primary: '#0B5FA5',        // sobre blanco: 6.6:1
    primaryContrast: '#FFFFFF',
    secondary: '#7A4B00',      // sobre blanco: 7.4:1
    success: '#1B6E2E',
    warning: '#8A5A00',
    error: '#B3261E',          // sobre blanco: 6.5:1
    info: '#0B5FA5',
    background: '#F5F7FA',
    surface: '#FFFFFF',
    textPrimary: '#16202B',    // sobre fondo: 14:1
    textSecondary: '#4A5663',  // sobre blanco: 7.4:1
    divider: '#CBD3DC',
    focus: '#FFB000',
  },
  dark: {
    primary: '#8AC4FF',
    primaryContrast: '#00243F',
    secondary: '#FFC86B',
    success: '#7DD58F',
    warning: '#FFC86B',
    error: '#FFB4AB',
    info: '#8AC4FF',
    background: '#10161D',
    surface: '#18212B',
    textPrimary: '#EAF0F6',
    textSecondary: '#B5C0CC',
    divider: '#3A4756',
    focus: '#FFD54F',
  },
};

export const spacingUnit = 8;
export const radius = { sm: 4, md: 8, lg: 16 };
export const typography = {
  fontFamily: '"Roboto", "Segoe UI", "Helvetica Neue", Arial, sans-serif',
  baseSize: 16,
};
export const breakpoints = { xs: 0, sm: 600, md: 900, lg: 1200, xl: 1536 };
