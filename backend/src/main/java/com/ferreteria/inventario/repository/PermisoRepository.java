/*
 * Nombre: PermisoRepository.java
 * Descripción: Repositorio JPA de la matriz de permisos.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.repository;

import com.ferreteria.inventario.domain.model.Permiso;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a datos de {@link Permiso}.
 */
public interface PermisoRepository extends JpaRepository<Permiso, Integer> {
    /**
     * Permisos de un rol.
     *
     * @param idRol rol
     * @return permisos
     */
    List<Permiso> findByIdRolOrderByModuloAsc(Integer idRol);

}
