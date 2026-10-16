/*
 * Nombre: ReservaResponse.java
 * Descripción: DTO de salida de una reserva (RF-05).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import com.ferreteria.inventario.domain.enums.EstadoReserva;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Reserva.
 *
 * @param id identificador
 * @param productoId producto
 * @param producto nombre
 * @param clienteId cliente
 * @param cliente nombre del cliente
 * @param idPedido pedido
 * @param cantidad cantidad
 * @param fechaLimite fecha límite
 * @param estado estado
 * @param usuario correo de quien reservó
 * @param fechaCreacion creación
 * @param fechaLiberacion liberación
 * @param motivoLiberacion motivo de liberación
 * @param ventaId venta que la consumió
 */
public record ReservaResponse(Long id, Long productoId, String producto, Long clienteId, String cliente, String idPedido,
                              BigDecimal cantidad, LocalDate fechaLimite, EstadoReserva estado, String usuario,
                              LocalDateTime fechaCreacion, LocalDateTime fechaLiberacion, String motivoLiberacion,
                              Long ventaId) {
}
