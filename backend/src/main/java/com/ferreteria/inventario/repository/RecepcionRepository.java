/*
 * Nombre: RecepcionRepository.java
 * Descripción: Repositorio JPA de recepciones.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.repository;

import com.ferreteria.inventario.domain.model.Recepcion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a datos de {@link Recepcion}.
 */
public interface RecepcionRepository extends JpaRepository<Recepcion, Long> {
    /**
     * Recepciones de un documento de ingreso.
     *
     * @param idDocumento documento
     * @return recepciones
     */
    List<Recepcion> findByIdDocumentoIngresoOrderByIdAsc(Long idDocumento);

    /**
     * Recepciones de varios documentos.
     *
     * @param ids documentos
     * @return recepciones
     */
    List<Recepcion> findByIdDocumentoIngresoIn(java.util.Collection<Long> ids);

}
