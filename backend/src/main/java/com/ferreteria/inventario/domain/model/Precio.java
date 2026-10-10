/*
 * Nombre: Precio.java
 * Descripción: Entidad JPA de precio: costo promedio, porcentaje, precio propuesto y vigente tras aprobación (RF-07, RF-16, RN-07).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
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

    /** Fecha desde la cual rige el precio. */
    @Column(name = "fecha_vigencia")
    private LocalDateTime fechaVigencia;
}
