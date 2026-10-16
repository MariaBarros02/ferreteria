/*
 * Nombre: UsuarioRequest.java
 * Descripción: DTO de entrada para crear o actualizar un usuario (RF-09). La contraseña solo se admite al crear o restablecer.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import com.ferreteria.inventario.domain.enums.EstadoCuenta;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Usuario.
 *
 * @param nombre nombre completo
 * @param correo correo (identificador de acceso)
 * @param rol nombre del rol (Ventas, Compras, Inventario, Administrador, Gerencia)
 * @param estado ACTIVO o INACTIVO
 * @param password contraseña inicial (solo al crear; política: 10+ caracteres con mayúscula, minúscula y número)
 */
public record UsuarioRequest(
        @NotBlank(message = "{validacion.obligatorio}") @Size(max = 150, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String nombre,
        @NotBlank(message = "{validacion.obligatorio}") @Email(message = "{validacion.correo}")
        @Size(max = 150, message = "{validacion.longitud}") String correo,
        @NotBlank(message = "{validacion.obligatorio}") @Size(max = 50, message = "{validacion.longitud}") String rol,
        @NotNull(message = "{validacion.obligatorio}") EstadoCuenta estado,
        @Size(max = 128, message = "{validacion.longitud}") String password) {
}
