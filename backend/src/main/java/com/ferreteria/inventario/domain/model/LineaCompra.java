/*
 * Nombre: LineaCompra.java
 * Descripción: Entidad JPA de línea de compra: cantidad pedida y recibida en unidad base (RF-03, RF-17).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/**
 * Línea de compra. {@code cantidadRecibida} nunca supera {@code cantidadPedida} (restricción de base de datos).
 */
@Entity
@Table(name = "linea_compra")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LineaCompra {
    /** Identificador. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_linea_compra")
    private Long id;

    /** Compra a la que pertenece. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_compra")
    @ToString.Exclude
    private Compra compra;

    /** Producto pedido. */
    @Column(name = "id_producto", nullable = false)
    private Long idProducto;

    /** Cantidad pedida en unidad base. */
    @Column(name = "cantidad_pedida", nullable = false, precision = 14, scale = 4)
    private BigDecimal cantidadPedida;

    /** Cantidad recibida acumulada en unidad base. */
    @Builder.Default
    @Column(name = "cantidad_recibida", nullable = false, precision = 14, scale = 4)
    private BigDecimal cantidadRecibida = BigDecimal.ZERO;

    /** Costo pactado por unidad base, antes de descuento e IVA. */
    @Column(name = "costo_pactado", nullable = false, precision = 14, scale = 4)
    private BigDecimal costoPactado;

    /** Presentación en la que se pidió (nula = unidad base). */
    @Column(name = "unidad", length = 20)
    private String unidad;

    /** Cantidad en la presentación pedida. */
    @Column(name = "cantidad_presentacion", precision = 14, scale = 4)
    private BigDecimal cantidadPresentacion;

    /** Costo por unidad base en la moneda de la compra; costoPactado es su equivalente en COP. */
    @Column(name = "costo_moneda", precision = 14, scale = 4)
    private BigDecimal costoMoneda;

    /** Costo por presentación. */
    @Column(name = "costo_presentacion", precision = 14, scale = 4)
    private BigDecimal costoPresentacion;

    /** IVA de la compra. */
    @Builder.Default
    @Column(name = "porcentaje_iva", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeIva = BigDecimal.ZERO;

    /** Descuento comercial del proveedor. */
    @Builder.Default
    @Column(name = "porcentaje_descuento", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeDescuento = BigDecimal.ZERO;
}
