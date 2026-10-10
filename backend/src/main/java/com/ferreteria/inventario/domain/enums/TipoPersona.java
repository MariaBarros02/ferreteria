/*
 * Nombre: TipoPersona.java
 * Descripción: Tipo de persona de un tercero (RN-06).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Tipo de persona de un tercero (RN-06).
 */
@Getter
@RequiredArgsConstructor
public enum TipoPersona implements ValorPersistente {
    /** Valor {@code natural}. */
    NATURAL("natural"),
    /** Valor {@code juridica}. */
    JURIDICA("juridica");

    /** Literal almacenado en la columna ENUM. */
    private final String valor;
}
