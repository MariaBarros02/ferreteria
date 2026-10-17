/*
 * Nombre: VentaRepository.java
 * Descripción: Repositorio de ventas y generación de consecutivos mediante secuencias de PostgreSQL (RN-14).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
package com.ferreteria.inventario.repository;

import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;
import com.ferreteria.inventario.domain.model.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Acceso a datos de {@link Venta}.
 */
public interface VentaRepository extends JpaRepository<Venta, Long>, JpaSpecificationExecutor<Venta> {

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

    /**
     * Venta registrada con una clave de idempotencia.
     *
     * @param idOperacion clave
     * @return venta, si existe
     */
    Optional<Venta> findByIdOperacion(String idOperacion);

    /**
     * Venta por número interno.
     *
     * @param numero número interno
     * @return venta, si existe
     */
    Optional<Venta> findByNumeroInterno(String numero);

    /**
     * Bloquea la venta para serializar devoluciones concurrentes sobre ella.
     *
     * @param id venta
     * @return venta bloqueada
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Venta v where v.id = :id")
    Optional<Venta> findForUpdate(@Param("id") Long id);
}
