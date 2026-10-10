/*
 * Nombre: Producto.java
 * Descripción: Entidad JPA de la tabla producto: referencia vendible = producto + marca + presentación (RN-01).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.model;

import com.ferreteria.inventario.domain.Convertidores;
import com.ferreteria.inventario.domain.enums.TipoRastreo;
import jakarta.persistence.*;
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
}
