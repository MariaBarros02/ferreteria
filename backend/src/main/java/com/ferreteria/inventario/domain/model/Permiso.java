/*
 * Nombre: Permiso.java
 * Descripción: Entidad JPA de la matriz de permisos por rol y módulo (RF-09).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Nivel de acceso de un rol en un módulo (no, ver, consultar, si, proponer, aprobar, segun_autorizacion).
 */
@Entity
@Table(name = "permiso")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Permiso {
    /** Identificador. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_permiso")
    private Integer id;

    /** Rol. */
    @Column(name = "id_rol", nullable = false)
    private Integer idRol;

    /** Módulo. */
    @Column(name = "modulo", nullable = false, length = 50)
    private String modulo;

    /** Nivel de acceso. */
    @Column(name = "nivel_acceso", nullable = false, length = 20)
    private String nivelAcceso;
}
