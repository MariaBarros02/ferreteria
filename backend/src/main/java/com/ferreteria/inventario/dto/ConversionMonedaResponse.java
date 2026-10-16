/*
 * Nombre: ConversionMonedaResponse.java
 * Descripción: DTO de salida de una conversión entre monedas con las tasas utilizadas (RF-23).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Resultado de convertir un monto.
 *
 * @param monto monto original
 * @param desde moneda de origen
 * @param hacia moneda de destino
 * @param resultado monto convertido (redondeado a los decimales de la moneda de destino)
 * @param tasaDesde pesos colombianos por 1 unidad de la moneda de origen
 * @param tasaHacia pesos colombianos por 1 unidad de la moneda de destino
 * @param fecha fecha de referencia de las tasas
 */
public record ConversionMonedaResponse(BigDecimal monto, String desde, String hacia, BigDecimal resultado,
                                       BigDecimal tasaDesde, BigDecimal tasaHacia, LocalDate fecha) {
}
