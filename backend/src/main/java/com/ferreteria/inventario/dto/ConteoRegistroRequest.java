/*
 * Nombre: ConteoRegistroRequest.java
 * Descripción: DTO de entrada para registrar la cantidad contada de un producto en una sesión (RF-15).
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
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Registro de conteo (si el producto ya fue contado en la sesión, se reemplaza el valor).
 *
 * @param productoId producto
 * @param cantidadContada cantidad física contada (≥ 0) en unidad base
 * @param observacion observación
 */
public record ConteoRegistroRequest(
        @NotNull(message = "{validacion.obligatorio}") @Positive(message = "{validacion.positivo}") Long productoId,
        @NotNull(message = "{validacion.obligatorio}") @DecimalMin(value = "0", message = "{validacion.no.negativo}")
        @Digits(integer = 10, fraction = 4, message = "{validacion.no.negativo}") BigDecimal cantidadContada,
        @Size(max = 255, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String observacion) {
}
