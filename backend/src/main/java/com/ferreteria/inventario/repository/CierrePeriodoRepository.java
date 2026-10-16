/*
 * Nombre: CierrePeriodoRepository.java
 * Descripción: Repositorio JPA de cierres mensuales.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.repository;

import com.ferreteria.inventario.domain.enums.EstadoCierre;
import com.ferreteria.inventario.domain.model.CierrePeriodo;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a datos de {@link CierrePeriodo}.
 */
public interface CierrePeriodoRepository extends JpaRepository<CierrePeriodo, Long> {
    /**
     * Cierre de un período.
     *
     * @param periodo primer día del mes
     * @return cierre, si existe
     */
    Optional<CierrePeriodo> findByPeriodo(LocalDate periodo);

    /**
     * Indica si un período está en el estado dado.
     *
     * @param periodo primer día del mes
     * @param estado estado
     * @return {@code true} si existe
     */
    boolean existsByPeriodoAndEstado(LocalDate periodo, EstadoCierre estado);

    /**
     * Bloquea el cierre de un período.
     *
     * @param periodo primer día del mes
     * @return cierre bloqueado
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CierrePeriodo c where c.periodo = :p")
    Optional<CierrePeriodo> findForUpdate(@Param("p") LocalDate periodo);

}
