/*
 * Nombre: LineaVentaLote.java
 * Descripción: Entidad JPA de la asignación de lotes a una línea de venta (para devolver al lote correcto).
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
 * Cantidad de una línea de venta tomada de un lote (asignación FEFO: primero el que vence antes).
 */
@Entity
@Table(name = "linea_venta_lote")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LineaVentaLote {
    /** Clave compuesta. */
    @EmbeddedId
    private LineaVentaLoteId id;

    /** Cantidad tomada del lote. */
    @Column(name = "cantidad", nullable = false, precision = 14, scale = 4)
    private BigDecimal cantidad;
}
