/*
 * Nombre: CierreResponse.java
 * Descripción: DTO de salida de un cierre mensual registrado (RF-08).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import com.ferreteria.inventario.domain.enums.EstadoCierre;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Cierre registrado.
 *
 * @param id identificador
 * @param periodo mes (AAAA-MM)
 * @param estado cerrado o reabierto
 * @param fechaCierre fecha del (último) cierre
 * @param usuario correo de quien cerró
 * @param numeroVentas número de ventas
 * @param totalVentas total vendido
 * @param totalDevoluciones total devuelto por clientes
 * @param totalComprasRecibidas valor de compras recibidas
 * @param numeroMovimientos movimientos de inventario
 * @param observaciones observaciones
 * @param fechaReapertura fecha de reapertura
 * @param usuarioReapertura correo de quien reabrió
 * @param motivoReapertura motivo de reapertura
 */
public record CierreResponse(Long id, String periodo, EstadoCierre estado, LocalDateTime fechaCierre, String usuario,
                             Integer numeroVentas, BigDecimal totalVentas, BigDecimal totalDevoluciones,
                             BigDecimal totalComprasRecibidas, Integer numeroMovimientos, String observaciones,
                             LocalDateTime fechaReapertura, String usuarioReapertura, String motivoReapertura) {
}
