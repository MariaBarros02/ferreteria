/*
 * Nombre: RolRepository.java
 * Descripción: Repositorio JPA de roles.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.repository;

import com.ferreteria.inventario.domain.model.Rol;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a datos de {@link Rol}.
 */
public interface RolRepository extends JpaRepository<Rol, Integer> {
    /**
     * Rol por nombre.
     *
     * @param nombre nombre
     * @return rol, si existe
     */
    Optional<Rol> findByNombreIgnoreCase(String nombre);

}
