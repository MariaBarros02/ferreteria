/*
 * Nombre: LoteResponse.java
 * Descripción: DTO de lote con su estado de vencimiento calculado a partir de la fecha real (RF-05).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import com.ferreteria.inventario.domain.model.Lote;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Lote de un producto.
 *
 * @param id identificador
 * @param idProducto producto
 * @param producto nombre del producto (puede ser nulo)
 * @param codigo código del lote
 * @param fechaVencimiento vencimiento (nulo si no vence)
 * @param disponible unidades aptas
 * @param danado unidades no aptas
 * @param diasParaVencer días hasta el vencimiento (negativo si ya venció)
 * @param estado SIN_FECHA, VIGENTE, POR_VENCER o VENCIDO
 */
public record LoteResponse(Long id, Long idProducto, String producto, String codigo, LocalDate fechaVencimiento,
                           BigDecimal disponible, BigDecimal danado, Long diasParaVencer, String estado) {

    /**
     * Construye el DTO calculando el estado con el umbral de alerta.
     *
     * @param l lote
     * @param producto nombre del producto
     * @param hoy fecha de referencia
     * @param diasAlerta umbral de alerta en días
     * @return DTO
     */
    public static LoteResponse de(Lote l, String producto, LocalDate hoy, int diasAlerta) {
        if (l.getFechaVencimiento() == null) {
            return new LoteResponse(l.getId(), l.getIdProducto(), producto, l.getCodigo(), null, l.getDisponible(),
                    l.getDanado(), null, "SIN_FECHA");
        }
        long dias = ChronoUnit.DAYS.between(hoy, l.getFechaVencimiento());
        String estado = dias < 0 ? "VENCIDO" : dias <= diasAlerta ? "POR_VENCER" : "VIGENTE";
        return new LoteResponse(l.getId(), l.getIdProducto(), producto, l.getCodigo(), l.getFechaVencimiento(),
                l.getDisponible(), l.getDanado(), dias, estado);
    }
}
