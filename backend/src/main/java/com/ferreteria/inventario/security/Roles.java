/*
 * Nombre: Roles.java
 * Descripción: Constantes de roles y normalización a autoridades de Spring Security (matriz de permisos RF-09).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.security;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Nombres de rol usados en {@code @PreAuthorize}. Spring antepone el prefijo {@code ROLE_} automáticamente.
 */
public final class Roles {

    /** Personal de ventas. */
    public static final String VENTAS = "VENTAS";
    /** Personal de compras. */
    public static final String COMPRAS = "COMPRAS";
    /** Responsable de inventario. */
    public static final String INVENTARIO = "INVENTARIO";
    /** Administrador. */
    public static final String ADMINISTRADOR = "ADMINISTRADOR";
    /** Gerencia / responsable de cierre. */
    public static final String GERENCIA = "GERENCIA";

    private Roles() { }

    /**
     * Normaliza el nombre almacenado (p. ej. "Administrador") al nombre de rol (p. ej. "ADMINISTRADOR").
     *
     * @param nombre nombre del rol en la base de datos
     * @return nombre normalizado sin tildes y en mayúsculas
     */
    public static String normalizar(String nombre) {
        String sinTildes = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.trim().toUpperCase(Locale.ROOT);
    }
}
