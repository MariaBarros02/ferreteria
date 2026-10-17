/*
 * Nombre: EstadoCierre.java
 * Descripción: Estado de un cierre mensual: cerrado o reabierto (RF-08).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Estado de un período cerrado.
 */
@Getter
@RequiredArgsConstructor
public enum EstadoCierre implements ValorPersistente {
    /** Valor {@code cerrado}. */
    CERRADO("cerrado"),
    /** Valor {@code reabierto}. */
    REABIERTO("reabierto");

    /** Literal almacenado en la base de datos. */
    private final String valor;
}
