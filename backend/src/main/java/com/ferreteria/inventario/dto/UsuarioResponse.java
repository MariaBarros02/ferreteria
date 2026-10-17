/*
 * Nombre: UsuarioResponse.java
 * Descripción: DTO de salida de un usuario (sin hash de contraseña).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import com.ferreteria.inventario.domain.enums.EstadoCuenta;
import com.ferreteria.inventario.domain.model.Usuario;

/**
 * Usuario.
 *
 * @param id identificador
 * @param nombre nombre
 * @param correo correo
 * @param rol rol
 * @param estado estado de la cuenta
 */
public record UsuarioResponse(Integer id, String nombre, String correo, String rol, EstadoCuenta estado) {
    /** Construye desde la entidad. */
    public static UsuarioResponse de(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNombre(), u.getCorreo(), u.getRol().getNombre(), u.getEstado());
    }
}
