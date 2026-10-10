/*
 * Nombre: TipoRastreo.java
 * Descripción: Rastreo parametrizable por producto (RN-05).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Rastreo parametrizable por producto (RN-05).
 */
@Getter
@RequiredArgsConstructor
public enum TipoRastreo implements ValorPersistente {
    /** Valor {@code ninguno}. */
    NINGUNO("ninguno"),
    /** Valor {@code lote}. */
    LOTE("lote"),
    /** Valor {@code serie}. */
    SERIE("serie");

    /** Literal almacenado en la columna ENUM. */
    private final String valor;
}
