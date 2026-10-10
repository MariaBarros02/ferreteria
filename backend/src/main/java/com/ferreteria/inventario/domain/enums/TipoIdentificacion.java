/*
 * Nombre: TipoIdentificacion.java
 * Descripción: Tipo de documento de identificación de un tercero.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Tipo de documento de identificación de un tercero.
 */
@Getter
@RequiredArgsConstructor
public enum TipoIdentificacion implements ValorPersistente {
    /** Valor {@code CC}. */
    CC("CC"),
    /** Valor {@code NIT}. */
    NIT("NIT"),
    /** Valor {@code CE}. */
    CE("CE"),
    /** Valor {@code PASAPORTE}. */
    PASAPORTE("PASAPORTE");

    /** Literal almacenado en la columna ENUM. */
    private final String valor;
}
