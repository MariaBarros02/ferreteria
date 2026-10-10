/*
 * Nombre: ModalidadFactura.java
 * Descripción: Modalidad de la factura (RN-14).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Modalidad de la factura (RN-14).
 */
@Getter
@RequiredArgsConstructor
public enum ModalidadFactura implements ValorPersistente {
    /** Valor {@code electronica}. */
    ELECTRONICA("electronica"),
    /** Valor {@code impresa}. */
    IMPRESA("impresa");

    /** Literal almacenado en la columna ENUM. */
    private final String valor;
}
