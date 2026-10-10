/*
 * Nombre: Rol.java
 * Descripción: Entidad JPA de la tabla rol: perfil de acceso (Ventas, Compras, Inventario, Administrador, Gerencia).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Rol de seguridad asignado a los usuarios (RF-09).
 */
@Entity
@Table(name = "rol")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Rol {

    /** Identificador. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_rol")
    private Integer id;

    /** Nombre único del rol. */
    @Column(name = "nombre", nullable = false, unique = true, length = 50)
    private String nombre;
}
