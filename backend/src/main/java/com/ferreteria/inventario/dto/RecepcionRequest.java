/*
 * Nombre: RecepcionRequest.java
 * Descripción: DTO de entrada para recibir mercancía de una compra (RF-17): cantidades recibidas, lote y vencimiento.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Recepción (parcial o total) de una compra. Genera un documento de ingreso.
 *
 * @param facturaProveedor número de la factura del proveedor que respalda el ingreso
 * @param observaciones observaciones
 * @param idOperacion clave de idempotencia (un reintento no vuelve a sumar inventario)
 * @param lineas líneas recibidas
 */
public record RecepcionRequest(
        @NotBlank(message = "{validacion.obligatorio}") @Size(max = 100, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String facturaProveedor,
        @Size(max = 255, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String observaciones,
        @Size(max = 64, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.ID_OPERACION, message = "{validacion.id.operacion}") String idOperacion,
        @NotEmpty(message = "{validacion.lineas.minimo}") @Size(max = 200, message = "{validacion.longitud}")
        List<@Valid @NotNull(message = "{validacion.obligatorio}") Linea> lineas) {

    /**
     * Cantidad recibida de una línea.
     *
     * @param idLineaCompra línea de la compra
     * @param cantidad cantidad recibida en unidad base
     * @param codigoLote código del lote (obligatorio si el producto se rastrea por lote)
     * @param fechaVencimiento fecha de vencimiento (obligatoria si el producto vence)
     */
    public record Linea(
            @NotNull(message = "{validacion.obligatorio}") @Positive(message = "{validacion.positivo}") Long idLineaCompra,
            @NotNull(message = "{validacion.obligatorio}")
            @DecimalMin(value = "0", inclusive = false, message = "{validacion.positivo}")
            @Digits(integer = 10, fraction = 4, message = "{validacion.positivo}") BigDecimal cantidad,
            @Size(max = 50, message = "{validacion.longitud}")
            @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String codigoLote,
            LocalDate fechaVencimiento) {
    }
}
