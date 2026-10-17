/*
 * Nombre: Producto.java
 * Descripción: Entidad JPA de la tabla producto: referencia vendible = producto + marca + presentación (RN-01).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
package com.ferreteria.inventario.domain.model;

import com.ferreteria.inventario.domain.Convertidores;
import com.ferreteria.inventario.domain.enums.TipoRastreo;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;

/**
 * Referencia vendible del catálogo (RF-01). El código de barras es único en todo el catálogo.
 */
@Entity
@Table(name = "producto")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Producto {

    /** Identificador interno. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto")
    private Long id;

    /** Nombre del producto. */
    @Column(nullable = false, length = 150)
    private String nombre;

    /** Marca comercial. */
    @Column(nullable = false, length = 100)
    private String marca;

    /** Presentación (potencia, tamaño, etc.). */
    @Column(nullable = false, length = 100)
    private String presentacion;

    /** Código de barras único (RN-01). */
    @Column(name = "codigo_barras", nullable = false, unique = true, length = 50)
    private String codigoBarras;

    /** Unidad base en la que se controla el saldo (RN-02). */
    @Column(name = "unidad_base", nullable = false, length = 20)
    private String unidadBase;

    /** Tipo de rastreo: ninguno, lote o serie (RN-05). */
    @Convert(converter = Convertidores.TipoRastreoConverter.class)
    @Column(name = "tipo_rastreo", nullable = false)
    private TipoRastreo tipoRastreo;

    /** Indica si el producto maneja vencimiento. */
    @Column(name = "tiene_vencimiento", nullable = false)
    private boolean tieneVencimiento;

    /** Indica si el producto maneja garantía. */
    @Column(name = "tiene_garantia", nullable = false)
    private boolean tieneGarantia;

    /** Referencia activa; un producto con historial se desactiva en lugar de eliminarse (RN-09). */
    @Builder.Default
    @Column(nullable = false)
    private boolean activo = true;

    /** Descripción opcional. */
    @Column(length = 500)
    private String descripcion;

    /** Categoría opcional (herramientas, tornillería…). */
    @Column(length = 100)
    private String categoria;

    /** Porcentaje de IVA aplicado sobre el precio antes de impuestos (0 por defecto). */
    @Builder.Default
    @Column(name = "porcentaje_iva", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeIva = BigDecimal.ZERO;

    /** Recargo sobre el costo para la propuesta automática de precio (0.30 = 30 %); nulo = sin propuesta automática. */
    @Column(name = "porcentaje_incremento", precision = 6, scale = 4)
    private BigDecimal porcentajeIncremento;

    /** Costo promedio ponderado vigente por unidad base (RN-07). */
    @Column(name = "costo_promedio", precision = 14, scale = 4)
    private BigDecimal costoPromedio;

    /** Existencia mínima para la alerta de stock bajo; nulo = sin alerta. */
    @Column(name = "stock_minimo", precision = 14, scale = 4)
    private BigDecimal stockMinimo;

    /** Meses de garantía que se informan en la factura cuando el producto tiene garantía. */
    @Column(name = "meses_garantia")
    private Integer mesesGarantia;
}
