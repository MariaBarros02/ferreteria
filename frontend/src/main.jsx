/*
 * Nombre: main.jsx
 * Descripción: Punto de entrada de la SPA: monta React e inicializa i18n y estilos globales.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import React from 'react';
import { createRoot } from 'react-dom/client';
import './i18n';
import './styles.css';
import App from './App';

createRoot(document.getElementById('root')).render(<React.StrictMode><App /></React.StrictMode>);
