/*
 * Nombre: CambioEstadoRequest.java
 * Descripción: DTO de entrada para mover unidades entre disponible y no apto (dañado) (RN-04).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import com.ferreteria.inventario.domain.enums.EstadoInventario;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Cambio de estado de inventario.
 *
 * @param productoId producto
 * @param idLote lote (opcional)
 * @param desde estado de origen (DISPONIBLE o DANADO)
 * @param hacia estado de destino (DANADO o DISPONIBLE)
 * @param cantidad cantidad en unidad base
 * @param motivo motivo obligatorio
 */
public record CambioEstadoRequest(
        @NotNull(message = "{validacion.obligatorio}") @Positive(message = "{validacion.positivo}") Long productoId,
        @Positive(message = "{validacion.positivo}") Long idLote,
        @NotNull(message = "{validacion.obligatorio}") EstadoInventario desde,
        @NotNull(message = "{validacion.obligatorio}") EstadoInventario hacia,
        @NotNull(message = "{validacion.obligatorio}")
        @DecimalMin(value = "0", inclusive = false, message = "{validacion.positivo}")
        @Digits(integer = 10, fraction = 4, message = "{validacion.positivo}") BigDecimal cantidad,
        @NotBlank(message = "{validacion.obligatorio}") @Size(max = 255, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String motivo) {
}
