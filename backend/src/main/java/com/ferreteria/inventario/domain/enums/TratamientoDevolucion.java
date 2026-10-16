/*
 * Nombre: TratamientoDevolucion.java
 * Descripción: Tratamiento económico de una devolución (reembolso, saldo a favor, cambio, nota crédito, ninguno).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Tratamiento del importe devuelto. El sistema no gestiona pagos: se registra la decisión.
 */
@Getter
@RequiredArgsConstructor
public enum TratamientoDevolucion implements ValorPersistente {
    /** Valor {@code reembolso}. */
    REEMBOLSO("reembolso"),
    /** Valor {@code saldo_favor}. */
    SALDO_FAVOR("saldo_favor"),
    /** Valor {@code cambio}. */
    CAMBIO("cambio"),
    /** Valor {@code nota_credito_proveedor}. */
    NOTA_CREDITO_PROVEEDOR("nota_credito_proveedor"),
    /** Valor {@code ninguno}. */
    NINGUNO("ninguno");

    /** Literal almacenado en la base de datos. */
    private final String valor;
}
