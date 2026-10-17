/*
 * Nombre: TasaCambioResponse.java
 * Descripción: DTO de salida de una tasa de cambio registrada (RF-23).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tasa de cambio registrada.
 *
 * @param id identificador
 * @param codigoMoneda moneda
 * @param tasa pesos colombianos por 1 unidad
 * @param fechaVigencia fecha de vigencia
 * @param fuente fuente
 * @param usuario correo de quien la registró
 * @param fechaRegistro fecha y hora de registro (UTC)
 */
public record TasaCambioResponse(Long id, String codigoMoneda, BigDecimal tasa, LocalDate fechaVigencia, String fuente,
                                 String usuario, LocalDateTime fechaRegistro) {
}
