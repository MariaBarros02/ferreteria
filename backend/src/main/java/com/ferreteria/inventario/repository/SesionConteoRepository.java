/*
 * Nombre: SesionConteoRepository.java
 * Descripción: Repositorio JPA de sesiones de conteo.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.repository;

import com.ferreteria.inventario.domain.enums.EstadoSesionConteo;
import com.ferreteria.inventario.domain.model.SesionConteo;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a datos de {@link SesionConteo}.
 */
public interface SesionConteoRepository extends JpaRepository<SesionConteo, Long> {
    /**
     * Siguiente valor de la secuencia seq_conteo.
     *
     * @return consecutivo
     */
    @Query(value = "select nextval('seq_conteo')", nativeQuery = true)
    long siguienteNumero();

    /**
     * Obtiene el registro bloqueándolo ({@code SELECT ... FOR UPDATE}) para serializar operaciones concurrentes.
     *
     * @param id identificador
     * @return registro bloqueado
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select x from SesionConteo x where x.id = :id")
    Optional<SesionConteo> findForUpdate(@Param("id") Long id);

    /**
     * Sesiones de conteo paginadas.
     *
     * @param estado estado opcional
     * @param pageable paginación
     * @return página
     */
    Page<SesionConteo> findByEstado(EstadoSesionConteo estado, Pageable pageable);

    /**
     * Cuenta sesiones en los estados dados.
     *
     * @param estados estados
     * @return cantidad
     */
    long countByEstadoIn(java.util.Collection<EstadoSesionConteo> estados);

}
