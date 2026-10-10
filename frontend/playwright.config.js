/*
 * Nombre: playwright.config.js
 * Descripción: Configuración de Playwright (E2E): navegadores de escritorio y móvil para validar el diseño responsivo.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { defineConfig, devices } from '@playwright/test';

/** Requiere backend + frontend levantados (docker compose up) o E2E_BASE_URL. */
export default defineConfig({
  testDir: './e2e',
  timeout: 30000,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: { baseURL: process.env.E2E_BASE_URL || 'http://localhost:5173', trace: 'on-first-retry' },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'movil', use: { ...devices['Pixel 7'] } },
  ],
});
