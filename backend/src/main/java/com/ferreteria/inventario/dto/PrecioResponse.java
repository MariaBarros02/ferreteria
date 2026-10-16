/*
 * Nombre: PrecioResponse.java
 * Descripción: DTO de salida de una propuesta o precio del historial, con la comparación costo / precio actual / propuesto (RF-07, RF-16).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Registro del historial de precios.
 *
 * @param id identificador
 * @param productoId producto
 * @param producto nombre legible del producto
 * @param costoPromedio costo usado en el cálculo
 * @param porcentajeIncremento porcentaje usado
 * @param modoCalculo recargo, margen o manual
 * @param precioPropuesto precio propuesto
 * @param precioVigente precio que quedó vigente (solo aprobados)
 * @param precioActual precio vigente actual del producto (para comparar)
 * @param margenPropuesto margen sobre el precio propuesto: (P − C) / P
 * @param estado propuesto, aprobado o rechazado
 * @param proponente correo de quien propuso
 * @param aprobador correo de quien decidió
 * @param motivo justificación
 * @param motivoRechazo motivo del rechazo
 * @param fechaPropuesta fecha de propuesta
 * @param fechaDecision fecha de decisión
 * @param fechaVigencia inicio de vigencia
 */
public record PrecioResponse(Long id, Long productoId, String producto, BigDecimal costoPromedio,
                             BigDecimal porcentajeIncremento, String modoCalculo, BigDecimal precioPropuesto,
                             BigDecimal precioVigente, BigDecimal precioActual, BigDecimal margenPropuesto,
                             String estado, String proponente, String aprobador, String motivo, String motivoRechazo,
                             LocalDateTime fechaPropuesta, LocalDateTime fechaDecision, LocalDateTime fechaVigencia) {
}
