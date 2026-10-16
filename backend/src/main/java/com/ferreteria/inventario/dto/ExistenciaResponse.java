/*
 * Nombre: ExistenciaResponse.java
 * Descripción: DTO de existencias por producto con costos, precio vigente y alertas (RF-05).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import com.ferreteria.inventario.domain.enums.TipoRastreo;
import java.math.BigDecimal;
import java.util.List;

/**
 * Existencias de un producto en unidad base.
 *
 * @param productoId producto
 * @param producto nombre legible
 * @param codigoBarras código
 * @param categoria categoría
 * @param unidadBase unidad base
 * @param tipoRastreo rastreo
 * @param activo activo
 * @param disponible saldo disponible
 * @param reservado saldo reservado
 * @param danado saldo no apto
 * @param total total físico
 * @param vencidoSinBaja unidades de lotes vencidos aún en disponible
 * @param porVencer unidades de lotes que vencen dentro del umbral
 * @param stockMinimo existencia mínima
 * @param costoPromedio costo promedio (nulo para roles sin acceso a costos)
 * @param precioVigente precio vigente aprobado
 * @param valorInventario total × costo promedio (nulo sin costo)
 * @param alertas AGOTADO, STOCK_BAJO, POR_VENCER, VENCIDO
 */
public record ExistenciaResponse(Long productoId, String producto, String codigoBarras, String categoria, String unidadBase,
                                 TipoRastreo tipoRastreo, boolean activo, BigDecimal disponible, BigDecimal reservado,
                                 BigDecimal danado, BigDecimal total, BigDecimal vencidoSinBaja, BigDecimal porVencer,
                                 BigDecimal stockMinimo, BigDecimal costoPromedio, BigDecimal precioVigente,
                                 BigDecimal valorInventario, List<String> alertas) {

    /**
     * Copia sin datos de costo.
     *
     * @return copia sin costo ni valor de inventario
     */
    public ExistenciaResponse sinCosto() {
        return new ExistenciaResponse(productoId, producto, codigoBarras, categoria, unidadBase, tipoRastreo, activo,
                disponible, reservado, danado, total, vencidoSinBaja, porVencer, stockMinimo, null, precioVigente, null,
                alertas);
    }
}
