/*
 * Nombre: session.test.js
 * Descripción: Pruebas unitarias de la persistencia de sesión: permanencia, expiración, corrupción y cierre.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
import { describe, expect, it } from 'vitest';
import { borrarSesion, guardarSesion, leerSesion } from '../auth/session';

const datos = { token: 'jwt', usuario: { nombre: 'Ana', rol: 'Ventas' }, expiraEnSegundos: 1800 };

describe('session', () => {
  it('FE-SES-01 con "recordar" persiste en localStorage', () => {
    guardarSesion(datos, true);
    expect(localStorage.getItem('sesion-inventario')).not.toBeNull();
    expect(sessionStorage.getItem('sesion-inventario')).toBeNull();
    expect(leerSesion().usuario.nombre).toBe('Ana');
  });
  it('FE-SES-02 sin "recordar" solo vive en sessionStorage', () => {
    guardarSesion(datos, false);
    expect(localStorage.getItem('sesion-inventario')).toBeNull();
    expect(leerSesion().token).toBe('jwt');
  });
  it('FE-SES-03 una sesión expirada se descarta (borde exacto incluido)', () => {
    const t0 = 1_000_000;
    guardarSesion(datos, true, t0);
    expect(leerSesion(t0 + 1799_999)).not.toBeNull();
    expect(leerSesion(t0 + 1800_000)).toBeNull();
    expect(localStorage.getItem('sesion-inventario')).toBeNull();
  });
  it('FE-SES-04 datos corruptos se descartan sin lanzar error', () => {
    localStorage.setItem('sesion-inventario', '{no-json');
    expect(leerSesion()).toBeNull();
    expect(localStorage.getItem('sesion-inventario')).toBeNull();
  });
  it('FE-SES-05 borrarSesion limpia ambos almacenamientos', () => {
    guardarSesion(datos, true); guardarSesion(datos, false);
    borrarSesion();
    expect(leerSesion()).toBeNull();
  });
});
