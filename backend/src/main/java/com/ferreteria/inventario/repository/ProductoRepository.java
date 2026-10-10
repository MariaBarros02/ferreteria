/*
 * Nombre: ProductoRepository.java
 * Descripción: Repositorio Spring Data de productos: búsqueda por código de barras, unicidad de referencia y filtro de texto.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.repository;

import com.ferreteria.inventario.domain.model.Producto;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a datos de {@link Producto}. Todas las consultas son parametrizadas (sin concatenación SQL).
 */
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    /**
     * Busca por código de barras.
     *
     * @param codigoBarras código exacto
     * @return producto, si existe
     */
    Optional<Producto> findByCodigoBarras(String codigoBarras);

    /**
     * Indica si existe la combinación producto + marca + presentación (RN-01).
     *
     * @param nombre nombre
     * @param marca marca
     * @param presentacion presentación
     * @return {@code true} si ya existe
     */
    boolean existsByNombreIgnoreCaseAndMarcaIgnoreCaseAndPresentacionIgnoreCase(
            String nombre, String marca, String presentacion);

    /**
     * Busca la referencia con la misma combinación producto+marca+presentación.
     *
     * @param nombre nombre
     * @param marca marca
     * @param presentacion presentación
     * @return coincidencia, si existe
     */
    Optional<Producto> findByNombreIgnoreCaseAndMarcaIgnoreCaseAndPresentacionIgnoreCase(
            String nombre, String marca, String presentacion);

    /**
     * Búsqueda paginada por texto libre en nombre, marca, presentación o código.
     *
     * @param q texto; usar cadena vacía para no filtrar (nunca nulo)
     * @param pageable paginación
     * @return página de productos
     */
    @Query("""
           select p from Producto p
           where :q = ''
              or lower(p.nombre) like lower(concat('%', :q, '%'))
              or lower(p.marca) like lower(concat('%', :q, '%'))
              or lower(p.presentacion) like lower(concat('%', :q, '%'))
              or p.codigoBarras like concat('%', :q, '%')
           """)
    Page<Producto> buscar(@Param("q") String q, Pageable pageable);
}
