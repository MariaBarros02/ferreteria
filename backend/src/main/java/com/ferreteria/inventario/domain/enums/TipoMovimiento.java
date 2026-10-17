/*
 * Nombre: TipoMovimiento.java
 * Descripción: Tipos de movimiento de inventario reconocidos (RN-08).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
package com.ferreteria.inventario.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Tipos de movimiento de inventario reconocidos (RN-08).
 */
@Getter
@RequiredArgsConstructor
public enum TipoMovimiento implements ValorPersistente {
    /** Valor {@code compra}. */
    COMPRA("compra"),
    /** Valor {@code venta}. */
    VENTA("venta"),
    /** Valor {@code devolucion_cliente}. */
    DEVOLUCION_CLIENTE("devolucion_cliente"),
    /** Valor {@code devolucion_proveedor}. */
    DEVOLUCION_PROVEEDOR("devolucion_proveedor"),
    /** Valor {@code ajuste}. */
    AJUSTE("ajuste"),
    /** Valor {@code conteo}. */
    CONTEO("conteo"),
    /** Valor {@code reserva}: paso de disponible a reservado o liberación (RN-03). */
    RESERVA("reserva"),
    /** Valor {@code cambio_estado}: transición entre disponible y dañado (RN-04). */
    CAMBIO_ESTADO("cambio_estado"),
    /** Valor {@code baja_vencimiento}: baja de un lote vencido (RN-05). */
    BAJA_VENCIMIENTO("baja_vencimiento");

    /** Literal almacenado en la columna ENUM. */
    private final String valor;
}
