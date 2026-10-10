/*
 * Nombre: SeguridadIT.java
 * Descripción: Pruebas de seguridad: 401/403, JWT alterado, inyección SQL, XSS, mass assignment, CORS, cabeceras y exposición de datos.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ferreteria.inventario.domain.enums.EstadoCuenta;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Casos SEG-01 … SEG-12 (OWASP: A01, A02, A03, A05, A07). */
class SeguridadIT extends BaseIT {

    @Test @DisplayName("SEG-01 sin token → 401 en JSON (A07)")
    void sinToken() throws Exception {
        mvc.perform(get("/api/productos")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test @DisplayName("SEG-02 token con payload alterado → 401")
    void tokenAlterado() throws Exception {
        String[] p = tokenPara("Ventas").split("\\.");
        String falso = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"ventas@test.com\",\"rol\":\"ADMINISTRADOR\"}".getBytes());
        mvc.perform(get("/api/productos").header("Authorization", "Bearer " + p[0] + "." + falso + "." + p[2]))
                .andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("SEG-03 token basura o con esquema distinto → 401")
    void tokenBasura() throws Exception {
        mvc.perform(get("/api/productos").header("Authorization", "Bearer abc.def.ghi")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/productos").header("Authorization", "Basic YWRtaW46YWRtaW4=")).andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("SEG-04 un token válido de un usuario luego desactivado deja de funcionar")
    void usuarioDesactivado() throws Exception {
        var u = usuario("baja@test.com", "Ventas", EstadoCuenta.ACTIVO);
        String token = jwt.generar(u.getCorreo(), "VENTAS");
        mvc.perform(get("/api/productos").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        u.setEstado(EstadoCuenta.INACTIVO);
        usuarios.save(u);
        mvc.perform(get("/api/productos").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("SEG-05 escalamiento horizontal: Ventas no accede a auditoría ni elimina (A01)")
    void escalamiento() throws Exception {
        String t = tokenPara("Ventas");
        mvc.perform(get("/api/auditoria").header("Authorization", "Bearer " + t)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/terceros/1").header("Authorization", "Bearer " + t)).andExpect(status().isForbidden());
    }

    @Test @DisplayName("SEG-06 inyección SQL en el filtro de búsqueda no altera ni rompe los datos (A03)")
    void inyeccionSql() throws Exception {
        var p = productoConStock("1", null);
        String ataque = "'; DROP TABLE producto; --";
        mvc.perform(get("/api/productos").param("q", ataque).header("Authorization", "Bearer " + tokenPara("Ventas")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/productos/codigo/{c}", "1' OR '1'='1").header("Authorization", "Bearer " + tokenPara("Ventas")))
                .andExpect(status().isBadRequest());
        assertThat(productos.findById(p.getId())).isPresent();
    }

    @Test @DisplayName("SEG-07 XSS almacenado: <script> en campos de texto se rechaza (400)")
    void xss() throws Exception {
        mvc.perform(post("/api/productos").header("Authorization", "Bearer " + tokenPara("Inventario"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "nombre", "<script>alert(1)</script>", "marca", "M", "presentacion", "P",
                        "codigoBarras", codigoUnico(), "unidadBase", "unidad", "tipoRastreo", "NINGUNO"))))
                .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("SEG-08 mass assignment: campos desconocidos en el JSON se rechazan (400)")
    void massAssignment() throws Exception {
        mvc.perform(post("/api/productos").header("Authorization", "Bearer " + tokenPara("Inventario"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"X\",\"marca\":\"M\",\"presentacion\":\"P\","
                        + "\"codigoBarras\":\"" + codigoUnico() + "\",\"unidadBase\":\"u\",\"tipoRastreo\":\"NINGUNO\",\"id\":1,\"rol\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("SEG-09 JSON mal formado → 400 sin filtrar la traza interna")
    void jsonMalo() throws Exception {
        mvc.perform(post("/api/productos").header("Authorization", "Bearer " + tokenPara("Inventario"))
                .contentType(MediaType.APPLICATION_JSON).content("{no-es-json"))
                .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.not(containsString("Exception"))));
    }

    @Test @DisplayName("SEG-10 cabeceras de seguridad presentes (A05)")
    void cabeceras() throws Exception {
        mvc.perform(get("/api/productos").header("Authorization", "Bearer " + tokenPara("Ventas")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test @DisplayName("SEG-11 CORS: un origen no permitido es rechazado")
    void cors() throws Exception {
        mvc.perform(options("/api/productos").header("Origin", "https://malicioso.example")
                .header("Access-Control-Request-Method", "GET")).andExpect(status().isForbidden());
        mvc.perform(options("/api/productos").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")).andExpect(status().isOk());
    }

    @Test @DisplayName("SEG-12 actuator sensible protegido; documentación OpenAPI y health públicos")
    void superficieExpuesta() throws Exception {
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.openapi").exists());
    }
}
