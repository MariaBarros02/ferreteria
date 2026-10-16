/*
 * Nombre: LoteRepository.java
 * Descripción: Repositorio JPA de lotes: asignación FEFO y consultas de vencimiento.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.repository;

import com.ferreteria.inventario.domain.model.Lote;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a datos de {@link Lote}.
 */
public interface LoteRepository extends JpaRepository<Lote, Long> {
    /**
     * Obtiene el registro bloqueándolo ({@code SELECT ... FOR UPDATE}) para serializar operaciones concurrentes.
     *
     * @param id identificador
     * @return registro bloqueado
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select x from Lote x where x.id = :id")
    Optional<Lote> findForUpdate(@Param("id") Long id);

    /**
     * Busca un lote por producto y código.
     *
     * @param idProducto producto
     * @param codigo código del lote
     * @return lote, si existe
     */
    Optional<Lote> findByIdProductoAndCodigoIgnoreCase(Long idProducto, String codigo);

    /**
     * Lotes de un producto, del que vence primero al último (sin fecha al final).
     *
     * @param idProducto producto
     * @return lotes
     */
    @Query("select l from Lote l where l.idProducto = :p order by l.fechaVencimiento asc nulls last, l.id asc")
    List<Lote> deProducto(@Param("p") Long idProducto);

    /**
     * Lotes vendibles (con saldo y no vencidos a la fecha) en orden FEFO, bloqueados para la asignación.
     *
     * @param idProducto producto
     * @param hoy fecha de referencia
     * @return lotes vendibles bloqueados
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           select l from Lote l where l.idProducto = :p and l.disponible > 0
              and (l.fechaVencimiento is null or l.fechaVencimiento >= :hoy)
           order by l.fechaVencimiento asc nulls last, l.id asc
           """)
    List<Lote> vendiblesParaActualizar(@Param("p") Long idProducto, @Param("hoy") LocalDate hoy);

    /**
     * Suma del saldo disponible de los lotes de un producto.
     *
     * @param idProducto producto
     * @return suma (0 si no hay lotes)
     */
    @Query("select coalesce(sum(l.disponible), 0) from Lote l where l.idProducto = :p")
    BigDecimal sumaDisponible(@Param("p") Long idProducto);

    /**
     * Suma de las unidades no aptas registradas en lotes de un producto.
     *
     * @param idProducto producto
     * @return suma (0 si no hay lotes)
     */
    @Query("select coalesce(sum(l.danado), 0) from Lote l where l.idProducto = :p")
    BigDecimal sumaDanado(@Param("p") Long idProducto);

    /**
     * Suma del saldo vencido (con fecha anterior a hoy) aún no dado de baja.
     *
     * @param idProducto producto
     * @param hoy fecha de referencia
     * @return suma vencida
     */
    @Query("select coalesce(sum(l.disponible), 0) from Lote l where l.idProducto = :p and l.fechaVencimiento < :hoy")
    BigDecimal sumaVencida(@Param("p") Long idProducto, @Param("hoy") LocalDate hoy);

    /**
     * Suma del saldo de lotes que vencen entre hoy (inclusive) y el límite (inclusive).
     *
     * @param idProducto producto
     * @param hoy fecha de referencia
     * @param limite fecha límite
     * @return suma por vencer
     */
    @Query("""
           select coalesce(sum(l.disponible), 0) from Lote l
           where l.idProducto = :p and l.fechaVencimiento >= :hoy and l.fechaVencimiento <= :limite
           """)
    BigDecimal sumaPorVencer(@Param("p") Long idProducto, @Param("hoy") LocalDate hoy, @Param("limite") LocalDate limite);

    /**
     * Lotes con saldo cuya fecha de vencimiento es anterior o igual al límite (incluye vencidos).
     *
     * @param limite fecha límite
     * @return lotes por vencer o vencidos, del más próximo al más lejano
     */
    @Query("select l from Lote l where l.disponible > 0 and l.fechaVencimiento is not null and l.fechaVencimiento <= :limite order by l.fechaVencimiento asc, l.id asc")
    List<Lote> conVencimientoHasta(@Param("limite") LocalDate limite);

}
