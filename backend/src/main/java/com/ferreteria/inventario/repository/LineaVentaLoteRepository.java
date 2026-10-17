/*
 * Nombre: LineaVentaLoteRepository.java
 * Descripción: Repositorio JPA de la asignación de lotes por línea de venta.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.repository;

import com.ferreteria.inventario.domain.model.LineaVentaLote;
import com.ferreteria.inventario.domain.model.LineaVentaLoteId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a datos de {@link LineaVentaLote}.
 */
public interface LineaVentaLoteRepository extends JpaRepository<LineaVentaLote, LineaVentaLoteId> {
    /**
     * Asignaciones de una línea de venta.
     *
     * @param idLineaVenta línea
     * @return asignaciones
     */
    List<LineaVentaLote> findById_IdLineaVenta(Long idLineaVenta);

}
