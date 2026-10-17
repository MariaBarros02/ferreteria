/*
 * Nombre: SesionConteo.java
 * Descripción: Entidad JPA de sesión de conteo físico (RF-15).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.model;

import jakarta.persistence.*;
import lombok.*;
import com.ferreteria.inventario.domain.Convertidores;
import com.ferreteria.inventario.domain.enums.EstadoSesionConteo;
import java.time.LocalDateTime;

/**
 * Sesión de conteo: agrupa los conteos por producto; nunca modifica el inventario sin aprobación.
 */
@Entity
@Table(name = "sesion_conteo")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SesionConteo {
    /** Identificador. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sesion")
    private Long id;

    /** Consecutivo (CF-000001). */
    @Column(name = "numero", nullable = false, unique = true, length = 30)
    private String numero;

    /** Estado. */
    @Convert(converter = Convertidores.EstadoSesionConteoConverter.class)
    @Column(name = "estado", nullable = false)
    private EstadoSesionConteo estado;

    /** Observaciones. */
    @Column(name = "observaciones", length = 500)
    private String observaciones;

    /** Usuario que la creó. */
    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    /** Inicio. */
    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;

    /** Cierre del registro. */
    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    /** Usuario que aprobó o rechazó. */
    @Column(name = "id_usuario_decide")
    private Integer idUsuarioDecide;

    /** Fecha de la decisión. */
    @Column(name = "fecha_decision")
    private LocalDateTime fechaDecision;

    /** Motivo del rechazo. */
    @Column(name = "motivo_rechazo", length = 255)
    private String motivoRechazo;
}
