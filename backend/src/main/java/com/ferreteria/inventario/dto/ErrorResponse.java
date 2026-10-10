/*
 * Nombre: ErrorResponse.java
 * Descripción: Formato de error de la API: {codigo, mensaje} con detalle opcional por campo (diseño de API, sección 17).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * Cuerpo de las respuestas de error.
 *
 * @param codigo código estable del error
 * @param mensaje mensaje en el idioma de la petición
 * @param detalles errores por campo (solo en validaciones)
 */
@Schema(description = "Error de la API")
public record ErrorResponse(
        @Schema(example = "SALDO_INSUFICIENTE") String codigo,
        @Schema(example = "Saldo insuficiente para el producto 1") String mensaje,
        Map<String, String> detalles) {

    /** Error sin detalle por campo. */
    public static ErrorResponse de(String codigo, String mensaje) {
        return new ErrorResponse(codigo, mensaje, null);
    }
}
