/*
 * Nombre: mensajes.test.js
 * Descripción: Pruebas unitarias de utilidades de mensajes: traducción de validaciones y errores de API (i18n es/en).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { describe, expect, it } from 'vitest';
import i18n from '../i18n';
import { mensajeDeError, traducirValidacion } from '../utils/mensajes';

describe('mensajes', () => {
  it('FE-I18N-01 traduce clave con parámetro en español e inglés', async () => {
    await i18n.changeLanguage('es');
    expect(traducirValidacion(i18n.t, 'errores.maximo|150')).toBe('Máximo 150 caracteres');
    await i18n.changeLanguage('en');
    expect(traducirValidacion(i18n.t, 'errores.maximo|150')).toBe('Maximum 150 characters');
    await i18n.changeLanguage('es');
  });
  it('FE-I18N-02 mensaje vacío o no textual devuelve undefined', () => {
    expect(traducirValidacion(i18n.t, undefined)).toBeUndefined();
    expect(traducirValidacion(i18n.t, 42)).toBeUndefined();
  });
  it('FE-I18N-03 errores de API: credenciales, bloqueo, mensaje del servidor y genérico', () => {
    const t = i18n.t.bind(i18n);
    expect(mensajeDeError(t, { response: { data: { codigo: 'CREDENCIALES_INVALIDAS' } } })).toBe('Credenciales inválidas');
    expect(mensajeDeError(t, { response: { data: { codigo: 'CUENTA_BLOQUEADA' } } })).toContain('bloqueada');
    expect(mensajeDeError(t, { response: { data: { codigo: 'X', mensaje: 'Saldo insuficiente' } } })).toBe('Saldo insuficiente');
    expect(mensajeDeError(t, {})).toBe('No se pudo completar la operación');
    expect(mensajeDeError(t, { code: 'OFFLINE' })).toContain('Sin conexión');
  });
});
