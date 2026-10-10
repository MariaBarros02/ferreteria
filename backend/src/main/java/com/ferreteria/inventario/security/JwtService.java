/*
 * Nombre: JwtService.java
 * Descripción: Servicio de emisión y validación de tokens JWT firmados con HMAC-SHA (ADR-04).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Genera y valida JWT de vida corta con el rol embebido.
 */
@Service
public class JwtService {

    private static final int LONGITUD_MINIMA_SECRETO = 32;

    private final String secreto;
    private final Duration vigencia;
    private final Clock reloj;
    private SecretKey clave;

    /**
     * Constructor.
     *
     * @param secreto secreto de firma (mínimo 32 caracteres)
     * @param minutos vigencia del token en minutos
     * @param reloj reloj (inyectable para pruebas)
     */
    public JwtService(@Value("${app.jwt.secret:}") String secreto,
                      @Value("${app.jwt.expiration-minutes:30}") long minutos,
                      Clock reloj) {
        this.secreto = secreto;
        this.vigencia = Duration.ofMinutes(minutos);
        this.reloj = reloj;
    }

    /** Valida el secreto al arrancar: falla rápido si es débil o falta. */
    @PostConstruct
    void inicializar() {
        if (secreto == null || secreto.getBytes(StandardCharsets.UTF_8).length < LONGITUD_MINIMA_SECRETO) {
            throw new IllegalStateException("app.jwt.secret debe definirse con al menos 32 caracteres (variable JWT_SECRET)");
        }
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Genera un token para el usuario.
     *
     * @param correo sujeto del token
     * @param rol rol normalizado
     * @return JWT compacto
     */
    public String generar(String correo, String rol) {
        Instant ahora = reloj.instant();
        return Jwts.builder()
                .subject(correo)
                .claim("rol", rol)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(vigencia)))
                .signWith(clave)
                .compact();
    }

    /**
     * Valida firma y expiración y devuelve los claims.
     *
     * @param token JWT
     * @return claims verificados
     * @throws JwtException si el token es inválido, alterado o expiró
     */
    public Claims validar(String token) {
        return Jwts.parser().verifyWith(clave).clock(() -> Date.from(reloj.instant())).build()
                .parseSignedClaims(token).getPayload();
    }

    /** @return vigencia en segundos */
    public long getVigenciaSegundos() {
        return vigencia.toSeconds();
    }
}
