/*
 * Nombre: InventarioResponse.java
 * Descripción: DTO de salida del saldo de inventario por estado (disponible, reservado, dañado).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import com.ferreteria.inventario.domain.model.Inventario;
import java.math.BigDecimal;

/**
 * Saldos de un producto en su unidad base.
 */
public record InventarioResponse(Long idProducto, BigDecimal disponible, BigDecimal reservado, BigDecimal danado) {

    /** Construye desde la entidad. */
    public static InventarioResponse de(Inventario i) {
        return new InventarioResponse(i.getIdProducto(), i.getDisponible(), i.getReservado(), i.getDanado());
    }
}
