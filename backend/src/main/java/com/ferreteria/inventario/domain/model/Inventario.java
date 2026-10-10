/*
 * Nombre: Inventario.java
 * Descripción: Entidad JPA de inventario: saldos disponible, reservado y dañado por producto; nunca negativos (RN-03).
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
 * Saldo de inventario de una referencia, siempre expresado en su unidad base.
 */
@Entity
@Table(name = "inventario")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Inventario {

    /** Identificador del producto (clave primaria). */
    @Id
    @Column(name = "id_producto")
    private Long idProducto;

    /** Cantidad disponible para venta. */
    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal disponible;

    /** Cantidad reservada. */
    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal reservado;

    /** Cantidad dañada. */
    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal danado;
}
