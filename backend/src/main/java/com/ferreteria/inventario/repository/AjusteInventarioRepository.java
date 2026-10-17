/*
 * Nombre: AjusteInventarioRepository.java
 * Descripción: Repositorio JPA de ajustes de inventario.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.repository;

import com.ferreteria.inventario.domain.enums.EstadoAprobacion;
import com.ferreteria.inventario.domain.model.AjusteInventario;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a datos de {@link AjusteInventario}.
 */
public interface AjusteInventarioRepository extends JpaRepository<AjusteInventario, Long>, JpaSpecificationExecutor<AjusteInventario> {
    /**
     * Siguiente valor de la secuencia seq_ajuste.
     *
     * @return consecutivo
     */
    @Query(value = "select nextval('seq_ajuste')", nativeQuery = true)
    long siguienteNumero();

    /**
     * Obtiene el registro bloqueándolo ({@code SELECT ... FOR UPDATE}) para serializar operaciones concurrentes.
     *
     * @param id identificador
     * @return registro bloqueado
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select x from AjusteInventario x where x.id = :id")
    Optional<AjusteInventario> findForUpdate(@Param("id") Long id);

    /**
     * Ajuste registrado con una clave de idempotencia.
     *
     * @param idOperacion clave
     * @return ajuste, si existe
     */
    Optional<AjusteInventario> findByIdOperacion(String idOperacion);

    /**
     * Cuenta ajustes en un estado.
     *
     * @param estado estado
     * @return cantidad
     */
    long countByEstado(EstadoAprobacion estado);

    /**
     * Ajuste generado por un conteo.
     *
     * @param idConteo conteo
     * @return ajuste, si existe
     */
    Optional<AjusteInventario> findFirstByIdConteo(Long idConteo);

}
