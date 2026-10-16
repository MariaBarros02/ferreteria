/*
 * Nombre: CierreRequest.java
 * Descripción: DTO de entrada para cerrar un período mensual (RF-08).
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
 * Cierre mensual.
 *
 * @param periodo mes a cerrar con formato AAAA-MM
 * @param observaciones observaciones (obligatorias si hay diferencias de conciliación)
 */
public record CierreRequest(
        @NotBlank(message = "{validacion.obligatorio}") @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "{validacion.periodo}") String periodo,
        @Size(max = 500, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String observaciones) {
}
