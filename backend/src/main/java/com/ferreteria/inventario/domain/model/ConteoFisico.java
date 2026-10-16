/*
 * Nombre: ConteoFisico.java
 * Descripción: Entidad JPA de conteo físico de un producto dentro de una sesión (RF-15).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.model;

import jakarta.persistence.*;
import lombok.*;
import com.ferreteria.inventario.domain.Convertidores;
import com.ferreteria.inventario.domain.enums.EstadoConteo;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Conteo de un producto: cantidad contada, saldo del sistema en ese momento y diferencia.
 */
@Entity
@Table(name = "conteo_fisico")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConteoFisico {
    /** Identificador. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_conteo")
    private Long id;

    /** Producto. */
    @Column(name = "id_producto", nullable = false)
    private Long idProducto;

    /** Cantidad física contada. */
    @Column(name = "cantidad_contada", nullable = false, precision = 14, scale = 4)
    private BigDecimal cantidadContada;

    /** Contado − sistema. */
    @Column(name = "diferencia", nullable = false, precision = 14, scale = 4)
    private BigDecimal diferencia;

    /** Fecha del registro. */
    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    /** Estado. */
    @Convert(converter = Convertidores.EstadoConteoConverter.class)
    @Column(name = "estado", nullable = false)
    private EstadoConteo estado;

    /** Usuario que contó. */
    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    /** Movimiento del ajuste derivado. */
    @Column(name = "id_movimiento_ajuste")
    private Long idMovimientoAjuste;

    /** Sesión. */
    @Column(name = "id_sesion")
    private Long idSesion;

    /** Saldo disponible del sistema al contar. */
    @Column(name = "cantidad_sistema", precision = 14, scale = 4)
    private BigDecimal cantidadSistema;

    /** Observación. */
    @Column(name = "observacion", length = 255)
    private String observacion;
}
