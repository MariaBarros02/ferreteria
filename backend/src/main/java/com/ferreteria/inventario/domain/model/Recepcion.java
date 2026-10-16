/*
 * Nombre: Recepcion.java
 * Descripción: Entidad JPA de recepción: cantidad recibida de una línea de compra dentro de un documento de ingreso.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Recepción parcial o total de una línea de compra.
 */
@Entity
@Table(name = "recepcion")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Recepcion {
    /** Identificador. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_recepcion")
    private Long id;

    /** Línea de compra. */
    @Column(name = "id_linea_compra", nullable = false)
    private Long idLineaCompra;

    /** Documento de ingreso. */
    @Column(name = "id_documento_ingreso", nullable = false)
    private Long idDocumentoIngreso;

    /** Cantidad recibida en unidad base. */
    @Column(name = "cantidad_recibida", nullable = false, precision = 14, scale = 4)
    private BigDecimal cantidadRecibida;

    /** Fecha. */
    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    /** Lote al que ingresó (productos con rastreo por lote). */
    @Column(name = "id_lote")
    private Long idLote;

    /** Costo neto por unidad base usado en el costo promedio. */
    @Column(name = "costo_unitario", precision = 14, scale = 4)
    private BigDecimal costoUnitario;

    /** Movimiento de inventario generado. */
    @Column(name = "id_movimiento")
    private Long idMovimiento;
}
