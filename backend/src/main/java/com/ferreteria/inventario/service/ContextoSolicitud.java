/*
 * Nombre: ContextoSolicitud.java
 * Descripción: Obtiene el usuario autenticado y la IP de la petición en curso para construir el Actor de cada operación.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import com.ferreteria.inventario.domain.model.Usuario;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Contexto de seguridad de la petición actual. La IP es la dirección remota de la conexión
 * (detrás del nginx del despliegue corresponde al proxy; ver docs).
 */
@Component
@RequiredArgsConstructor
public class ContextoSolicitud {

    private final UsuarioRepository usuarios;

    /**
     * Construye el actor de la petición en curso.
     *
     * @return actor autenticado
     * @throws ApiException 401 si no hay usuario autenticado
     */
    public Actor actor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO", "error.no.autenticado");
        }
        return actorDe(auth.getName(), ip());
    }

    /**
     * Construye un actor a partir de un correo y una IP conocidos (procesos internos y pruebas).
     *
     * @param correo correo del usuario
     * @param ip IP de origen
     * @return actor
     */
    public Actor actorDe(String correo, String ip) {
        Usuario u = usuarios.findByCorreoIgnoreCase(correo).orElseThrow(() -> ApiException.noEncontrado("Usuario"));
        return new Actor(u.getId(), u.getCorreo(), ip);
    }

    /**
     * Indica si el usuario autenticado tiene alguno de los roles dados.
     *
     * @param roles nombres normalizados (p. ej. ADMINISTRADOR)
     * @return {@code true} si tiene al menos uno
     */
    public boolean tieneRol(String... roles) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream().map(a -> a.getAuthority())
                .anyMatch(a -> Arrays.stream(roles).anyMatch(r -> a.equals("ROLE_" + r)));
    }

    /**
     * IP remota de la petición en curso.
     *
     * @return IP o {@code null} fuera de una petición HTTP
     */
    public String ip() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            return req.getRemoteAddr();
        }
        return null;
    }
}
