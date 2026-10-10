/*
 * Nombre: Tercero.java
 * Descripción: Entidad JPA de tercero: cliente, proveedor o ambos; persona natural o jurídica (RN-06, RF-06).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.model;

import com.ferreteria.inventario.domain.Convertidores;
import com.ferreteria.inventario.domain.enums.TipoIdentificacion;
import com.ferreteria.inventario.domain.enums.TipoPersona;
import jakarta.persistence.*;
import lombok.*;

/**
 * Tercero (cliente y/o proveedor) con campos diferenciados según el tipo de persona.
 */
@Entity
@Table(name = "tercero")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Tercero {

    /** Identificador. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tercero")
    private Long id;

    /** Tipo de persona. */
    @Convert(converter = Convertidores.TipoPersonaConverter.class)
    @Column(name = "tipo_persona", nullable = false)
    private TipoPersona tipoPersona;

    /** Tipo de documento. */
    @Convert(converter = Convertidores.TipoIdentificacionConverter.class)
    @Column(name = "tipo_identificacion", nullable = false)
    private TipoIdentificacion tipoIdentificacion;

    /** Número de identificación (único). */
    @Column(name = "numero_identificacion", nullable = false, unique = true, length = 20)
    private String numeroIdentificacion;

    /** Primer nombre (persona natural). */
    @Column(name = "primer_nombre", length = 80) private String primerNombre;
    /** Segundo nombre (persona natural). */
    @Column(name = "segundo_nombre", length = 80) private String segundoNombre;
    /** Primer apellido (persona natural). */
    @Column(name = "primer_apellido", length = 80) private String primerApellido;
    /** Segundo apellido (persona natural). */
    @Column(name = "segundo_apellido", length = 80) private String segundoApellido;
    /** Razón social (persona jurídica). */
    @Column(name = "razon_social", length = 200) private String razonSocial;

    /** Dirección. */
    @Column(nullable = false, length = 200) private String direccion;
    /** Ciudad. */
    @Column(nullable = false, length = 80) private String ciudad;
    /** Teléfono. */
    @Column(nullable = false, length = 30) private String telefono;
    /** Correo electrónico. */
    @Column(nullable = false, length = 150) private String correo;

    /** Rol cliente. */
    @Column(name = "es_cliente", nullable = false) private boolean cliente;
    /** Rol proveedor. */
    @Column(name = "es_proveedor", nullable = false) private boolean proveedor;
}
