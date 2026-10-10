/*
 * Nombre: LoginAttemptService.java
 * Descripción: Control de intentos fallidos de inicio de sesión con bloqueo temporal (mitiga fuerza bruta).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Cuenta fallos por correo y bloquea la cuenta tras superar el máximo configurado.
 * Almacenamiento en memoria: en un despliegue con varias réplicas debe sustituirse por un almacén compartido.
 */
@Service
public class LoginAttemptService {

    private record Estado(int fallos, Instant bloqueadoHasta) { }

    private final ConcurrentHashMap<String, Estado> estados = new ConcurrentHashMap<>();
    private final int maximo;
    private final Duration bloqueo;
    private final Clock reloj;

    /**
     * Constructor.
     *
     * @param maximo intentos fallidos permitidos
     * @param minutosBloqueo duración del bloqueo en minutos
     * @param reloj reloj inyectable
     */
    public LoginAttemptService(@Value("${app.security.max-login-attempts:5}") int maximo,
                               @Value("${app.security.lock-minutes:15}") long minutosBloqueo,
                               Clock reloj) {
        this.maximo = maximo;
        this.bloqueo = Duration.ofMinutes(minutosBloqueo);
        this.reloj = reloj;
    }

    private static String clave(String correo) {
        return correo == null ? "" : correo.toLowerCase(Locale.ROOT);
    }

    /**
     * Indica si la cuenta está bloqueada en este momento.
     *
     * @param correo correo
     * @return {@code true} si está bloqueada
     */
    public boolean estaBloqueado(String correo) {
        Estado e = estados.get(clave(correo));
        return e != null && e.bloqueadoHasta() != null && reloj.instant().isBefore(e.bloqueadoHasta());
    }

    /**
     * Registra un intento fallido; al alcanzar el máximo bloquea la cuenta.
     *
     * @param correo correo
     */
    public void registrarFallo(String correo) {
        estados.compute(clave(correo), (k, previo) -> {
            Instant ahora = reloj.instant();
            boolean vencido = previo != null && previo.bloqueadoHasta() != null
                    && !ahora.isBefore(previo.bloqueadoHasta());
            int fallos = (previo == null || vencido) ? 1 : previo.fallos() + 1;
            Instant hasta = fallos >= maximo ? ahora.plus(bloqueo) : null;
            return new Estado(fallos, hasta);
        });
    }

    /**
     * Limpia el contador tras un inicio de sesión correcto.
     *
     * @param correo correo
     */
    public void registrarExito(String correo) {
        estados.remove(clave(correo));
    }
}
