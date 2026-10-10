/*
 * Nombre: UsuarioRepository.java
 * Descripción: Repositorio Spring Data de usuarios (búsqueda por correo para autenticación).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.repository;

import com.ferreteria.inventario.domain.model.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a datos de {@link Usuario}.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    /**
     * Busca un usuario por su correo (sin distinguir mayúsculas).
     *
     * @param correo correo del usuario
     * @return usuario, si existe
     */
    Optional<Usuario> findByCorreoIgnoreCase(String correo);
}
