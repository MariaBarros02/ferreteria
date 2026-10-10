/*
 * Nombre: FacturaRepository.java
 * Descripción: Repositorio de facturas (consulta por venta).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.repository;

import com.ferreteria.inventario.domain.model.Factura;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a datos de {@link Factura}.
 */
public interface FacturaRepository extends JpaRepository<Factura, Long> {

    /**
     * Factura de una venta.
     *
     * @param idVenta venta
     * @return factura, si existe
     */
    Optional<Factura> findByIdVenta(Long idVenta);
}
