/*
 * Nombre: TipoDevolucion.java
 * Descripción: Tipo de devolución: de cliente o a proveedor (RF-13).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Origen de la devolución (tipo_devolucion_enum).
 */
@Getter
@RequiredArgsConstructor
public enum TipoDevolucion implements ValorPersistente {
    /** Valor {@code cliente}. */
    CLIENTE("cliente"),
    /** Valor {@code proveedor}. */
    PROVEEDOR("proveedor");

    /** Literal almacenado en la base de datos. */
    private final String valor;
}
