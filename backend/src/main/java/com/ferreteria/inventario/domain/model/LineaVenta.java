/*
 * Nombre: LineaVenta.java
 * Descripción: Entidad JPA de línea de venta: cantidad en unidad base y precio vigente aplicado.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
package com.ferreteria.inventario.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
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

    /** Presentación usada al vender (nula = unidad base). */
    @Column(length = 20)
    private String unidad;

    /** Cantidad expresada en la presentación vendida. */
    @Column(name = "cantidad_presentacion", precision = 14, scale = 4)
    private BigDecimal cantidadPresentacion;

    /** Porcentaje de descuento de la línea. */
    @Builder.Default
    @Column(name = "porcentaje_descuento", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeDescuento = BigDecimal.ZERO;

    /** Porcentaje de IVA aplicado (copiado del producto en el momento de la venta). */
    @Builder.Default
    @Column(name = "porcentaje_iva", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeIva = BigDecimal.ZERO;

    /** Cantidad × precio, antes de descuento e impuesto. */
    @Column(precision = 16, scale = 2)
    private BigDecimal subtotal;

    /** Valor del descuento. */
    @Builder.Default
    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal descuento = BigDecimal.ZERO;

    /** Valor del impuesto. */
    @Builder.Default
    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal impuesto = BigDecimal.ZERO;

    /** Total de la línea. */
    @Column(precision = 16, scale = 2)
    private BigDecimal total;

    /** Importes en la moneda de la venta (iguales a los de COP cuando la venta es en COP). */
    @Column(name = "precio_moneda", precision = 14, scale = 4)
    private BigDecimal precioMoneda;

    @Column(name = "subtotal_moneda", precision = 16, scale = 2)
    private BigDecimal subtotalMoneda;

    @Column(name = "descuento_moneda", precision = 16, scale = 2)
    private BigDecimal descuentoMoneda;

    @Column(name = "impuesto_moneda", precision = 16, scale = 2)
    private BigDecimal impuestoMoneda;

    @Column(name = "total_moneda", precision = 16, scale = 2)
    private BigDecimal totalMoneda;

    /** Fin de la garantía cuando el producto la maneja. */
    @Column(name = "garantia_hasta")
    private LocalDate garantiaHasta;
}
