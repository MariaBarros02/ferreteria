/*
 * Nombre: EstadoAprobacion.java
 * Descripción: Estado de aprobación de ajustes (pendiente, aprobado, rechazado).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Estado de una solicitud sujeta a aprobación (ajustes de inventario).
 */
@Getter
@RequiredArgsConstructor
public enum EstadoAprobacion implements ValorPersistente {
    /** Valor {@code pendiente}. */
    PENDIENTE("pendiente"),
    /** Valor {@code aprobado}. */
    APROBADO("aprobado"),
    /** Valor {@code rechazado}. */
    RECHAZADO("rechazado");

    /** Literal almacenado en la base de datos. */
    private final String valor;
}
