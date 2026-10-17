/*
 * Nombre: EstadoImportacion.java
 * Descripción: Estado de una importación de Excel (RF-10).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Una importación validada espera revisión; importada o cancelada son estados finales.
 */
@Getter
@RequiredArgsConstructor
public enum EstadoImportacion implements ValorPersistente {
    /** Valor {@code validada}. */
    VALIDADA("validada"),
    /** Valor {@code importada}. */
    IMPORTADA("importada"),
    /** Valor {@code cancelada}. */
    CANCELADA("cancelada");

    /** Literal almacenado en la base de datos. */
    private final String valor;
}
