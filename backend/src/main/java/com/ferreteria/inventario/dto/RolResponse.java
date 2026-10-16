/*
 * Nombre: RolResponse.java
 * Descripción: DTO de un rol con su matriz de permisos por módulo (RF-09).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import java.util.Map;

/**
 * Rol y permisos.
 *
 * @param id identificador
 * @param nombre nombre
 * @param permisos módulo → nivel de acceso
 */
public record RolResponse(Integer id, String nombre, Map<String, String> permisos) {
}
