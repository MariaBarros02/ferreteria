/*
 * Nombre: MonedaRequest.java
 * Descripción: DTO de entrada para registrar una moneda adicional (RF-23).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Nueva moneda (código ISO 4217).
 *
 * @param codigo tres letras mayúsculas
 * @param nombre nombre
 * @param simbolo símbolo
 * @param decimales decimales de presentación (0 a 4)
 */
public record MonedaRequest(
        @NotBlank(message = "{validacion.obligatorio}") @Pattern(regexp = "^[A-Z]{3}$", message = "{validacion.moneda}") String codigo,
        @NotBlank(message = "{validacion.obligatorio}") @Size(max = 60, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String nombre,
        @NotBlank(message = "{validacion.obligatorio}") @Size(max = 5, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String simbolo,
        @Min(value = 0, message = "{validacion.no.negativo}") @Max(value = 4, message = "{validacion.porcentaje}") int decimales) {
}
