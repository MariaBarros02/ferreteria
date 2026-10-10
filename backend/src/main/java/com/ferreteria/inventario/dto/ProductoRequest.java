/*
 * Nombre: ProductoRequest.java
 * Descripción: DTO de entrada para crear/actualizar una referencia de producto con validaciones (RF-01, RN-01, RN-05).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import com.ferreteria.inventario.domain.enums.TipoRastreo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos de una referencia vendible: producto + marca + presentación.
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
        boolean tieneGarantia) {
}
