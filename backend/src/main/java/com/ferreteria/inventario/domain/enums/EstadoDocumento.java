/*
 * Nombre: EstadoDocumento.java
 * Descripción: Estados válidos de un documento: nunca se borra, solo se anula (RN-09).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Estados válidos de un documento: nunca se borra, solo se anula (RN-09).
 */
@Getter
@RequiredArgsConstructor
public enum EstadoDocumento implements ValorPersistente {
    /** Valor {@code activo}. */
    ACTIVO("activo"),
    /** Valor {@code anulado}. */
    ANULADO("anulado");

    /** Literal almacenado en la columna ENUM. */
    private final String valor;
}
