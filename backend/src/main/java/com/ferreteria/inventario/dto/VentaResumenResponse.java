/*
 * Nombre: VentaResumenResponse.java
 * Descripción: DTO de listado de ventas con cliente, usuario y total persistido.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import com.ferreteria.inventario.domain.enums.EstadoDocumento;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Venta en el listado.
 *
 * @param id identificador
 * @param numeroInterno número interno
 * @param numeracionDian numeración de la factura
 * @param fecha fecha
 * @param clienteId cliente
 * @param cliente nombre del cliente
 * @param usuario correo del usuario
 * @param estado estado
 * @param total total en COP
 * @param tieneDevoluciones la venta tiene devoluciones registradas
 * @param moneda moneda del documento
 * @param totalMoneda total en la moneda del documento
 */
public record VentaResumenResponse(Long id, String numeroInterno, String numeracionDian, LocalDateTime fecha, Long clienteId,
                                   String cliente, String usuario, EstadoDocumento estado, BigDecimal total,
                                   boolean tieneDevoluciones, String moneda, BigDecimal totalMoneda) {
}
