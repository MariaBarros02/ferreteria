/*
 * Nombre: LoginRequest.java
 * Descripción: DTO de entrada para iniciar sesión (correo y contraseña).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credenciales de acceso.
 *
 * @param correo correo del usuario
 * @param password contraseña en texto plano (solo en tránsito por HTTPS)
 */
public record LoginRequest(
        @NotBlank(message = "{validacion.obligatorio}") @Email(message = "{validacion.correo}")
        @Size(max = 150, message = "{validacion.longitud}") String correo,
        @NotBlank(message = "{validacion.obligatorio}") @Size(max = 128, message = "{validacion.longitud}") String password) {
}
