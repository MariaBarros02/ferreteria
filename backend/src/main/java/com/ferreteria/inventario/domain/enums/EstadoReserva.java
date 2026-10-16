/*
 * Nombre: EstadoReserva.java
 * Descripción: Estados del ciclo de vida de una reserva (RN-03).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Una reserva se libera solo por venta, cancelación autorizada o vencimiento de su fecha límite (RN-03).
 */
@Getter
@RequiredArgsConstructor
public enum EstadoReserva implements ValorPersistente {
    /** Valor {@code activa}. */
    ACTIVA("activa"),
    /** Valor {@code liberada_venta}. */
    LIBERADA_VENTA("liberada_venta"),
    /** Valor {@code liberada_cancelacion}. */
    LIBERADA_CANCELACION("liberada_cancelacion"),
    /** Valor {@code liberada_vencimiento}. */
    LIBERADA_VENCIMIENTO("liberada_vencimiento");

    /** Literal almacenado en la base de datos. */
    private final String valor;
}
