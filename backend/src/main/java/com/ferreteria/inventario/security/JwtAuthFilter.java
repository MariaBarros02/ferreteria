/*
 * Nombre: JwtAuthFilter.java
 * Descripción: Filtro que autentica cada petición leyendo el token Bearer y verificando que la cuenta siga activa.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.security;

import com.ferreteria.inventario.domain.enums.EstadoCuenta;
import com.ferreteria.inventario.repository.UsuarioRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autenticación sin estado basada en JWT. Un token inválido deja la petición sin autenticar
 * (el punto de entrada responderá 401).
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String cabecera = request.getHeader("Authorization");
        if (cabecera != null && cabecera.startsWith(PREFIJO)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtService.validar(cabecera.substring(PREFIJO.length()).trim());
                usuarioRepository.findByCorreoIgnoreCase(claims.getSubject())
                        .filter(u -> u.getEstado() == EstadoCuenta.ACTIVO)
                        .ifPresent(u -> {
                            var auth = new UsernamePasswordAuthenticationToken(u.getCorreo(), null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + Roles.normalizar(u.getRol().getNombre()))));
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        });
            } catch (JwtException | IllegalArgumentException e) {
                logger.debug("Token rechazado: " + e.getClass().getSimpleName());
            }
        }
        chain.doFilter(request, response);
    }
}
