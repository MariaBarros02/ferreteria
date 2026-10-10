/*
 * Nombre: VentaRequest.java
 * Descripción: DTO de entrada para registrar una venta con sus líneas (RF-04); cantidades en presentación o unidad base.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Solicitud de venta.
 *
 * @param clienteId identificador del tercero cliente
 * @param lineas líneas de la venta (al menos una)
 * @param impuestos impuestos (opcional, por defecto 0)
 * @param descuentos descuentos (opcional, por defecto 0)
 */
public record VentaRequest(
        @NotNull(message = "{validacion.obligatorio}") @Positive(message = "{validacion.positivo}") Long clienteId,
        @NotEmpty(message = "{validacion.lineas.minimo}") @Size(max = 200, message = "{validacion.longitud}")
        List<@Valid @NotNull(message = "{validacion.obligatorio}") Linea> lineas,
        @DecimalMin(value = "0", message = "{validacion.no.negativo}") @Digits(integer = 10, fraction = 4, message = "{validacion.no.negativo}") BigDecimal impuestos,
        @DecimalMin(value = "0", message = "{validacion.no.negativo}") @Digits(integer = 10, fraction = 4, message = "{validacion.no.negativo}") BigDecimal descuentos) {

    /**
     * Línea de venta solicitada.
     *
     * @param productoId producto
     * @param cantidad cantidad, en la presentación indicada o en unidad base si no se indica
     * @param unidad presentación opcional (p. ej. caja); si falta se asume unidad base
     */
    public record Linea(
            @NotNull(message = "{validacion.obligatorio}") @Positive(message = "{validacion.positivo}") Long productoId,
            @NotNull(message = "{validacion.obligatorio}")
            @DecimalMin(value = "0", inclusive = false, message = "{validacion.positivo}")
            @Digits(integer = 10, fraction = 4, message = "{validacion.positivo}") BigDecimal cantidad,
            @Size(max = 20, message = "{validacion.longitud}") String unidad) {
    }
}
