/*
 * Nombre: AutenticacionIT.java
 * Descripción: Pruebas de integración de autenticación: login, bloqueo por fuerza bruta, cuentas inactivas y renovación de token.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ferreteria.inventario.domain.enums.EstadoCuenta;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Casos IT-AUT-01 … IT-AUT-08. */
class AutenticacionIT extends BaseIT {

    private ResultActions login(String correo, String clave) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("correo", correo, "password", clave))));
    }

    @Test @DisplayName("IT-AUT-01 credenciales correctas devuelven JWT y rol, sin hash")
    void loginOk() throws Exception {
        usuario("ok1@test.com", "Ventas", EstadoCuenta.ACTIVO);
        String cuerpo = login("ok1@test.com", CLAVE).andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty()).andExpect(jsonPath("$.usuario.rol").value("Ventas"))
                .andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain("passwordHash").doesNotContain("$2a$");
    }

    @Test @DisplayName("IT-AUT-02 contraseña errónea → 401 con mensaje genérico")
    void passwordMala() throws Exception {
        usuario("mala@test.com", "Ventas", EstadoCuenta.ACTIVO);
        login("mala@test.com", "incorrecta").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    @Test @DisplayName("IT-AUT-03 usuario inexistente responde igual que contraseña errónea (no enumera cuentas)")
    void usuarioInexistente() throws Exception {
        login("no-existe@test.com", "x").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    @Test @DisplayName("IT-AUT-04 tres fallos seguidos bloquean la cuenta → 429 incluso con la clave correcta")
    void bloqueoFuerzaBruta() throws Exception {
        usuario("brute@test.com", "Ventas", EstadoCuenta.ACTIVO);
        for (int i = 0; i < 3; i++) { login("brute@test.com", "mal").andExpect(status().isUnauthorized()); }
        login("brute@test.com", CLAVE).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("CUENTA_BLOQUEADA"));
    }

    @Test @DisplayName("IT-AUT-05 cuenta inactiva no inicia sesión → 403")
    void cuentaInactiva() throws Exception {
        usuario("inactivo@test.com", "Ventas", EstadoCuenta.INACTIVO);
        login("inactivo@test.com", CLAVE).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("CUENTA_INACTIVA"));
    }

    @Test @DisplayName("IT-AUT-06 renovar token con sesión vigente (permanencia)")
    void refresh() throws Exception {
        String token = tokenPara("Ventas");
        mvc.perform(post("/api/auth/refresh").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test @DisplayName("IT-AUT-07 validación del cuerpo de login (correo inválido) → 400")
    void loginInvalido() throws Exception {
        login("no-es-correo", CLAVE).andExpect(status().isBadRequest());
        login("", "").andExpect(status().isBadRequest());
    }

    @Test @DisplayName("IT-AUT-08 mensaje de credenciales inválidas en inglés (i18n)")
    void i18nLogin() throws Exception {
        mvc.perform(post("/api/auth/login").header("Accept-Language", "en").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("correo", "x@test.com", "password", "x"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.mensaje").value("Invalid credentials"));
    }
}
