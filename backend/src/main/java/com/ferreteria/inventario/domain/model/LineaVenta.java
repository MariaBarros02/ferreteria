/*
 * Nombre: LineaVenta.java
 * Descripción: Entidad JPA de línea de venta: cantidad en unidad base y precio vigente aplicado.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;

/**
 * Línea de una venta.
 */
@Entity
@Table(name = "linea_venta")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LineaVenta {

    /** Identificador. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_linea_venta")
    private Long id;

    /** Venta a la que pertenece. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_venta")
    @ToString.Exclude
    private Venta venta;

    /** Producto vendido. */
    @Column(name = "id_producto", nullable = false)
    private Long idProducto;

    /** Cantidad en unidad base. */
    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal cantidad;

    /** Precio vigente aplicado por unidad base. */
    @Column(name = "precio_aplicado", nullable = false, precision = 14, scale = 4)
    private BigDecimal precioAplicado;
}
