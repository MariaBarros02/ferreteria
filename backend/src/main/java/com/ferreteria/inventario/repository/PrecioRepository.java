/*
 * Nombre: PrecioRepository.java
 * Descripción: Repositorio de precios: obtiene el último precio vigente aprobado de un producto (RN-07).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.repository;

import com.ferreteria.inventario.domain.model.Precio;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a datos de {@link Precio}.
 */
public interface PrecioRepository extends JpaRepository<Precio, Long> {

    /**
     * Último precio con valor vigente (solo existe tras aprobación).
     *
     * @param idProducto producto
     * @return precio vigente más reciente
     */
    Optional<Precio> findFirstByIdProductoAndPrecioVigenteIsNotNullOrderByIdDesc(Long idProducto);
}
