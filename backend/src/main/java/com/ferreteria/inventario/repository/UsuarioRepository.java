/*
 * Nombre: UsuarioRepository.java
 * Descripción: Repositorio Spring Data de usuarios (búsqueda por correo para autenticación).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
package com.ferreteria.inventario.repository;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import com.ferreteria.inventario.domain.enums.EstadoCuenta;
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

    /**
     * Cuenta usuarios de un rol en un estado (protege al último administrador activo).
     *
     * @param nombreRol nombre del rol
     * @param estado estado de la cuenta
     * @return cantidad
     */
    long countByRolNombreIgnoreCaseAndEstado(String nombreRol, EstadoCuenta estado);

    /**
     * Búsqueda paginada por nombre o correo.
     *
     * @param q texto (cadena vacía = sin filtro)
     * @param pageable paginación
     * @return página
     */
    @Query("""
           select u from Usuario u where :q = ''
              or lower(u.nombre) like lower(concat('%', :q, '%'))
              or lower(u.correo) like lower(concat('%', :q, '%'))
           """)
    Page<Usuario> buscar(@Param("q") String q, Pageable pageable);
}
