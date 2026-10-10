/*
 * Nombre: ConversionUnidadRepository.java
 * Descripción: Repositorio de conversiones de unidad por producto (RF-02).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.repository;

import com.ferreteria.inventario.domain.model.ConversionUnidad;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a datos de {@link ConversionUnidad}.
 */
public interface ConversionUnidadRepository extends JpaRepository<ConversionUnidad, Long> {

    /**
     * Busca la conversión de una presentación.
     *
     * @param idProducto producto
     * @param unidadPresentacion presentación
     * @return conversión, si está configurada
     */
    Optional<ConversionUnidad> findByIdProductoAndUnidadPresentacionIgnoreCase(Long idProducto, String unidadPresentacion);

    /**
     * Lista las conversiones de un producto.
     *
     * @param idProducto producto
     * @return conversiones
     */
    List<ConversionUnidad> findByIdProducto(Long idProducto);

    /**
     * Elimina las conversiones de un producto.
     *
     * @param idProducto producto
     */
    void deleteByIdProducto(Long idProducto);
}
