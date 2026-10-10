/*
 * Nombre: ValorPersistente.java
 * Descripción: Contrato para enumerados cuyo valor en PostgreSQL (tipo ENUM, en minúscula) difiere del nombre Java.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.enums;

/**
 * Enumerado con un valor persistido en la base de datos.
 */
public interface ValorPersistente {

    /**
     * Devuelve el literal tal como está definido en el tipo ENUM de PostgreSQL.
     *
     * @return valor persistido (minúsculas)
     */
    String getValor();
}
