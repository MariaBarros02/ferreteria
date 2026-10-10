/*
 * Nombre: JwtServiceTest.java
 * Descripción: Pruebas unitarias de seguridad del JWT: firma, expiración, alteración y secreto débil.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.unit;
import static org.assertj.core.api.Assertions.assertThatCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ferreteria.inventario.security.JwtService;
import io.jsonwebtoken.JwtException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Casos UT-JWT-01 … UT-JWT-05. */
class JwtServiceTest {

    private static final String SECRETO = "un-secreto-de-prueba-con-mas-de-32-caracteres!";
    private static final Instant T0 = Instant.parse("2026-10-07T10:00:00Z");

    private JwtService servicio(String secreto, Clock reloj) {
        JwtService s = new JwtService(secreto, 30, reloj);
        try {
            var m = JwtService.class.getDeclaredMethod("inicializar");
            m.setAccessible(true);
            m.invoke(s);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw (RuntimeException) e.getCause();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return s;
    }

    @Test @DisplayName("UT-JWT-01 genera y valida un token con sujeto y rol")
    void generaYValida() {
        JwtService s = servicio(SECRETO, Clock.fixed(T0, ZoneOffset.UTC));
        var claims = s.validar(s.generar("a@b.com", "VENTAS"));
        assertThat(claims.getSubject()).isEqualTo("a@b.com");
        assertThat(claims.get("rol", String.class)).isEqualTo("VENTAS");
    }

    @Test @DisplayName("UT-JWT-02 un token expirado se rechaza")
    void expirado() {
        String token = servicio(SECRETO, Clock.fixed(T0, ZoneOffset.UTC)).generar("a@b.com", "VENTAS");
        JwtService despues = servicio(SECRETO, Clock.fixed(T0.plus(Duration.ofMinutes(31)), ZoneOffset.UTC));
        assertThatThrownBy(() -> despues.validar(token)).isInstanceOf(JwtException.class);
    }

    @Test @DisplayName("UT-JWT-03 un token alterado (payload) se rechaza")
    void alterado() {
        JwtService s = servicio(SECRETO, Clock.fixed(T0, ZoneOffset.UTC));
        String[] partes = s.generar("a@b.com", "VENTAS").split("\\.");
        String payloadFalso = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"a@b.com\",\"rol\":\"ADMINISTRADOR\"}".getBytes());
        assertThatThrownBy(() -> s.validar(partes[0] + "." + payloadFalso + "." + partes[2])).isInstanceOf(JwtException.class);
    }

    @Test @DisplayName("UT-JWT-04 un token firmado con otro secreto se rechaza")
    void otroSecreto() {
        String token = servicio("otro-secreto-distinto-de-mas-de-32-caracteres", Clock.systemUTC()).generar("a@b.com", "VENTAS");
        JwtService s = servicio(SECRETO, Clock.systemUTC());
        assertThatThrownBy(() -> s.validar(token)).isInstanceOf(JwtException.class);
    }

    @Test @DisplayName("UT-JWT-05 un secreto débil o ausente impide arrancar")
    void secretoDebil() {
        assertThatThrownBy(() -> servicio("corto", Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> servicio("", Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
    }


    @Test
    @DisplayName("Un secreto de exactamente 32 caracteres es válido")
    void secretoDeExactamente32CaracteresEsValido() {
        String secreto = "12345678901234567890123456789012";

        assertThatCode(() ->
                servicio(secreto, Clock.systemUTC())
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Devuelve la vigencia configurada en segundos")
    void devuelveVigenciaConfiguradaEnSegundos() {
        JwtService s = servicio(
                "12345678901234567890123456789012",
                Clock.systemUTC()
        );

        assertThat(s.getVigenciaSegundos()).isEqualTo(1800L);
    }

}
