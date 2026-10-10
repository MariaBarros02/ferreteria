/*
 * Nombre: MovimientoResponse.java
 * Descripción: DTO de salida de auditoría con los ocho campos exigidos por RF-08 / RNF-06.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import com.ferreteria.inventario.domain.enums.TipoMovimiento;
import com.ferreteria.inventario.domain.model.MovimientoInventario;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Movimiento auditado.
 */
public record MovimientoResponse(Long id, TipoMovimiento tipo, Long idProducto, BigDecimal cantidad,
                                 LocalDateTime fechaHora, Integer idUsuario, String ip, String documentoRelacionado,
                                 BigDecimal valorAnterior, BigDecimal valorNuevo, String motivo,
                                 Long idMovimientoOriginal) {

    /** Construye desde la entidad. */
    public static MovimientoResponse de(MovimientoInventario m) {
        return new MovimientoResponse(m.getId(), m.getTipo(), m.getIdProducto(), m.getCantidad(), m.getFechaHora(),
                m.getIdUsuario(), m.getIp(), m.getDocumentoRelacionado(), m.getValorAnterior(), m.getValorNuevo(),
                m.getMotivo(), m.getIdMovimientoOriginal());
    }
}
