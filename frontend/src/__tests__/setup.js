/*
 * Nombre: setup.js
 * Descripción: Configuración global de Vitest: matchers de jest-dom, i18n inicializado y limpieza de almacenamiento entre pruebas.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';
import '../i18n';

afterEach(() => {
  cleanup();
  localStorage.clear();
  sessionStorage.clear();
});
