/*
 * Nombre: MonedaRepository.java
 * Descripción: Repositorio de monedas (RF-23).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.repository;

import com.ferreteria.inventario.domain.model.Moneda;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a la tabla moneda. */
public interface MonedaRepository extends JpaRepository<Moneda, String> {

    /**
     * Monedas ordenadas: la funcional primero y luego por código.
     *
     * @return monedas
     */
    List<Moneda> findAllByOrderByFuncionalDescCodigoAsc();

    /**
     * Moneda funcional (única).
     *
     * @return moneda funcional
     */
    Optional<Moneda> findFirstByFuncionalTrue();
}
