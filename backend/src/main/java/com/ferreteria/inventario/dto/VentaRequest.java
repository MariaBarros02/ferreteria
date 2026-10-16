/*
 * Nombre: VentaRequest.java
 * Descripción: DTO de entrada para registrar una venta (RF-04): cliente, líneas con presentación y descuento, clave de idempotencia.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
package com.ferreteria.inventario.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
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
 * Solicitud de venta. Los importes (subtotal, descuentos, impuestos y total) los calcula el servidor con el
 * precio vigente aprobado y el IVA del producto; el cliente no puede fijarlos (RF-04).
 *
 * @param clienteId identificador del tercero cliente
 * @param lineas líneas de la venta (al menos una)
 * @param impuestos obsoleto (v1.0): solo se acepta nulo o 0; los impuestos se calculan por línea
 * @param descuentos obsoleto (v1.0): solo se acepta nulo o 0; use el descuento por línea
 * @param idOperacion clave de idempotencia generada por el cliente: un reintento no crea otra venta (RF-11)
 * @param observaciones observaciones
 * @param moneda moneda del documento (ISO 4217); nula = moneda funcional COP (RF-23)
 */
public record VentaRequest(
        @NotNull(message = "{validacion.obligatorio}") @Positive(message = "{validacion.positivo}") Long clienteId,
        @NotEmpty(message = "{validacion.lineas.minimo}") @Size(max = 200, message = "{validacion.longitud}")
        List<@Valid @NotNull(message = "{validacion.obligatorio}") Linea> lineas,
        @DecimalMin(value = "0", message = "{validacion.no.negativo}") @Digits(integer = 10, fraction = 4, message = "{validacion.no.negativo}") BigDecimal impuestos,
        @DecimalMin(value = "0", message = "{validacion.no.negativo}") @Digits(integer = 10, fraction = 4, message = "{validacion.no.negativo}") BigDecimal descuentos,
        @Size(max = 64, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.ID_OPERACION, message = "{validacion.id.operacion}") String idOperacion,
        @Size(max = 255, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String observaciones,
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "{validacion.moneda}") String moneda) {

    /** Compatibilidad: venta en la moneda funcional (COP). */
    public VentaRequest(Long clienteId, List<Linea> lineas, BigDecimal impuestos, BigDecimal descuentos,
                        String idOperacion, String observaciones) {
        this(clienteId, lineas, impuestos, descuentos, idOperacion, observaciones, null);
    }

    /**
     * Constructor de compatibilidad con la versión 1.0.
     *
     * @param clienteId cliente
     * @param lineas líneas
     * @param impuestos impuestos (nulo o 0)
     * @param descuentos descuentos (nulo o 0)
     */
    public VentaRequest(Long clienteId, List<Linea> lineas, BigDecimal impuestos, BigDecimal descuentos) {
        this(clienteId, lineas, impuestos, descuentos, null, null, null);
    }

    /** Los importes globales manuales no se admiten: se calculan en el servidor. */
    @JsonIgnore
    @AssertTrue(message = "{validacion.importes.calculados}")
    public boolean isSinImportesManuales() {
        return (impuestos == null || impuestos.signum() == 0) && (descuentos == null || descuentos.signum() == 0);
    }

    /**
     * Línea de venta solicitada.
     *
     * @param productoId producto
     * @param cantidad cantidad, en la presentación indicada o en unidad base si no se indica
     * @param unidad presentación opcional (p. ej. caja); si falta se asume unidad base
     * @param porcentajeDescuento descuento de la línea (0 a app.ventas.descuento-maximo)
     * @param reservaId reserva del cliente que se consume con esta línea (opcional)
     */
    public record Linea(
            @NotNull(message = "{validacion.obligatorio}") @Positive(message = "{validacion.positivo}") Long productoId,
            @NotNull(message = "{validacion.obligatorio}")
            @DecimalMin(value = "0", inclusive = false, message = "{validacion.positivo}")
            @Digits(integer = 10, fraction = 4, message = "{validacion.positivo}") BigDecimal cantidad,
            @Size(max = 20, message = "{validacion.longitud}") String unidad,
            @DecimalMin(value = "0", message = "{validacion.no.negativo}")
            @DecimalMax(value = "100", message = "{validacion.porcentaje}")
            @Digits(integer = 3, fraction = 2, message = "{validacion.porcentaje}") BigDecimal porcentajeDescuento,
            @Positive(message = "{validacion.positivo}") Long reservaId) {

        /**
         * Constructor de compatibilidad con la versión 1.0.
         *
         * @param productoId producto
         * @param cantidad cantidad
         * @param unidad presentación
         */
        public Linea(Long productoId, BigDecimal cantidad, String unidad) {
            this(productoId, cantidad, unidad, null, null);
        }
    }
}
