/*
 * Nombre: ReservaRepository.java
 * Descripción: Repositorio JPA de reservas.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.repository;

import com.ferreteria.inventario.domain.enums.EstadoReserva;
import com.ferreteria.inventario.domain.model.Reserva;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a datos de {@link Reserva}.
 */
public interface ReservaRepository extends JpaRepository<Reserva, Long>, JpaSpecificationExecutor<Reserva> {
    /**
     * Obtiene el registro bloqueándolo ({@code SELECT ... FOR UPDATE}) para serializar operaciones concurrentes.
     *
     * @param id identificador
     * @return registro bloqueado
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select x from Reserva x where x.id = :id")
    Optional<Reserva> findForUpdate(@Param("id") Long id);

    /**
     * Reservas activas cuya fecha límite ya pasó.
     *
     * @param estado estado activo
     * @param hoy fecha de referencia
     * @return reservas por liberar
     */
    List<Reserva> findByEstadoAndFechaLimiteBefore(EstadoReserva estado, LocalDate hoy);

}
