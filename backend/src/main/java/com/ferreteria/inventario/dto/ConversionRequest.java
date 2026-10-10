/*
 * Nombre: ConversionRequest.java
 * Descripción: DTO de entrada para configurar una conversión de presentación a unidad base (RF-02).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Conversión de una presentación (p. ej. caja) a unidad base.
 *
 * @param unidadPresentacion nombre de la presentación
 * @param factor factor multiplicador, mayor que cero
 * @param precisionDecimales decimales admitidos
 */
public record ConversionRequest(
        @NotBlank(message = "{validacion.obligatorio}") @Size(max = 20, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String unidadPresentacion,
        @NotNull(message = "{validacion.obligatorio}")
        @DecimalMin(value = "0", inclusive = false, message = "{validacion.positivo}")
        @Digits(integer = 10, fraction = 4, message = "{validacion.positivo}") BigDecimal factor,
        @Min(value = 0, message = "{validacion.no.negativo}") @Max(4) int precisionDecimales) {
}
