/*
 * Nombre: vite.config.js
 * Descripción: Configuración de Vite y Vitest (jsdom). El proxy /api apunta al backend en desarrollo.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/** Configuración del bundler y del runner de pruebas unitarias. */
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, proxy: { '/api': { target: process.env.VITE_PROXY_TARGET || 'http://localhost:8080', changeOrigin: true } } },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/__tests__/setup.js',
    include: ['src/__tests__/**/*.test.{js,jsx}'],
    coverage: { provider: 'v8', reporter: ['text', 'json-summary'], include: ['src/**/*.{js,jsx}'], exclude: ['src/__tests__/**', 'src/main.jsx'] },
  },
});
