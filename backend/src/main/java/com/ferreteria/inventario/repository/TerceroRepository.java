/*
 * Nombre: TerceroRepository.java
 * Descripción: Repositorio Spring Data de terceros: unicidad de identificación y búsqueda paginada.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
package com.ferreteria.inventario.repository;

import com.ferreteria.inventario.domain.model.Tercero;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a datos de {@link Tercero}.
 */
public interface TerceroRepository extends JpaRepository<Tercero, Long> {

    /**
     * Busca por número de identificación.
     *
     * @param numero identificación
     * @return tercero, si existe
     */
    Optional<Tercero> findByNumeroIdentificacion(String numero);

    /**
     * Búsqueda paginada por texto libre.
     *
     * @param q texto; usar cadena vacía para no filtrar (nunca nulo)
     * @param pageable paginación
     * @return página de terceros
     */
    @Query("""
           select t from Tercero t
           where :q = ''
              or t.numeroIdentificacion like concat('%', :q, '%')
              or lower(coalesce(t.razonSocial, '')) like lower(concat('%', :q, '%'))
              or lower(coalesce(t.primerNombre, '')) like lower(concat('%', :q, '%'))
              or lower(coalesce(t.primerApellido, '')) like lower(concat('%', :q, '%'))
           """)
    Page<Tercero> buscar(@Param("q") String q, Pageable pageable);

    /**
     * Búsqueda por texto restringida a un rol (selectores de cliente y proveedor).
     *
     * @param q texto; cadena vacía para no filtrar
     * @param clientes {@code true} para clientes
     * @param proveedores {@code true} para proveedores
     * @param pageable paginación
     * @return página de terceros con el rol pedido
     */
    @Query("""
           select t from Tercero t
           where ((:clientes = true and t.cliente = true) or (:proveedores = true and t.proveedor = true))
             and (:q = ''
              or t.numeroIdentificacion like concat('%', :q, '%')
              or lower(coalesce(t.razonSocial, '')) like lower(concat('%', :q, '%'))
              or lower(concat(coalesce(t.primerNombre, ''), ' ', coalesce(t.segundoNombre, ''), ' ',
                              coalesce(t.primerApellido, ''), ' ', coalesce(t.segundoApellido, ''))) like lower(concat('%', :q, '%'))
              or lower(t.correo) like lower(concat('%', :q, '%')))
           """)
    Page<Tercero> buscarPorRol(@Param("q") String q, @Param("clientes") boolean clientes,
                               @Param("proveedores") boolean proveedores, Pageable pageable);
}
