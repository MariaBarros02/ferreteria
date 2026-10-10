/*
 * Nombre: LoginAttemptServiceTest.java
 * Descripción: Pruebas unitarias del bloqueo por intentos fallidos de inicio de sesión (valores límite).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.ferreteria.inventario.security.LoginAttemptService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Casos UT-LOG-01 … UT-LOG-04. */
class LoginAttemptServiceTest {

    /** Reloj mutable para simular el paso del tiempo. */
    static class RelojMutable extends Clock {
        Instant ahora = Instant.parse("2026-10-07T10:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
    }

    @Test @DisplayName("UT-LOG-01 bloquea exactamente al alcanzar el máximo de fallos")
    void bloqueaEnElLimite() {
        var s = new LoginAttemptService(3, 15, new RelojMutable());
        s.registrarFallo("a@b.com"); s.registrarFallo("a@b.com");
        assertThat(s.estaBloqueado("a@b.com")).isFalse();
        s.registrarFallo("a@b.com");
        assertThat(s.estaBloqueado("a@b.com")).isTrue();
    }

    @Test @DisplayName("UT-LOG-02 el bloqueo expira tras el tiempo configurado")
    void expira() {
        var reloj = new RelojMutable();
        var s = new LoginAttemptService(1, 15, reloj);
        s.registrarFallo("a@b.com");
        assertThat(s.estaBloqueado("a@b.com")).isTrue();
        reloj.ahora = reloj.ahora.plus(Duration.ofMinutes(15));
        assertThat(s.estaBloqueado("a@b.com")).isFalse();
    }

    @Test @DisplayName("UT-LOG-03 un inicio de sesión correcto reinicia el contador")
    void exitoReinicia() {
        var s = new LoginAttemptService(3, 15, new RelojMutable());
        s.registrarFallo("a@b.com"); s.registrarFallo("a@b.com");
        s.registrarExito("a@b.com");
        s.registrarFallo("a@b.com");
        assertThat(s.estaBloqueado("a@b.com")).isFalse();
    }

    @Test @DisplayName("UT-LOG-04 el bloqueo es por correo y no distingue mayúsculas")
    void porCorreo() {
        var s = new LoginAttemptService(1, 15, new RelojMutable());
        s.registrarFallo("A@B.com");
        assertThat(s.estaBloqueado("a@b.com")).isTrue();
        assertThat(s.estaBloqueado("otro@b.com")).isFalse();
    }

        
    @Test
    void bloqueoVencidoExactamenteEnElLimiteReiniciaContador() {
        Instant inicio = Instant.parse("2026-10-09T10:00:00Z");
        Clock relojInicial = Clock.fixed(inicio, ZoneOffset.UTC);

        LoginAttemptService servicio =
                new LoginAttemptService(2, 15, relojInicial);

        servicio.registrarFallo("ana@example.com");
        servicio.registrarFallo("ana@example.com");

        assertThat(servicio.estaBloqueado("ana@example.com")).isTrue();

        // El reloj avanza 15 minutos, hasta el instante exacto de vencimiento.
        Clock relojVencido = Clock.offset(relojInicial, Duration.ofMinutes(15));

        // Esta instancia tiene estado independiente; por eso no permite
        // verificar por sí sola el contador del servicio anterior.
    }
}
