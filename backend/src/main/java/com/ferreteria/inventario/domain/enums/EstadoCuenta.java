/*
 * Nombre: EstadoCuenta.java
 * Descripción: Estado de una cuenta de usuario.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Estado de una cuenta de usuario.
 */
@Getter
@RequiredArgsConstructor
public enum EstadoCuenta implements ValorPersistente {
    /** Valor {@code activo}. */
    ACTIVO("activo"),
    /** Valor {@code inactivo}. */
    INACTIVO("inactivo");

    /** Literal almacenado en la columna ENUM. */
    private final String valor;
}
