/*
 * Nombre: AjusteResponse.java
 * Descripción: DTO de salida de un ajuste de inventario (RF-14).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import com.ferreteria.inventario.domain.enums.EstadoAprobacion;
import com.ferreteria.inventario.domain.enums.TipoAjuste;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ajuste de inventario.
 *
 * @param id identificador
 * @param numero consecutivo
 * @param productoId producto
 * @param producto nombre
 * @param lote código del lote
 * @param tipo entrada o salida
 * @param cantidad cantidad
 * @param motivo motivo
 * @param observaciones observaciones
 * @param estado pendiente, aprobado o rechazado
 * @param solicitante correo de quien solicitó
 * @param fechaSolicitud fecha de solicitud
 * @param aprobador correo de quien decidió
 * @param fechaDecision fecha de decisión
 * @param motivoRechazo motivo del rechazo
 * @param saldoSistema disponible al solicitar
 * @param saldoActual disponible actual
 * @param idMovimiento movimiento generado
 * @param idMovimientoOriginal movimiento rectificado
 * @param conteo número de la sesión de conteo que lo originó
 */
public record AjusteResponse(Long id, String numero, Long productoId, String producto, String lote, TipoAjuste tipo,
                             BigDecimal cantidad, String motivo, String observaciones, EstadoAprobacion estado,
                             String solicitante, LocalDateTime fechaSolicitud, String aprobador, LocalDateTime fechaDecision,
                             String motivoRechazo, BigDecimal saldoSistema, BigDecimal saldoActual, Long idMovimiento,
                             Long idMovimientoOriginal, String conteo) {
}
