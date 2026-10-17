/*
 * Nombre: CierrePeriodo.java
 * Descripción: Entidad JPA de cierre mensual con la constancia de totales (RF-08).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.model;

import jakarta.persistence.*;
import lombok.*;
import com.ferreteria.inventario.domain.Convertidores;
import com.ferreteria.inventario.domain.enums.EstadoCierre;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Cierre de un mes (periodo = primer día del mes). Mientras esté cerrado no se registran movimientos con fecha de ese mes.
 */
@Entity
@Table(name = "cierre_periodo")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CierrePeriodo {
    /** Identificador. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cierre")
    private Long id;

    /** Primer día del mes cerrado. */
    @Column(name = "periodo", nullable = false, unique = true)
    private LocalDate periodo;

    /** Fecha del cierre. */
    @Column(name = "fecha_cierre", nullable = false)
    private LocalDateTime fechaCierre;

    /** Usuario que cerró. */
    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    /** Estado. */
    @Convert(converter = Convertidores.EstadoCierreConverter.class)
    @Column(name = "estado", nullable = false)
    private EstadoCierre estado;

    /** Total vendido en el período. */
    @Column(name = "total_ventas", precision = 16, scale = 2)
    private BigDecimal totalVentas;

    /** Número de ventas. */
    @Column(name = "numero_ventas")
    private Integer numeroVentas;

    /** Total devuelto por clientes. */
    @Column(name = "total_devoluciones", precision = 16, scale = 2)
    private BigDecimal totalDevoluciones;

    /** Valor de las compras recibidas (costo neto). */
    @Column(name = "total_compras_recibidas", precision = 16, scale = 2)
    private BigDecimal totalComprasRecibidas;

    /** Movimientos de inventario del período. */
    @Column(name = "numero_movimientos")
    private Integer numeroMovimientos;

    /** Observaciones. */
    @Column(name = "observaciones", length = 500)
    private String observaciones;

    /** Resumen completo en JSON. */
    @Column(name = "resumen", columnDefinition = "jsonb")
    private String resumen;

    /** Fecha de reapertura. */
    @Column(name = "fecha_reapertura")
    private LocalDateTime fechaReapertura;

    /** Usuario que reabrió. */
    @Column(name = "id_usuario_reapertura")
    private Integer idUsuarioReapertura;

    /** Motivo de reapertura. */
    @Column(name = "motivo_reapertura", length = 255)
    private String motivoReapertura;
}
