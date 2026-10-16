/*
 * Nombre: SecurityConfig.java
 * Descripción: Seguridad HTTP: JWT sin estado, RBAC por método, CORS restringido, cabeceras seguras y errores 401/403 en JSON.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
package com.ferreteria.inventario.config;

import com.ferreteria.inventario.dto.ErrorResponse;
import com.ferreteria.inventario.security.JwtAuthFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuración de Spring Security. CSRF se deshabilita porque la API es stateless y usa token Bearer
 * en cabecera (no cookies de sesión).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Cadena de filtros de seguridad.
     *
     * @param http constructor de seguridad
     * @param jwtFilter filtro JWT
     * @param mensajes fuente de mensajes i18n
     * @param mapper serializador JSON
     * @return cadena configurada
     * @throws Exception si falla la configuración
     */
    @Bean
    SecurityFilterChain filtros(HttpSecurity http, JwtAuthFilter jwtFilter, MessageSource mensajes, ObjectMapper mapper)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(h -> h
                .frameOptions(f -> f.deny())
                .contentTypeOptions(Customizer.withDefaults())
                .referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .contentSecurityPolicy(c -> c.policyDirectives(
                    "default-src 'none'; " +
                    "script-src 'self'; " +
                    "style-src 'self'; " +
                    "img-src 'self' data:; " +
                    "font-src 'self' data:; " +
                    "connect-src 'self'; " +
                    "frame-ancestors 'none'; " +
                    "object-src 'none'; " +
                    "base-uri 'self'"
                )))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) ->
                    escribir(res, HttpServletResponse.SC_UNAUTHORIZED, "NO_AUTENTICADO", "error.no.autenticado", mensajes, mapper))
                .accessDeniedHandler((req, res, ex) ->
                    escribir(res, HttpServletResponse.SC_FORBIDDEN, "SIN_PERMISO", "error.sin.permiso", mensajes, mapper)))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void escribir(HttpServletResponse res, int estado, String codigo, String clave,
                                 MessageSource mensajes, ObjectMapper mapper) throws java.io.IOException {
        res.setStatus(estado);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        String mensaje = mensajes.getMessage(clave, null, clave, LocaleContextHolder.getLocale());
        mapper.writeValue(res.getWriter(), ErrorResponse.de(codigo, mensaje));
    }

    /**
     * CORS restringido a los orígenes configurados (app.cors.allowed-origins).
     *
     * @param origenes lista separada por comas
     * @return fuente de configuración CORS
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String origenes) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(Arrays.stream(origenes.split(",")).map(String::trim).toList());
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept-Language"));
        c.setExposedHeaders(List.of("Content-Disposition"));   // nombre de los archivos descargados
        c.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", c);
        return src;
    }
}
