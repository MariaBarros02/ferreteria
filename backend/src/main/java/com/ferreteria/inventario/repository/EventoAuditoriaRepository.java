/*
 * Nombre: EventoAuditoriaRepository.java
 * Descripción: Repositorio JPA de eventos de auditoría (solo inserción y consulta).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.repository;

import com.ferreteria.inventario.domain.model.EventoAuditoria;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Acceso a datos de {@link EventoAuditoria}. No expone operaciones de edición propias: el disparador de la base de datos las rechaza.
 */
public interface EventoAuditoriaRepository extends JpaRepository<EventoAuditoria, Long>, JpaSpecificationExecutor<EventoAuditoria> {
    /**
     * Eventos de una entidad concreta, del más reciente al más antiguo.
     *
     * @param entidad entidad
     * @param idEntidad identificador
     * @return eventos
     */
    List<EventoAuditoria> findByEntidadAndIdEntidadOrderByIdDesc(String entidad, String idEntidad);

}
