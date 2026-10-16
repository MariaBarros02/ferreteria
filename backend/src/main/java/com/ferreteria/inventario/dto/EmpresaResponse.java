/*
 * Nombre: EmpresaResponse.java
 * Descripción: DTO con los datos del negocio que se imprimen en los comprobantes (configurables en app.empresa.*).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;


/**
 * Datos del negocio emisor.
 *
 * @param nombre nombre comercial
 * @param nit NIT
 * @param direccion dirección
 * @param ciudad ciudad
 * @param telefono teléfono
 * @param correo correo
 */
public record EmpresaResponse(String nombre, String nit, String direccion, String ciudad, String telefono, String correo) {
}
