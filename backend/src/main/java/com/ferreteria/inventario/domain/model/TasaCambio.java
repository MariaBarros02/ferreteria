/*
 * Nombre: TasaCambio.java
 * Descripción: Entidad JPA de tasa de cambio (RF-23): pesos colombianos por una unidad de la moneda, con fecha de vigencia, fuente y usuario. Inmutable (disparador en BD).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tasa de cambio a la moneda funcional. Una corrección se registra como una tasa nueva. */
@Entity
@Table(name = "tasa_cambio")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TasaCambio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tasa")
    private Long id;

    @Column(name = "codigo_moneda", nullable = false, length = 3, columnDefinition = "bpchar(3)")
    private String codigoMoneda;

    /** Unidades de moneda funcional (COP) por 1 unidad de la moneda. */
    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal tasa;

    @Column(name = "fecha_vigencia", nullable = false)
    private LocalDate fechaVigencia;

    @Column(length = 100)
    private String fuente;

    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;
}
