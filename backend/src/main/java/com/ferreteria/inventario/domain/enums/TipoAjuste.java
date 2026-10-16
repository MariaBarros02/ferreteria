/*
 * Nombre: TipoAjuste.java
 * Descripción: Sentido de un ajuste de inventario: entrada o salida (RF-14).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Dirección del ajuste.
 */
@Getter
@RequiredArgsConstructor
public enum TipoAjuste implements ValorPersistente {
    /** Valor {@code entrada}. */
    ENTRADA("entrada"),
    /** Valor {@code salida}. */
    SALIDA("salida");

    /** Literal almacenado en la base de datos. */
    private final String valor;
}
