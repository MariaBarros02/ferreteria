/*
 * Nombre: RolRepositoryHelper.java
 * Descripción: Utilidad de pruebas: obtiene un rol por nombre desde la base de datos sembrada por Flyway.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.integration;

import com.ferreteria.inventario.domain.model.Rol;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta de roles para las pruebas (el proyecto no expone un repositorio de roles en producción).
 */
@Component
public class RolRepositoryHelper {

    @PersistenceContext
    private EntityManager em;

    /**
     * Devuelve el rol con el nombre indicado.
     *
     * @param nombre nombre del rol (p. ej. Ventas)
     * @return rol
     */
    @Transactional(readOnly = true)
    public Rol porNombre(String nombre) {
        return em.createQuery("select r from Rol r where lower(r.nombre) = lower(:n)", Rol.class)
                .setParameter("n", nombre).getSingleResult();
    }
}
