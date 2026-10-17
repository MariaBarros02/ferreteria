/*
 * Nombre: EstadoConteo.java
 * Descripción: Estado de una línea de conteo físico (estado_conteo_enum).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Estado de cada línea de conteo: pendiente hasta que la sesión se aprueba o rechaza.
 */
@Getter
@RequiredArgsConstructor
public enum EstadoConteo implements ValorPersistente {
    /** Valor {@code pendiente}. */
    PENDIENTE("pendiente"),
    /** Valor {@code aprobado}. */
    APROBADO("aprobado"),
    /** Valor {@code rechazado}. */
    RECHAZADO("rechazado");

    /** Literal almacenado en la base de datos. */
    private final String valor;
}
