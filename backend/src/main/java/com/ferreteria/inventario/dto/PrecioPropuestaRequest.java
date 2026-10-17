/*
 * Nombre: PrecioPropuestaRequest.java
 * Descripción: DTO de entrada para proponer un precio (RF-07): calculado por recargo/margen o manual con justificación.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Propuesta de precio.
 *
 * @param productoId producto
 * @param modo recargo, margen o manual
 * @param porcentaje recargo o margen como fracción (0.30 = 30 %); si falta se usa el del producto
 * @param precioManual precio propuesto (solo modo manual)
 * @param motivo justificación (obligatoria en modo manual)
 */
public record PrecioPropuestaRequest(
        @NotNull(message = "{validacion.obligatorio}") @Positive(message = "{validacion.positivo}") Long productoId,
        @Pattern(regexp = "recargo|margen|manual", message = "{validacion.opcion}") String modo,
        @DecimalMin(value = "0", message = "{validacion.no.negativo}")
        @Digits(integer = 2, fraction = 4, message = "{validacion.porcentaje}") BigDecimal porcentaje,
        @DecimalMin(value = "0", inclusive = false, message = "{validacion.positivo}")
        @Digits(integer = 10, fraction = 4, message = "{validacion.positivo}") BigDecimal precioManual,
        @Size(max = 255, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String motivo) {

    /** En modo manual el precio y la justificación son obligatorios. */
    @JsonIgnore
    @AssertTrue(message = "{validacion.precio.manual}")
    public boolean isManualCompleto() {
        return !"manual".equals(modo) || (precioManual != null && motivo != null && !motivo.isBlank());
    }
}
