/*
 * Nombre: EstadoProductoDevuelto.java
 * Descripción: Estado del producto devuelto por el cliente; solo apto vuelve a disponible (RF-13).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Condición del producto devuelto. Dañado o vencido nunca se reintegran al disponible.
 */
@Getter
@RequiredArgsConstructor
public enum EstadoProductoDevuelto implements ValorPersistente {
    /** Valor {@code apto}. */
    APTO("apto"),
    /** Valor {@code danado}. */
    DANADO("danado"),
    /** Valor {@code vencido}. */
    VENCIDO("vencido");

    /** Literal almacenado en la base de datos. */
    private final String valor;
}
