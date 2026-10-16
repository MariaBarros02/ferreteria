/*
 * Nombre: EstadoInventario.java
 * Descripción: Estado (cubeta) del inventario afectado por un movimiento: disponible, reservado o dañado (RN-03).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Estados de existencia de un producto (RN-03). La suma de los tres es el total físico.
 */
@Getter
@RequiredArgsConstructor
public enum EstadoInventario implements ValorPersistente {
    /** Valor {@code disponible}. */
    DISPONIBLE("disponible"),
    /** Valor {@code reservado}. */
    RESERVADO("reservado"),
    /** Valor {@code danado}. */
    DANADO("danado");

    /** Literal almacenado en la base de datos. */
    private final String valor;
}
