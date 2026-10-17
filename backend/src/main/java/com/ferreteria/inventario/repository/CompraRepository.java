/*
 * Nombre: CompraRepository.java
 * Descripción: Repositorio JPA de compras con numeración y filtros.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.repository;

import com.ferreteria.inventario.domain.model.Compra;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a datos de {@link Compra}.
 */
public interface CompraRepository extends JpaRepository<Compra, Long>, JpaSpecificationExecutor<Compra> {
    /**
     * Siguiente valor de la secuencia seq_numero_compra.
     *
     * @return número consecutivo
     */
    @Query(value = "select nextval('seq_numero_compra')", nativeQuery = true)
    long siguienteNumero();

    /**
     * Obtiene el registro bloqueándolo ({@code SELECT ... FOR UPDATE}) para serializar operaciones concurrentes.
     *
     * @param id identificador
     * @return registro bloqueado
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select x from Compra x where x.id = :id")
    Optional<Compra> findForUpdate(@Param("id") Long id);

    /**
     * Compra registrada con una clave de idempotencia.
     *
     * @param idOperacion clave
     * @return compra, si existe
     */
    Optional<Compra> findByIdOperacion(String idOperacion);

}
