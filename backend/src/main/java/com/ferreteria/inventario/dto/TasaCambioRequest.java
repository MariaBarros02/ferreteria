/*
 * Nombre: TasaCambioRequest.java
 * Descripción: DTO de entrada para registrar una tasa de cambio (RF-23).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tasa de cambio a registrar.
 *
 * @param tasa pesos colombianos por 1 unidad de la moneda (hasta 6 decimales)
 * @param fechaVigencia fecha desde la que aplica (no futura)
 * @param fuente fuente de la tasa (p. ej. TRM Banco de la República)
 */
public record TasaCambioRequest(
        @NotNull(message = "{validacion.obligatorio}")
        @DecimalMin(value = "0.000001", message = "{validacion.positivo}")
        @Digits(integer = 12, fraction = 6, message = "{validacion.positivo}") BigDecimal tasa,
        @NotNull(message = "{validacion.obligatorio}") LocalDate fechaVigencia,
        @Size(max = 100, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String fuente) {
}
