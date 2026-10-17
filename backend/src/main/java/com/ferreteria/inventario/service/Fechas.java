/*
 * Nombre: Fechas.java
 * Descripción: Conversión de fechas locales del negocio a instantes UTC de almacenamiento para filtros por rango de días.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Las fechas se guardan en UTC; los filtros "desde/hasta" llegan como días de la zona horaria del negocio.
 */
public final class Fechas {

    private Fechas() { }

    /**
     * Inicio (inclusive) del día local expresado en UTC.
     *
     * @param dia día local
     * @param zona zona del negocio
     * @return instante UTC sin zona
     */
    public static LocalDateTime inicioUtc(LocalDate dia, ZoneId zona) {
        return dia.atStartOfDay(zona).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /**
     * Fin (exclusivo) del día local expresado en UTC: inicio del día siguiente.
     *
     * @param dia día local
     * @param zona zona del negocio
     * @return instante UTC sin zona
     */
    public static LocalDateTime finExclusivoUtc(LocalDate dia, ZoneId zona) {
        return inicioUtc(dia.plusDays(1), zona);
    }
}
