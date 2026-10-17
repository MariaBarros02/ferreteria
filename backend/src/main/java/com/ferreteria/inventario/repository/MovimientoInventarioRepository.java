/*
 * Nombre: MovimientoInventarioRepository.java
 * Descripción: Repositorio de movimientos de inventario para auditoría paginada con filtro opcional por producto (RF-08).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
package com.ferreteria.inventario.repository;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import com.ferreteria.inventario.domain.model.MovimientoInventario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a datos de {@link MovimientoInventario}. La ordenación la define el {@link Pageable}.
 */
public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Long>,
        JpaSpecificationExecutor<MovimientoInventario> {

    /**
     * Movimientos de un producto.
     *
     * @param idProducto producto
     * @param pageable paginación y orden
     * @return página de movimientos
     */
    Page<MovimientoInventario> findByIdProducto(Long idProducto, Pageable pageable);
}
