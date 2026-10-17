/*
 * Nombre: ReservaRequest.java
 * Descripción: DTO de entrada para reservar inventario a un cliente (RF-05, RN-03).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Reserva.
 *
 * @param productoId producto
 * @param clienteId cliente
 * @param idPedido pedido o referencia
 * @param cantidad cantidad en unidad base
 * @param fechaLimite fecha límite (hoy o posterior)
 */
public record ReservaRequest(
        @NotNull(message = "{validacion.obligatorio}") @Positive(message = "{validacion.positivo}") Long productoId,
        @NotNull(message = "{validacion.obligatorio}") @Positive(message = "{validacion.positivo}") Long clienteId,
        @NotBlank(message = "{validacion.obligatorio}") @Size(max = 50, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String idPedido,
        @NotNull(message = "{validacion.obligatorio}")
        @DecimalMin(value = "0", inclusive = false, message = "{validacion.positivo}")
        @Digits(integer = 10, fraction = 4, message = "{validacion.positivo}") BigDecimal cantidad,
        @NotNull(message = "{validacion.obligatorio}")
        @FutureOrPresent(message = "{validacion.fecha.futura}") LocalDate fechaLimite) {
}
