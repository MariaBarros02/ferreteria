/*
 * Nombre: CompraResumenResponse.java
 * Descripción: DTO de listado de compras.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.ferreteria.inventario.domain.enums.EstadoDocumento;

/**
 * Compra en el listado.
 *
 * @param id identificador
 * @param numero consecutivo
 * @param proveedor nombre del proveedor
 * @param fecha fecha
 * @param estado activo o anulado
 * @param estadoRecepcion PENDIENTE, PARCIAL, RECIBIDA o ANULADA
 * @param lineas número de líneas
 * @param total total
 */
public record CompraResumenResponse(Long id, String numero, String proveedor, LocalDateTime fecha, EstadoDocumento estado,
                                    String estadoRecepcion, int lineas, BigDecimal total) {
}
