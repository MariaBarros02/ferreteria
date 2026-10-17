/*
 * Nombre: Lote.java
 * Descripción: Entidad JPA de lote: agrupa unidades de un producto con la misma fecha de vencimiento (RF-05, RN-05).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Lote de un producto. La fecha de vencimiento pertenece al lote, no a la referencia: el vencimiento de un lote no afecta a los demás (RN-05).
 */
@Entity
@Table(name = "lote")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Lote {
    /** Identificador. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_lote")
    private Long id;

    /** Producto. */
    @Column(name = "id_producto", nullable = false)
    private Long idProducto;

    /** Código del lote (único por producto). */
    @Column(name = "codigo", nullable = false, length = 50)
    private String codigo;

    /** Fecha de vencimiento; nula si el producto no vence. */
    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    /** Unidades del lote aptas para la venta (incluye las reservadas). */
    @Builder.Default
    @Column(name = "disponible", nullable = false, precision = 14, scale = 4)
    private BigDecimal disponible = BigDecimal.ZERO;

    /** Unidades del lote no aptas (dañadas o vencidas dadas de baja). */
    @Builder.Default
    @Column(name = "danado", nullable = false, precision = 14, scale = 4)
    private BigDecimal danado = BigDecimal.ZERO;

    /** Fecha de creación del lote. */
    @Column(name = "fecha_ingreso", nullable = false)
    private LocalDateTime fechaIngreso;
}
