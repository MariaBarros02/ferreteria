/*
 * Nombre: PrecioRepository.java
 * Descripción: Repositorio de precios: obtiene el último precio vigente aprobado de un producto (RN-07).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
package com.ferreteria.inventario.repository;

import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import java.util.List;
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

    /**
     * Precio vigente: el aprobado más recientemente (por fecha de vigencia; sin fecha al final, luego por id).
     * Evita que una propuesta antigua aprobada después quede oculta por otra más nueva aprobada antes.
     *
     * @param idProducto producto
     * @param pageable usar {@code PageRequest.of(0, 1)}
     * @return lista con a lo sumo un precio
     */
    @Query("""
           select p from Precio p where p.idProducto = :id and p.precioVigente is not null and p.estadoAprobacion = 'aprobado'
           order by p.fechaVigencia desc nulls last, p.id desc
           """)
    List<Precio> vigentes(@Param("id") Long idProducto, Pageable pageable);

    /**
     * Historial de precios de un producto, del más reciente al más antiguo.
     *
     * @param idProducto producto
     * @param pageable paginación
     * @return página
     */
    Page<Precio> findByIdProductoOrderByIdDesc(Long idProducto, Pageable pageable);

    /**
     * Precios en un estado de aprobación.
     *
     * @param estado propuesto, aprobado o rechazado
     * @param pageable paginación
     * @return página
     */
    Page<Precio> findByEstadoAprobacionOrderByIdDesc(String estado, Pageable pageable);

    /**
     * Propuestas pendientes de un producto.
     *
     * @param idProducto producto
     * @param estado estado propuesto
     * @return propuestas
     */
    List<Precio> findByIdProductoAndEstadoAprobacion(Long idProducto, String estado);

    /**
     * Cuenta propuestas en un estado.
     *
     * @param estado estado
     * @return cantidad
     */
    long countByEstadoAprobacion(String estado);

    /**
     * Bloquea una propuesta para decidirla una sola vez.
     *
     * @param id identificador
     * @return propuesta bloqueada
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Precio p where p.id = :id")
    Optional<Precio> findForUpdate(@Param("id") Long id);
}
