/*
 * Nombre: Precio.java
 * Descripción: Entidad JPA de precio: costo promedio, porcentaje, precio propuesto y vigente tras aprobación (RF-07, RF-16, RN-07).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
package com.ferreteria.inventario.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

/**
 * Registro del historial de precios de un producto.
 * El precio vigente solo existe cuando el estado de aprobación es {@code aprobado}.
 */
@Entity
@Table(name = "precio")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Precio {

    /** Identificador. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_precio")
    private Long id;

    /** Producto. */
    @Column(name = "id_producto", nullable = false)
    private Long idProducto;

    /** Costo promedio ponderado. */
    @Column(name = "costo_promedio", nullable = false, precision = 14, scale = 4)
    private BigDecimal costoPromedio;

    /** Porcentaje de incremento. */
    @Column(name = "porcentaje_incremento", nullable = false, precision = 6, scale = 4)
    private BigDecimal porcentajeIncremento;

    /** Precio propuesto (no vigente). */
    @Column(name = "precio_propuesto", nullable = false, precision = 14, scale = 4)
    private BigDecimal precioPropuesto;

    /** Precio vigente (solo tras aprobación). */
    @Column(name = "precio_vigente", precision = 14, scale = 4)
    private BigDecimal precioVigente;

    /** Estado de aprobación: propuesto, aprobado o rechazado. */
    @Builder.Default
    @Column(name = "estado_aprobacion", nullable = false, length = 20)
    private String estadoAprobacion = "propuesto";

    /** Usuario que aprobó o rechazó la propuesta. */
    @Column(name = "usuario_aprobador")
    private Integer usuarioAprobador;

    /** Usuario que propuso el precio. */
    @Column(name = "usuario_proponente")
    private Integer usuarioProponente;

    /** Forma de cálculo: recargo, margen o manual. */
    @Column(name = "modo_calculo", length = 10)
    private String modoCalculo;

    /** Justificación de la propuesta (obligatoria si es manual). */
    @Column(length = 255)
    private String motivo;

    /** Motivo del rechazo. */
    @Column(name = "motivo_rechazo", length = 255)
    private String motivoRechazo;

    /** Fecha de la propuesta. */
    @Column(name = "fecha_propuesta", insertable = false, updatable = false)
    private LocalDateTime fechaPropuesta;

    /** Fecha de la decisión (aprobación o rechazo). */
    @Column(name = "fecha_decision")
    private LocalDateTime fechaDecision;

    /** Fecha desde la cual rige el precio. */
    @Column(name = "fecha_vigencia")
    private LocalDateTime fechaVigencia;
}
