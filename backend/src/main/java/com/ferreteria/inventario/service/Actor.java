/*
 * Nombre: Actor.java
 * Descripción: Identidad del usuario que ejecuta una operación (id, correo e IP) para atribuir movimientos y eventos.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

/**
 * Usuario responsable de una operación (RF-08: usuario, IP).
 *
 * @param idUsuario identificador del usuario (nulo solo en eventos anónimos)
 * @param correo correo del usuario
 * @param ip dirección IP de origen
 */
public record Actor(Integer idUsuario, String correo, String ip) {
}
