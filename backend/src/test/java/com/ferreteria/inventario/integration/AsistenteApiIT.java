/*
 * Nombre: AsistenteApiIT.java
 * Descripción: Pruebas de integración del asistente virtual (API real con PostgreSQL): autenticación obligatoria, temas por rol, preguntas en español e inglés, consulta de un producto real por código y validaciones.
 * Fecha de creación: 2026-11-17
 * Fecha de actualización: 2026-11-17
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.integration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ferreteria.inventario.domain.model.Producto;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Casos IT-ASI-01 … IT-ASI-06. */
class AsistenteApiIT extends BaseIT {

    private ResultActions enviar(String rol, String idioma, String texto, String nodo) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("texto", texto);
        cuerpo.put("nodo", nodo);
        return mvc.perform(post("/api/asistente/mensajes").header("Authorization", bearer(rol))
                .header("Accept-Language", idioma).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)));
    }

    @Test @DisplayName("IT-ASI-01 sin sesión el asistente responde 401")
    void requiereSesion() throws Exception {
        mvc.perform(get("/api/asistente/inicio")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/asistente/mensajes").contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"hola\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("IT-ASI-02 bienvenida con los temas del rol (Ventas no ve Precios; Gerencia sí)")
    void bienvenidaPorRol() throws Exception {
        mvc.perform(get("/api/asistente/inicio").header("Authorization", bearer("Ventas")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tipo").value("BIENVENIDA"))
                .andExpect(jsonPath("$.opciones[*].id", hasItem("ventas")))
                .andExpect(jsonPath("$.opciones[*].id", not(hasItem("precios"))));
        mvc.perform(get("/api/asistente/inicio").header("Authorization", bearer("Gerencia")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.opciones[*].id", hasItem("precios")));
    }

    @Test @DisplayName("IT-ASI-03 pregunta frecuente en español con enlace, y en inglés con Accept-Language: en")
    void preguntaFrecuente() throws Exception {
        enviar("Ventas", "es", "¿Cómo registro una venta?", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.nodo").value("venta-registrar"))
                .andExpect(jsonPath("$.texto", startsWith("1. Abre Ventas")))
                .andExpect(jsonPath("$.enlace.ruta").value("/ventas/nueva"));
        enviar("Ventas", "en", "How do I record a sale?", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.nodo").value("venta-registrar"))
                .andExpect(jsonPath("$.texto", startsWith("1. Open Sales")));
    }

    @Test @DisplayName("IT-ASI-04 rol sin permiso: aviso y sin enlace (Gerencia no registra ventas, RF-09)")
    void sinPermiso() throws Exception {
        enviar("Gerencia", "es", null, "venta-registrar").andExpect(status().isOk())
                .andExpect(jsonPath("$.texto", containsString("Tu rol no tiene permiso")))
                .andExpect(jsonPath("$.enlace").doesNotExist());
    }

    @Test @DisplayName("IT-ASI-05 consulta real por código de barras: precio vigente y existencias")
    void productoPorCodigo() throws Exception {
        Producto p = productoConStock("7", "15000");
        enviar("Ventas", "es", "¿qué precio tiene " + p.getCodigoBarras() + "?", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("PRODUCTO"))
                .andExpect(jsonPath("$.texto", containsString(p.getNombre())))
                .andExpect(jsonPath("$.texto", containsString("Precio vigente: $ 15.000 COP")))
                .andExpect(jsonPath("$.texto", containsString("Disponible: 7 unidad")));
    }

    @Test @DisplayName("IT-ASI-06 validaciones: mensaje vacío 422, tema inválido 400, < > rechazados 400, tema inexistente 404")
    void validaciones() throws Exception {
        enviar("Ventas", "es", "  ", null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("MENSAJE_VACIO"));
        enviar("Ventas", "es", null, "Tema Inválido").andExpect(status().isBadRequest());
        enviar("Ventas", "es", "<script>alert(1)</script>", null).andExpect(status().isBadRequest());
        enviar("Ventas", "es", "x".repeat(301), null).andExpect(status().isBadRequest());
        enviar("Ventas", "es", null, "no-existe").andExpect(status().isNotFound());
    }
}
