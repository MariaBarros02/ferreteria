/*
 * Nombre: LoginResponse.java
 * Descripción: DTO de salida de la autenticación: token JWT, expiración y datos básicos del usuario.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import com.ferreteria.inventario.domain.model.Usuario;

/**
 * Respuesta de inicio de sesión / renovación de token.
 *
 * @param token JWT firmado
 * @param tipo esquema de autenticación (Bearer)
 * @param expiraEnSegundos segundos de vigencia
 * @param usuario datos del usuario autenticado
 */
public record LoginResponse(String token, String tipo, long expiraEnSegundos, UsuarioInfo usuario) {

    /**
     * Datos públicos del usuario (sin hash de contraseña).
     *
     * @param nombre nombre
     * @param correo correo
     * @param rol rol
     */
    public record UsuarioInfo(String nombre, String correo, String rol) {
        /** Construye desde la entidad. */
        public static UsuarioInfo de(Usuario u) {
            return new UsuarioInfo(u.getNombre(), u.getCorreo(), u.getRol().getNombre());
        }
    }
}
