/*
 * Nombre: ConteoFisicoRepository.java
 * Descripción: Repositorio JPA de conteos físicos.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.repository;

import com.ferreteria.inventario.domain.model.ConteoFisico;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a datos de {@link ConteoFisico}.
 */
public interface ConteoFisicoRepository extends JpaRepository<ConteoFisico, Long> {
    /**
     * Conteos de una sesión.
     *
     * @param idSesion sesión
     * @return conteos
     */
    List<ConteoFisico> findByIdSesionOrderByIdAsc(Long idSesion);

    /**
     * Conteo de un producto en una sesión.
     *
     * @param idSesion sesión
     * @param idProducto producto
     * @return conteo, si existe
     */
    Optional<ConteoFisico> findByIdSesionAndIdProducto(Long idSesion, Long idProducto);

}
