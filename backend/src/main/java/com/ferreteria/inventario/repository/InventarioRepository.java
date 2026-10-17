/*
 * Nombre: InventarioRepository.java
 * Descripción: Repositorio de inventario con bloqueo pesimista de fila para garantizar atomicidad en ventas concurrentes (RN-11).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
package com.ferreteria.inventario.repository;

import java.util.List;
import com.ferreteria.inventario.domain.model.Inventario;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a datos de {@link Inventario}.
 */
public interface InventarioRepository extends JpaRepository<Inventario, Long> {

    /**
     * Obtiene el saldo bloqueando la fila ({@code SELECT ... FOR UPDATE}). Dos ventas sobre el mismo
     * producto se serializan: gana la primera transacción confirmada exitosamente (RN-11).
     *
     * @param idProducto identificador del producto
     * @return saldo bloqueado
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventario i where i.idProducto = :id")
    Optional<Inventario> findForUpdate(@Param("id") Long idProducto);

    /**
     * Productos agotados (sin disponible).
     *
     * @return saldos en cero
     */
    @Query("select i from Inventario i where i.disponible = 0")
    List<Inventario> agotados();
}
