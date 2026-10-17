/*
 * Nombre: Moneda.java
 * Descripción: Entidad JPA de moneda ISO 4217 (RF-23): código, nombre, símbolo, decimales de presentación, estado y marca de moneda funcional (COP, RN-17).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Moneda. Solo una es funcional: en ella se llevan inventario, costos, cierres y auditoría. */
@Entity
@Table(name = "moneda")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Moneda {

    /** Código ISO 4217 (tres letras mayúsculas). */
    @Id
    @Column(name = "codigo", length = 3, columnDefinition = "bpchar(3)")
    private String codigo;

    @Column(nullable = false, length = 60)
    private String nombre;

    @Column(nullable = false, length = 5)
    private String simbolo;

    /** Decimales de presentación (0 a 4). */
    @Column(nullable = false)
    private short decimales;

    @Column(nullable = false)
    @Builder.Default
    private boolean activa = true;

    /** Moneda funcional del sistema (COP). */
    @Column(nullable = false)
    private boolean funcional;
}
