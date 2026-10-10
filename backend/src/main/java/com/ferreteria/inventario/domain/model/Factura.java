/*
 * Nombre: Factura.java
 * Descripción: Entidad JPA de factura: numeración fiscal DIAN, modalidad, impuestos y descuentos (RN-14).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.model;

import com.ferreteria.inventario.domain.Convertidores;
import com.ferreteria.inventario.domain.enums.ModalidadFactura;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

/**
 * Factura asociada a una venta (relación 1 a 1).
 */
@Entity
@Table(name = "factura")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Factura {

    /** Identificador. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_factura")
    private Long id;

    /** Venta facturada. */
    @Column(name = "id_venta", nullable = false, unique = true)
    private Long idVenta;

    /** Numeración fiscal (distinta del número interno de venta). */
    @Column(name = "numeracion_dian", unique = true, length = 30)
    private String numeracionDian;

    /** Modalidad de facturación. */
    @Convert(converter = Convertidores.ModalidadFacturaConverter.class)
    @Column(nullable = false)
    private ModalidadFactura modalidad;

    /** Impuestos aplicados. */
    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal impuestos;

    /** Descuentos aplicados. */
    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal descuentos;

    /** Fecha de emisión. */
    @Column(name = "fecha_emision", nullable = false)
    private LocalDateTime fechaEmision;
}
