/*
 * Nombre: VentaRepository.java
 * Descripción: Repositorio de ventas y generación de consecutivos mediante secuencias de PostgreSQL (RN-14).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.repository;

import com.ferreteria.inventario.domain.model.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Acceso a datos de {@link Venta}.
 */
public interface VentaRepository extends JpaRepository<Venta, Long> {

    /**
     * Siguiente valor de la secuencia del número interno de venta.
     *
     * @return número secuencial
     */
    @Query(value = "select nextval('seq_numero_venta')", nativeQuery = true)
    long siguienteNumeroVenta();

    /**
     * Siguiente valor de la secuencia de numeración fiscal simulada.
     *
     * @return número secuencial
     */
    @Query(value = "select nextval('seq_numeracion_dian')", nativeQuery = true)
    long siguienteNumeracionDian();
}
