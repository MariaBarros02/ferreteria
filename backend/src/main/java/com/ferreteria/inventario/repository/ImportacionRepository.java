/*
 * Nombre: ImportacionRepository.java
 * Descripción: Repositorio JPA de importaciones de Excel.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.repository;

import com.ferreteria.inventario.domain.enums.EstadoImportacion;
import com.ferreteria.inventario.domain.model.Importacion;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a datos de {@link Importacion}.
 */
public interface ImportacionRepository extends JpaRepository<Importacion, Long> {
    /**
     * Obtiene el registro bloqueándolo ({@code SELECT ... FOR UPDATE}) para serializar operaciones concurrentes.
     *
     * @param id identificador
     * @return registro bloqueado
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select x from Importacion x where x.id = :id")
    Optional<Importacion> findForUpdate(@Param("id") Long id);

    /**
     * Indica si un archivo idéntico ya fue importado.
     *
     * @param tipo tipo de importación
     * @param hash SHA-256
     * @param estado estado importada
     * @return {@code true} si ya existe
     */
    boolean existsByTipoAndHashArchivoAndEstado(String tipo, String hash, EstadoImportacion estado);

    /**
     * Historial de importaciones.
     *
     * @param pageable paginación
     * @return página
     */
    Page<Importacion> findAllByOrderByIdDesc(Pageable pageable);

}
