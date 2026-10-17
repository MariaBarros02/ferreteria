/*
 * Nombre: EventoAuditoriaResponse.java
 * Descripción: DTO de salida de un evento de auditoría (RF-20).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import java.time.LocalDateTime;

/**
 * Evento auditado.
 *
 * @param id identificador
 * @param fechaHora fecha y hora UTC
 * @param idUsuario usuario autor
 * @param correoUsuario correo del autor
 * @param ip IP de origen
 * @param modulo módulo
 * @param accion acción
 * @param entidad entidad afectada
 * @param idEntidad registro afectado
 * @param descripcion descripción legible
 * @param detalle detalle estructurado (sin datos sensibles)
 * @param idOperacion clave de la operación relacionada
 */
public record EventoAuditoriaResponse(Long id, LocalDateTime fechaHora, Integer idUsuario, String correoUsuario, String ip,
                                      String modulo, String accion, String entidad, String idEntidad, String descripcion,
                                      Object detalle, String idOperacion) {
}
