/*
 * Nombre: Validaciones.java
 * Descripción: Constantes de expresiones regulares compartidas por las validaciones de entrada (anti-inyección de marcado).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
package com.ferreteria.inventario.dto;

/**
 * Patrones de validación reutilizables en los DTO.
 */
public final class Validaciones {

    /** Rechaza los caracteres {@code <} y {@code >} para evitar inyección de HTML/script almacenado. */
    public static final String SIN_HTML = "^[^<>]*$";

    /** Código de barras: entre 8 y 14 dígitos (EAN-8, UPC, EAN-13, GTIN-14). */
    public static final String CODIGO_BARRAS = "^\\d{8,14}$";

    /** Identificación: letras, dígitos y guiones, entre 4 y 20 caracteres. */
    public static final String IDENTIFICACION = "^[A-Za-z0-9-]{4,20}$";

    /** Clave de idempotencia: letras, dígitos, guiones y guiones bajos (p. ej. un UUID). */
    public static final String ID_OPERACION = "^[A-Za-z0-9_-]{8,64}$";

    private Validaciones() { }
}
