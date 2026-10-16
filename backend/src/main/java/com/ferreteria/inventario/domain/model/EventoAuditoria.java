/*
 * Nombre: EventoAuditoria.java
 * Descripción: Entidad JPA de evento de auditoría general (RF-08, RF-20). Inmutable por disparador.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Evento auditado: quién hizo qué, sobre qué entidad, cuándo y desde dónde. Nunca se edita ni se elimina.
 */
@Entity
@Table(name = "evento_auditoria")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EventoAuditoria {
    /** Identificador. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evento")
    private Long id;

    /** Fecha y hora (UTC). */
    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    /** Usuario autor (nulo en eventos anónimos, p. ej. login fallido). */
    @Column(name = "id_usuario")
    private Integer idUsuario;

    /** Correo del usuario o identidad intentada. */
    @Column(name = "correo_usuario", length = 150)
    private String correoUsuario;

    /** IP de origen. */
    @Column(name = "ip", length = 45)
    private String ip;

    /** Módulo (VENTAS, COMPRAS, INVENTARIO…). */
    @Column(name = "modulo", nullable = false, length = 30)
    private String modulo;

    /** Acción (CREAR, ACTUALIZAR, APROBAR…). */
    @Column(name = "accion", nullable = false, length = 40)
    private String accion;

    /** Entidad afectada. */
    @Column(name = "entidad", nullable = false, length = 40)
    private String entidad;

    /** Identificador o número del registro afectado. */
    @Column(name = "id_entidad", length = 60)
    private String idEntidad;

    /** Descripción legible. */
    @Column(name = "descripcion", nullable = false, length = 500)
    private String descripcion;

    /** Detalle en JSON (sin datos sensibles). */
    @Column(name = "detalle", columnDefinition = "jsonb")
    private String detalle;

    /** Clave de la operación de negocio relacionada. */
    @Column(name = "id_operacion", length = 64)
    private String idOperacion;
}
