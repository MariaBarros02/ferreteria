/*
 * Nombre: ProductoApiIT.java
 * Descripción: Pruebas de integración del CRUD de productos: HU-01, RN-01, validación i18n, RBAC y búsqueda.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.integration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Casos IT-PRO-01 … IT-PRO-10. */
class ProductoApiIT extends BaseIT {

    private Map<String, Object> cuerpo(String codigo) {
        return Map.of("nombre", "Taladro", "marca", "M" + codigo, "presentacion", "500W", "codigoBarras", codigo,
                "unidadBase", "unidad", "tipoRastreo", "SERIE", "tieneVencimiento", false, "tieneGarantia", true);
    }

    private ResultActions crear(String token, Object body, String idioma) throws Exception {
        return mvc.perform(post("/api/productos").header("Authorization", "Bearer " + token)
                .header("Accept-Language", idioma).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    @Test @DisplayName("IT-PRO-01 crear y recuperar por código de barras (HU-01)")
    void crearYConsultarPorCodigo() throws Exception {
        String codigo = codigoUnico();
        crear(tokenPara("Inventario"), cuerpo(codigo), "es").andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigoBarras").value(codigo)).andExpect(jsonPath("$.id").isNumber());
        mvc.perform(get("/api/productos/codigo/" + codigo).header("Authorization", "Bearer " + tokenPara("Ventas")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.marca").value("M" + codigo));
    }

    @Test @DisplayName("IT-PRO-02 el producto nuevo nace con saldo en cero")
    void saldoInicialCero() throws Exception {
        String codigo = codigoUnico();
        String id = json.readTree(crear(tokenPara("Inventario"), cuerpo(codigo), "es").andReturn().getResponse()
                .getContentAsString()).get("id").asText();
        mvc.perform(get("/api/productos/" + id + "/existencias").header("Authorization", "Bearer " + tokenPara("Inventario")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.disponible").value(0));
    }

    @Test @DisplayName("IT-PRO-03 código de barras duplicado → 409 (RN-01)")
    void codigoDuplicado() throws Exception {
        String codigo = codigoUnico();
        crear(tokenPara("Inventario"), cuerpo(codigo), "es").andExpect(status().isCreated());
        Map<String, Object> otro = new java.util.HashMap<>(cuerpo(codigo));
        otro.put("marca", "OtraMarca");
        crear(tokenPara("Inventario"), otro, "es").andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CODIGO_DUPLICADO"));
    }

    @Test @DisplayName("IT-PRO-04 validación 400 con mensajes en español por defecto")
    void validacionEspanol() throws Exception {
        crear(tokenPara("Inventario"), cuerpo("123"), "es").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION_FALLIDA"))
                .andExpect(jsonPath("$.detalles.codigoBarras").value(containsString("8 y 14")));
    }

    @Test @DisplayName("IT-PRO-05 los mismos errores en inglés con Accept-Language: en (i18n)")
    void validacionIngles() throws Exception {
        crear(tokenPara("Inventario"), cuerpo("123"), "en").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles.codigoBarras").value(containsString("between 8 and 14")));
    }

    @Test @DisplayName("IT-PRO-06 el rol Ventas no puede crear productos (403, RF-09)")
    void ventasNoCrea() throws Exception {
        crear(tokenPara("Ventas"), cuerpo(codigoUnico()), "es").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
    }

    @Test @DisplayName("IT-PRO-07 actualizar un producto")
    void actualizar() throws Exception {
        String codigo = codigoUnico();
        String id = json.readTree(crear(tokenPara("Inventario"), cuerpo(codigo), "es").andReturn().getResponse()
                .getContentAsString()).get("id").asText();
        Map<String, Object> cambio = new java.util.HashMap<>(cuerpo(codigo));
        cambio.put("presentacion", "750W");
        mvc.perform(put("/api/productos/" + id).header("Authorization", "Bearer " + tokenPara("Inventario"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cambio)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.presentacion").value("750W"));
    }

    @Test @DisplayName("IT-PRO-08 eliminar: solo Administrador; producto sin movimientos → 204")
    void eliminar() throws Exception {
        String id = json.readTree(crear(tokenPara("Inventario"), cuerpo(codigoUnico()), "es").andReturn().getResponse()
                .getContentAsString()).get("id").asText();
        mvc.perform(delete("/api/productos/" + id).header("Authorization", "Bearer " + tokenPara("Inventario")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/productos/" + id).header("Authorization", "Bearer " + tokenPara("Administrador")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/productos/" + id).header("Authorization", "Bearer " + tokenPara("Administrador")))
                .andExpect(status().isNotFound());
    }

    @Test @DisplayName("IT-PRO-09 producto con existencias no se elimina → 409")
    void noEliminaConExistencias() throws Exception {
        var p = productoConStock("5", null);
        mvc.perform(delete("/api/productos/" + p.getId()).header("Authorization", "Bearer " + tokenPara("Administrador")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PRODUCTO_CON_EXISTENCIAS"));
    }

    @Test @DisplayName("IT-PRO-10 listado paginado y filtro por texto")
    void listarYBuscar() throws Exception {
        var p = productoConStock("1", null);
        mvc.perform(get("/api/productos").param("q", p.getNombre()).header("Authorization", "Bearer " + tokenPara("Ventas")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.totalElementos").value(1));
        mvc.perform(get("/api/productos").param("tamano", "101").header("Authorization", "Bearer " + tokenPara("Ventas")))
                .andExpect(status().isBadRequest());
    }
}
