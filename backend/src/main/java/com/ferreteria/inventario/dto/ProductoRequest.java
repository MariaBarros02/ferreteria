/*
 * Nombre: ProductoRequest.java
 * Descripción: DTO de entrada para crear/actualizar una referencia de producto con validaciones (RF-01, RN-01, RN-05).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
package com.ferreteria.inventario.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ferreteria.inventario.domain.enums.TipoRastreo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
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
 * Datos de una referencia vendible: producto + marca + presentación.
 *
 * @param nombre nombre del producto
 * @param marca marca
 * @param presentacion presentación (potencia, tamaño…)
 * @param codigoBarras código de barras único (8 a 14 dígitos)
 * @param unidadBase unidad en la que se controla el saldo
 * @param tipoRastreo ninguno, lote o serie
 * @param tieneVencimiento el producto vence (exige rastreo por lote: la fecha pertenece al lote)
 * @param tieneGarantia el producto tiene garantía
 * @param descripcion descripción opcional
 * @param categoria categoría opcional
 * @param porcentajeIva IVA en porcentaje (0 por defecto)
 * @param porcentajeIncremento recargo sobre costo para la propuesta automática de precio (0.30 = 30 %)
 * @param stockMinimo existencia mínima para la alerta de stock bajo
 * @param mesesGarantia meses de garantía
 */
@Schema(description = "Referencia vendible (producto + marca + presentación)")
public record ProductoRequest(
        @Schema(example = "Taladro")
        @NotBlank(message = "{validacion.obligatorio}") @Size(max = 150, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String nombre,
        @Schema(example = "Bosch")
        @NotBlank(message = "{validacion.obligatorio}") @Size(max = 100, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String marca,
        @Schema(example = "500W")
        @NotBlank(message = "{validacion.obligatorio}") @Size(max = 100, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String presentacion,
        @Schema(example = "7701234500015")
        @NotBlank(message = "{validacion.obligatorio}")
        @Pattern(regexp = Validaciones.CODIGO_BARRAS, message = "{validacion.codigo.barras}") String codigoBarras,
        @Schema(example = "unidad")
        @NotBlank(message = "{validacion.obligatorio}") @Size(max = 20, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String unidadBase,
        @NotNull(message = "{validacion.obligatorio}") TipoRastreo tipoRastreo,
        boolean tieneVencimiento,
        boolean tieneGarantia,
        @Size(max = 500, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String descripcion,
        @Size(max = 100, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String categoria,
        @DecimalMin(value = "0", message = "{validacion.no.negativo}")
        @DecimalMax(value = "100", message = "{validacion.porcentaje}")
        @Digits(integer = 3, fraction = 2, message = "{validacion.porcentaje}") BigDecimal porcentajeIva,
        @DecimalMin(value = "0", message = "{validacion.no.negativo}")
        @Digits(integer = 2, fraction = 4, message = "{validacion.porcentaje}") BigDecimal porcentajeIncremento,
        @DecimalMin(value = "0", message = "{validacion.no.negativo}")
        @Digits(integer = 10, fraction = 4, message = "{validacion.no.negativo}") BigDecimal stockMinimo,
        @Min(value = 1, message = "{validacion.positivo}") @Max(value = 120, message = "{validacion.positivo}") Integer mesesGarantia) {

    /**
     * Constructor de compatibilidad con la versión 1.0 (sin los campos opcionales).
     *
     * @param nombre nombre
     * @param marca marca
     * @param presentacion presentación
     * @param codigoBarras código de barras
     * @param unidadBase unidad base
     * @param tipoRastreo rastreo
     * @param tieneVencimiento vence
     * @param tieneGarantia tiene garantía
     */
    public ProductoRequest(String nombre, String marca, String presentacion, String codigoBarras, String unidadBase,
                           TipoRastreo tipoRastreo, boolean tieneVencimiento, boolean tieneGarantia) {
        this(nombre, marca, presentacion, codigoBarras, unidadBase, tipoRastreo, tieneVencimiento, tieneGarantia,
                null, null, null, null, null, null);
    }

    /** RN-05: la fecha de vencimiento se registra por lote; un producto que vence debe rastrearse por lote. */
    @JsonIgnore
    @AssertTrue(message = "{validacion.vencimiento.lote}")
    public boolean isVencimientoConLote() {
        return !tieneVencimiento || tipoRastreo == null || tipoRastreo == TipoRastreo.LOTE;
    }
}
