/*
 * Nombre: ConversionResponse.java
 * Descripción: DTO de salida de una conversión de unidad.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import com.ferreteria.inventario.domain.model.ConversionUnidad;
import java.math.BigDecimal;

/**
 * Conversión configurada para un producto.
 */
public record ConversionResponse(String unidadPresentacion, BigDecimal factor, int precisionDecimales) {

    /** Construye desde la entidad. */
    public static ConversionResponse de(ConversionUnidad c) {
        return new ConversionResponse(c.getUnidadPresentacion(), c.getFactorAUnidadBase(), c.getPrecisionDecimales());
    }
}
