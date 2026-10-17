/*
 * Nombre: CompraRequest.java
 * Descripción: DTO de entrada para registrar una compra con sus líneas (RF-03).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Compra a proveedor.
 *
 * @param proveedorId tercero con rol proveedor
 * @param observaciones observaciones
 * @param idOperacion clave de idempotencia generada por el cliente (evita registrar dos veces la misma compra)
 * @param lineas líneas (al menos una); el costo unitario se expresa en la moneda de la compra
 * @param moneda moneda del proveedor (ISO 4217); nula = moneda funcional COP (RF-23)
 */
public record CompraRequest(
        @NotNull(message = "{validacion.obligatorio}") @Positive(message = "{validacion.positivo}") Long proveedorId,
        @Size(max = 255, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String observaciones,
        @Size(max = 64, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.ID_OPERACION, message = "{validacion.id.operacion}") String idOperacion,
        @NotEmpty(message = "{validacion.lineas.minimo}") @Size(max = 200, message = "{validacion.longitud}")
        List<@Valid @NotNull(message = "{validacion.obligatorio}") Linea> lineas,
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "{validacion.moneda}") String moneda) {

    /** Compatibilidad: compra en la moneda funcional (COP). */
    public CompraRequest(Long proveedorId, String observaciones, String idOperacion, List<Linea> lineas) {
        this(proveedorId, observaciones, idOperacion, lineas, null);
    }

    /**
     * Línea pedida.
     *
     * @param productoId producto activo
     * @param cantidad cantidad en la presentación indicada (o en unidad base)
     * @param unidad presentación opcional (caja, paquete…)
     * @param costoUnitario costo pactado por la presentación indicada, antes de descuento e IVA
     * @param porcentajeIva IVA de la compra (0–100)
     * @param porcentajeDescuento descuento del proveedor (0–100)
     */
    public record Linea(
            @NotNull(message = "{validacion.obligatorio}") @Positive(message = "{validacion.positivo}") Long productoId,
            @NotNull(message = "{validacion.obligatorio}")
            @DecimalMin(value = "0", inclusive = false, message = "{validacion.positivo}")
            @Digits(integer = 10, fraction = 4, message = "{validacion.positivo}") BigDecimal cantidad,
            @Size(max = 20, message = "{validacion.longitud}")
            @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String unidad,
            @NotNull(message = "{validacion.obligatorio}")
            @DecimalMin(value = "0", message = "{validacion.no.negativo}")
            @Digits(integer = 10, fraction = 4, message = "{validacion.no.negativo}") BigDecimal costoUnitario,
            @DecimalMin(value = "0", message = "{validacion.no.negativo}")
            @DecimalMax(value = "100", message = "{validacion.porcentaje}") BigDecimal porcentajeIva,
            @DecimalMin(value = "0", message = "{validacion.no.negativo}")
            @DecimalMax(value = "100", message = "{validacion.porcentaje}") BigDecimal porcentajeDescuento) {
    }
}
