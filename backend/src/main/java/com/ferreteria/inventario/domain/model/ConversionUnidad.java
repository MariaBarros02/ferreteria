/*
 * Nombre: ConversionUnidad.java
 * Descripción: Entidad JPA de conversion_unidad: factor de una presentación de compra/venta hacia la unidad base (RF-02).
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
 * Equivalencia de una presentación (caja, paquete…) a la unidad base del producto.
 */
@Entity
@Table(name = "conversion_unidad")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConversionUnidad {

    /** Identificador. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_conversion")
    private Long id;

    /** Producto al que aplica. */
    @Column(name = "id_producto", nullable = false)
    private Long idProducto;

    /** Nombre de la presentación (p. ej. caja). */
    @Column(name = "unidad_presentacion", nullable = false, length = 20)
    private String unidadPresentacion;

    /** Factor multiplicador hacia la unidad base (mayor que cero). */
    @Column(name = "factor_a_unidad_base", nullable = false, precision = 14, scale = 4)
    private BigDecimal factorAUnidadBase;

    /** Decimales admitidos para la cantidad en esta presentación. */
    @Column(name = "precision_decimales", nullable = false)
    private int precisionDecimales;
}
