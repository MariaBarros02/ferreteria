/*
 * Nombre: EstadoSesionConteo.java
 * Descripción: Estados de una sesión de conteo físico (RF-15).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Una sesión abierta admite registros; cerrada espera aprobación; aprobada genera ajustes; rechazada no altera saldos.
 */
@Getter
@RequiredArgsConstructor
public enum EstadoSesionConteo implements ValorPersistente {
    /** Valor {@code abierta}. */
    ABIERTA("abierta"),
    /** Valor {@code cerrada}. */
    CERRADA("cerrada"),
    /** Valor {@code aprobada}. */
    APROBADA("aprobada"),
    /** Valor {@code rechazada}. */
    RECHAZADA("rechazada");

    /** Literal almacenado en la base de datos. */
    private final String valor;
}
