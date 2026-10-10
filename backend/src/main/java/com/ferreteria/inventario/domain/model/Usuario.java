/*
 * Nombre: Usuario.java
 * Descripción: Entidad JPA de la tabla usuario: credenciales (hash BCrypt), rol y estado de cuenta.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.model;

import com.ferreteria.inventario.domain.Convertidores;
import com.ferreteria.inventario.domain.enums.EstadoCuenta;
import jakarta.persistence.*;
import lombok.*;

/**
 * Usuario del sistema. La contraseña se almacena únicamente como hash BCrypt.
 */
@Entity
@Table(name = "usuario")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Usuario {

    /** Identificador. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Integer id;

    /** Nombre completo. */
    @Column(nullable = false, length = 150)
    private String nombre;

    /** Correo, usado como nombre de usuario (único). */
    @Column(nullable = false, unique = true, length = 150)
    private String correo;

    /** Hash BCrypt de la contraseña. Nunca se expone por la API. */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** Rol asignado. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_rol")
    private Rol rol;

    /** Estado de la cuenta. */
    @Convert(converter = Convertidores.EstadoCuentaConverter.class)
    @Column(nullable = false)
    private EstadoCuenta estado;
}
