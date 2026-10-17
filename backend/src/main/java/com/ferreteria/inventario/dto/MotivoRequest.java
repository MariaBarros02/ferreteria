/*
 * Nombre: MotivoRequest.java
 * Descripción: DTO de entrada con un motivo obligatorio (anulaciones, rechazos, cancelaciones, reaperturas).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Motivo de una decisión.
 *
 * @param motivo texto obligatorio (máx. 255)
 */
public record MotivoRequest(
        @NotBlank(message = "{validacion.obligatorio}") @Size(max = 255, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String motivo) {
}
