/*
 * Nombre: ModulosAdministrativosIT.java
 * Descripción: Pruebas de integración de cierres (RF-08), usuarios y permisos (RF-09), importación/exportación de Excel (RF-10), conteos (RF-15), sin conexión (RF-12) y migraciones V3/V4.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

/** Casos IT-ADM-01 … IT-ADM-07. */
class ModulosAdministrativosIT extends BaseIT {

    private ResultActions postJson(String url, String rol, Object cuerpo) throws Exception {
        return mvc.perform(post(url).header("Authorization", bearer(rol)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)));
    }

    @Test @DisplayName("IT-ADM-01 cierre del mes: bloquea movimientos; la reapertura los permite; solo Gerencia cierra")
    void cierreBloqueaMovimientos() throws Exception {
        String mes = YearMonth.now(ZoneId.of("America/Bogota")).toString();
        // Deja sin pendientes el período (las demás pruebas pueden haber dejado ajustes o conteos abiertos)
        Integer gerente = idUsuario("Gerencia");
        jdbc.update("update ajuste_inventario set estado = 'rechazado', id_usuario_decide = ?, fecha_decision = now(), "
                + "motivo_rechazo = 'limpieza de prueba' where estado = 'pendiente'", gerente);
        jdbc.update("update sesion_conteo set estado = 'rechazada', id_usuario_decide = ?, fecha_decision = now() "
                + "where estado in ('abierta', 'cerrada')", gerente);
        postJson("/api/cierres", "Administrador", Map.of("periodo", mes)).andExpect(status().isForbidden());
        var p = productoConStock("5", "100");
        try {
            postJson("/api/cierres", "Gerencia", Map.of("periodo", mes, "observaciones", "Cierre de prueba"))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("CERRADO"));
            postJson("/api/cierres", "Gerencia", Map.of("periodo", mes, "observaciones", "x")).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.codigo").value("PERIODO_YA_CERRADO"));
            postJson("/api/ventas", "Ventas", Map.of("clienteId", cliente().getId(),
                    "lineas", List.of(Map.of("productoId", p.getId(), "cantidad", 1))))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PERIODO_CERRADO"));
            assertThat(disponible(p.getId())).isEqualByComparingTo("5");
        } finally {
            postJson("/api/cierres/" + mes + "/reabrir", "Gerencia", Map.of("motivo", "Fin de la prueba"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("REABIERTO"));
        }
        postJson("/api/ventas", "Ventas", Map.of("clienteId", cliente().getId(),
                "lineas", List.of(Map.of("productoId", p.getId(), "cantidad", 1)))).andExpect(status().isCreated());
        mvc.perform(get("/api/cierres/resumen").param("periodo", mes).header("Authorization", bearer("Gerencia")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("REABIERTO"));
    }

    @Test @DisplayName("IT-ADM-02 usuarios: solo Administrador gestiona; política de contraseña; cambio de rol auditado")
    void usuarios() throws Exception {
        String correo = "nuevo" + codigoUnico() + "@test.com";
        postJson("/api/usuarios", "Gerencia", Map.of("nombre", "N", "correo", correo, "rol", "Ventas", "estado", "ACTIVO",
                "password", "Segura12345")).andExpect(status().isForbidden());
        postJson("/api/usuarios", "Administrador", Map.of("nombre", "N", "correo", correo, "rol", "Ventas", "estado", "ACTIVO",
                "password", "debil")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("CLAVE_DEBIL"));
        JsonNode u = json.readTree(postJson("/api/usuarios", "Administrador", Map.of("nombre", "N", "correo", correo,
                "rol", "Ventas", "estado", "ACTIVO", "password", "Segura12345")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertThat(u.has("passwordHash")).isFalse();
        mvc.perform(put("/api/usuarios/" + u.get("id").asInt()).header("Authorization", bearer("Administrador"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("nombre", "N", "correo", correo,
                        "rol", "Compras", "estado", "ACTIVO")))).andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("Compras"));
        Integer eventos = jdbc.queryForObject("select count(*) from evento_auditoria where accion = 'CAMBIO_ROL' and id_entidad = ?",
                Integer.class, u.get("id").asText());
        assertThat(eventos).isEqualTo(1);
        Integer conClave = jdbc.queryForObject("select count(*) from evento_auditoria where detalle::text ilike '%Segura12345%'",
                Integer.class);
        assertThat(conClave).isZero();
        mvc.perform(get("/api/usuarios/roles").header("Authorization", bearer("Administrador"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == 'Gerencia')].permisos.Precios").value("aprobar"));
    }

    @Test @DisplayName("IT-ADM-03 importación Excel: validar → revisar → confirmar; reprocesar el mismo archivo se rechaza")
    void importacionExcel() throws Exception {
        String c1 = codigoUnico();
        String c2 = codigoUnico();
        byte[] archivo;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("productos");
            String[][] filas = {{"codigo_barras", "nombre", "marca", "presentacion", "unidad_base"},
                    {c1, "Martillo", "Stanley", "16 oz " + c1, "unidad"},
                    {c2, "Sierra", "Truper", "", "unidad"}};
            for (int i = 0; i < filas.length; i++) {
                Row r = s.createRow(i);
                for (int j = 0; j < filas[i].length; j++) {
                    r.createCell(j).setCellValue(filas[i][j]);
                }
            }
            wb.write(out);
            archivo = out.toByteArray();
        }
        MockMultipartFile f = new MockMultipartFile("archivo", "productos.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", archivo);
        mvc.perform(multipart("/api/importaciones/productos/validar").file(f).header("Authorization", bearer("Ventas")))
                .andExpect(status().isForbidden());
        JsonNode v = json.readTree(mvc.perform(multipart("/api/importaciones/productos/validar").file(f)
                .header("Authorization", bearer("Inventario"))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.filasValidas").value(1)).andExpect(jsonPath("$.filasError").value(1))
                .andExpect(jsonPath("$.filas[1].errores[0].columna").value("presentacion"))
                .andReturn().getResponse().getContentAsString());
        assertThat(productos.findByCodigoBarras(c1)).isEmpty();   // validar no importa
        postJson("/api/importaciones/" + v.get("id").asLong() + "/confirmar", "Inventario", Map.of())
                .andExpect(status().isOk()).andExpect(jsonPath("$.filasImportadas").value(1));
        assertThat(productos.findByCodigoBarras(c1)).isPresent();
        assertThat(productos.findByCodigoBarras(c2)).isEmpty();
        mvc.perform(multipart("/api/importaciones/productos/validar").file(f).header("Authorization", bearer("Inventario")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ARCHIVO_YA_IMPORTADO"));
        mvc.perform(get("/api/importaciones/" + v.get("id").asLong() + "/errores").header("Authorization", bearer("Inventario")))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("errores")));
    }

    @Test @DisplayName("IT-ADM-04 exportación Excel: neutraliza fórmulas y respeta permisos")
    void exportacion() throws Exception {
        var t = cliente();
        jdbc.update("update tercero set direccion = '=HYPERLINK(\"http://x\")' where id_tercero = ?", t.getId());
        byte[] xlsx = mvc.perform(get("/api/exportaciones/terceros").header("Authorization", bearer("Administrador")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            boolean neutralizada = false;
            for (Row r : wb.getSheetAt(0)) {
                if (r.getCell(5) != null && r.getCell(5).getStringCellValue().contains("HYPERLINK")) {
                    neutralizada = r.getCell(5).getStringCellValue().startsWith("'=");
                }
            }
            assertThat(neutralizada).isTrue();
        }
        mvc.perform(get("/api/exportaciones/terceros").header("Authorization", bearer("Inventario"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/exportaciones/movimientos").header("Authorization", bearer("Ventas"))).andExpect(status().isForbidden());
    }

    @Test @DisplayName("IT-ADM-05 conteo físico: no ajusta hasta aprobar; la aprobación genera el ajuste")
    void conteo() throws Exception {
        var p = productoConStock("10", null);
        JsonNode s = json.readTree(postJson("/api/conteos", "Inventario", Map.of()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        long id = s.get("id").asLong();
        postJson("/api/conteos/" + id + "/registros", "Inventario", Map.of("productoId", p.getId(), "cantidadContada", 7))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lineas[0].diferencia").value(-3));
        assertThat(disponible(p.getId())).isEqualByComparingTo("10");
        postJson("/api/conteos/" + id + "/cerrar", "Inventario", Map.of()).andExpect(status().isOk());
        postJson("/api/conteos/" + id + "/aprobar", "Inventario", Map.of()).andExpect(status().isForbidden());
        postJson("/api/conteos/" + id + "/aprobar", "Gerencia", Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA")).andExpect(jsonPath("$.lineas[0].ajuste").isNotEmpty());
        assertThat(disponible(p.getId())).isEqualByComparingTo("7");
    }

    @Test @DisplayName("IT-ADM-06 catálogo para consulta sin conexión: paginado y solo productos activos")
    void catalogoOffline() throws Exception {
        var p = productoConStock("3", "100");
        mvc.perform(get("/api/catalogo/sincronizacion").param("tamano", "500").header("Authorization", bearer("Ventas")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido").isArray());
        jdbc.update("update producto set activo = false where id_producto = ?", p.getId());
        mvc.perform(get("/api/productos/seleccion/codigo/" + p.getCodigoBarras()).header("Authorization", bearer("Ventas")))
                .andExpect(status().isNotFound());
    }

    @Test @DisplayName("IT-ADM-07 migraciones V3/V4: auditoría inmutable y nuevos tipos de movimiento disponibles")
    void migraciones() {
        Integer tipos = jdbc.queryForObject("select count(*) from pg_enum e join pg_type t on t.oid = e.enumtypid "
                + "where t.typname = 'tipo_movimiento_enum' and e.enumlabel in ('reserva','cambio_estado','baja_vencimiento')", Integer.class);
        assertThat(tipos).isEqualTo(3);
        jdbc.update("insert into evento_auditoria (modulo, accion, entidad, descripcion) values ('PRUEBA','CREAR','X','x')");
        assertThatThrownBy(() -> jdbc.update("update evento_auditoria set descripcion = 'y' where modulo = 'PRUEBA'"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("RF-08");
        assertThatThrownBy(() -> jdbc.update("delete from evento_auditoria where modulo = 'PRUEBA'"))
                .isInstanceOf(DataAccessException.class);
    }
}
