/*
 * Nombre: MonedaResponse.java
 * Descripción: DTO de salida de una moneda con su tasa vigente (RF-23).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Moneda con su tasa vigente a la fecha de consulta.
 *
 * @param codigo código ISO 4217
 * @param nombre nombre
 * @param simbolo símbolo
 * @param decimales decimales de presentación
 * @param activa disponible para documentos
 * @param funcional moneda funcional (COP)
 * @param tasaVigente pesos colombianos por 1 unidad (1 en la funcional; nulo si no hay tasa registrada)
 * @param fechaTasa fecha de vigencia de la tasa
 * @param fuenteTasa fuente de la tasa
 */
public record MonedaResponse(String codigo, String nombre, String simbolo, short decimales, boolean activa,
                             boolean funcional, BigDecimal tasaVigente, LocalDate fechaTasa, String fuenteTasa) {
}
