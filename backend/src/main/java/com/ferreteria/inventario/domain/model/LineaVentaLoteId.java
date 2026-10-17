/*
 * Nombre: LineaVentaLoteId.java
 * Descripción: Clave compuesta (línea de venta, lote) de LineaVentaLote.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.*;

/**
 * Clave compuesta de la asignación de lotes.
 */
@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class LineaVentaLoteId implements Serializable {

    /** Línea de venta. */
    @Column(name = "id_linea_venta")
    private Long idLineaVenta;

    /** Lote. */
    @Column(name = "id_lote")
    private Long idLote;
}
