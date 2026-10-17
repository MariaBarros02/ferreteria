/*
 * Nombre: ClaveRequest.java
 * Descripción: DTO de entrada para restablecer la contraseña de un usuario (RF-09).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Nueva contraseña.
 *
 * @param password nueva contraseña (10+ caracteres con mayúscula, minúscula y número)
 */
public record ClaveRequest(@NotBlank(message = "{validacion.obligatorio}") @Size(max = 128, message = "{validacion.longitud}")
                           String password) {
}
