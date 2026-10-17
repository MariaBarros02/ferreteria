/*
 * Nombre: DocumentoIngresoRepository.java
 * Descripción: Repositorio JPA de documentos de ingreso.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.repository;

import com.ferreteria.inventario.domain.model.DocumentoIngreso;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a datos de {@link DocumentoIngreso}.
 */
public interface DocumentoIngresoRepository extends JpaRepository<DocumentoIngreso, Long> {
    /**
     * Siguiente valor de la secuencia seq_documento_ingreso.
     *
     * @return consecutivo
     */
    @Query(value = "select nextval('seq_documento_ingreso')", nativeQuery = true)
    long siguienteNumero();

    /**
     * Obtiene el registro bloqueándolo ({@code SELECT ... FOR UPDATE}) para serializar operaciones concurrentes.
     *
     * @param id identificador
     * @return registro bloqueado
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select x from DocumentoIngreso x where x.id = :id")
    Optional<DocumentoIngreso> findForUpdate(@Param("id") Long id);

    /**
     * Documento registrado con una clave de idempotencia.
     *
     * @param idOperacion clave
     * @return documento, si existe
     */
    Optional<DocumentoIngreso> findByIdOperacion(String idOperacion);

    /**
     * Documentos de una compra, del más reciente al más antiguo.
     *
     * @param idCompra compra
     * @return documentos
     */
    List<DocumentoIngreso> findByIdCompraOrderByIdDesc(Long idCompra);

}
