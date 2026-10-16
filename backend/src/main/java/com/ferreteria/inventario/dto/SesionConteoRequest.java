/*
 * Nombre: SesionConteoRequest.java
 * Descripción: DTO de entrada para abrir una sesión de conteo físico (RF-15).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Apertura de sesión de conteo.
 *
 * @param observaciones alcance u observaciones (zona, categoría…)
 */
public record SesionConteoRequest(
        @Size(max = 500, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String observaciones) {
}
