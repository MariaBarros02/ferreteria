/*
 * Nombre: TasaCambioRepository.java
 * Descripción: Repositorio de tasas de cambio (RF-23): tasa vigente a una fecha e historial.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.repository;

import com.ferreteria.inventario.domain.model.TasaCambio;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a la tabla tasa_cambio (solo inserción y lectura). */
public interface TasaCambioRepository extends JpaRepository<TasaCambio, Long> {

    /**
     * Tasa vigente en una fecha: la de fecha de vigencia más reciente que no sea posterior a la fecha;
     * si hay varias ese día, la última registrada.
     *
     * @param codigo moneda
     * @param fecha fecha de referencia
     * @return tasa vigente
     */
    Optional<TasaCambio> findFirstByCodigoMonedaAndFechaVigenciaLessThanEqualOrderByFechaVigenciaDescIdDesc(
            String codigo, LocalDate fecha);

    /**
     * Historial de tasas de una moneda, de la más reciente a la más antigua.
     *
     * @param codigo moneda
     * @param pageable paginación
     * @return página
     */
    Page<TasaCambio> findByCodigoMonedaOrderByFechaVigenciaDescIdDesc(String codigo, Pageable pageable);
}
