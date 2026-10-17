/*
 * Nombre: FlujoOperativoIT.java
 * Descripción: Prueba de integración del flujo completo: producto → compra → recepción con lote → precio propuesto y aprobado → venta con IVA y descuento → detalle → devolución → ajuste → auditoría (RF-01…RF-20).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Casos IT-FLU-01 … IT-FLU-03. */
class FlujoOperativoIT extends BaseIT {

    private ResultActions postJson(String url, String rol, Object cuerpo) throws Exception {
        return mvc.perform(post(url).header("Authorization", bearer(rol)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)));
    }

    private JsonNode leer(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    @Test @DisplayName("IT-FLU-01 compra → recepción con lote y vencimiento → existencias, lote, costo y auditoría")
    void compraYRecepcion() throws Exception {
        String codigo = codigoUnico();
        JsonNode prod = leer(postJson("/api/productos", "Inventario", Map.of("nombre", "Pegante", "marca", "Sika",
                "presentacion", "P" + codigo, "codigoBarras", codigo, "unidadBase", "unidad", "tipoRastreo", "LOTE",
                "tieneVencimiento", true, "tieneGarantia", false, "porcentajeIva", 19, "porcentajeIncremento", 0.30))
                .andExpect(status().isCreated()));
        long idProducto = prod.get("id").asLong();
        long idProveedor = proveedor().getId();
        JsonNode compra = leer(postJson("/api/compras", "Compras", Map.of("proveedorId", idProveedor,
                "lineas", List.of(Map.of("productoId", idProducto, "cantidad", 50, "costoUnitario", 10000))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estadoRecepcion").value("PENDIENTE")));
        assertThat(disponible(idProducto)).isEqualByComparingTo("0");   // la compra no mueve inventario (RF-03)
        long idCompra = compra.get("id").asLong();
        long idLinea = compra.get("lineas").get(0).get("id").asLong();
        String vence = LocalDate.now().plusMonths(6).toString();
        String op = "rec-" + codigo;
        Map<String, Object> recepcion = Map.of("facturaProveedor", "FP-" + codigo, "idOperacion", op,
                "lineas", List.of(Map.of("idLineaCompra", idLinea, "cantidad", 20, "codigoLote", "L-" + codigo, "fechaVencimiento", vence)));
        postJson("/api/compras/" + idCompra + "/recepciones", "Inventario", recepcion).andExpect(status().isCreated())
                .andExpect(jsonPath("$.estadoRecepcion").value("PARCIAL"))
                .andExpect(jsonPath("$.documentos", hasSize(1)))
                .andExpect(jsonPath("$.documentos[0].recepciones[0].lote").value("L-" + codigo));
        // Reintento de la misma recepción: no suma dos veces (RF-17)
        postJson("/api/compras/" + idCompra + "/recepciones", "Inventario", recepcion).andExpect(status().isCreated());
        assertThat(disponible(idProducto)).isEqualByComparingTo("20");
        mvc.perform(get("/api/inventario/productos/" + idProducto + "/lotes").header("Authorization", bearer("Ventas")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].fechaVencimiento").value(vence))
                .andExpect(jsonPath("$[0].disponible").value(20));
        BigDecimal costo = jdbc.queryForObject("select costo_promedio from producto where id_producto = ?", BigDecimal.class, idProducto);
        assertThat(costo).isEqualByComparingTo("10000");
        // Se generó automáticamente la propuesta de precio (no vigente) con recargo 30 %
        mvc.perform(get("/api/precios/productos/" + idProducto).header("Authorization", bearer("Gerencia")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido[0].precioPropuesto").value(13000))
                .andExpect(jsonPath("$.contenido[0].estado").value("propuesto"));
        // Recepción mayor a lo pendiente → 409
        postJson("/api/compras/" + idCompra + "/recepciones", "Inventario", Map.of("facturaProveedor", "FP2",
                "lineas", List.of(Map.of("idLineaCompra", idLinea, "cantidad", 31, "codigoLote", "L-" + codigo,
                        "fechaVencimiento", vence)))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RECEPCION_EXCEDE"));
        // Ventas no registra compras (RF-09)
        postJson("/api/compras", "Ventas", Map.of("proveedorId", idProveedor, "lineas",
                List.of(Map.of("productoId", idProducto, "cantidad", 1, "costoUnitario", 1)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/auditoria/eventos").param("modulo", "COMPRAS").param("accion", "RECEPCION")
                .header("Authorization", bearer("Gerencia"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(greaterThanOrEqualTo(1)));
    }

    @Test @DisplayName("IT-FLU-02 precio aprobado → venta con IVA y descuento → detalle persistido → devolución parcial")
    void ventaDetalleDevolucion() throws Exception {
        var p = productoConStock("10", null);
        jdbc.update("update producto set porcentaje_iva = 19, costo_promedio = 1000 where id_producto = ?", p.getId());
        JsonNode prop = leer(postJson("/api/precios/propuestas", "Inventario", Map.of("productoId", p.getId(),
                "modo", "recargo", "porcentaje", 0.5)).andExpect(status().isCreated()));
        long idPrecio = prop.get("id").asLong();
        postJson("/api/precios/propuestas/" + idPrecio + "/aprobar", "Inventario", Map.of()).andExpect(status().isForbidden());
        postJson("/api/precios/propuestas/" + idPrecio + "/aprobar", "Gerencia", Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.precioVigente").value(1500));
        long cliente = cliente().getId();
        JsonNode venta = leer(postJson("/api/ventas", "Ventas", Map.of("clienteId", cliente, "lineas",
                List.of(Map.of("productoId", p.getId(), "cantidad", 2, "porcentajeDescuento", 10))))
                .andExpect(status().isCreated()));
        // 2 x 1.500 = 3.000; −10 % = 2.700; IVA 19 % = 513; total 3.213
        assertThat(venta.get("total").decimalValue()).isEqualByComparingTo("3213");
        long idVenta = venta.get("id").asLong();
        // Un cambio de precio posterior no altera la venta histórica (RF-19)
        jdbc.update("update precio set precio_vigente = 9999, precio_propuesto = 9999 where id_precio = ?", idPrecio);
        JsonNode detalle = leer(mvc.perform(get("/api/ventas/" + idVenta + "/detalle").header("Authorization", bearer("Gerencia")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.exportable").value(true))
                .andExpect(jsonPath("$.venta.lineas[0].precioAplicado").value(1500))
                .andExpect(jsonPath("$.venta.totalImpuestos").value(513))
                .andExpect(jsonPath("$.usuarioCorreo").value("ventas@test.com"))
                .andExpect(jsonPath("$.venta.factura.validaFiscalmente").value(false)));
        assertThat(detalle.get("leyenda").asText()).contains("No constituye factura electrónica");
        long idLinea = detalle.get("venta").get("lineas").get(0).get("id").asLong();
        // Devolución parcial apta y luego exceso
        postJson("/api/devoluciones/clientes", "Ventas", Map.of("ventaId", idVenta, "motivo", "No le sirvió",
                "tratamiento", "REEMBOLSO", "lineas", List.of(Map.of("idLineaVenta", idLinea, "cantidad", 1, "estadoProducto", "APTO"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.total").value(1606.5));
        postJson("/api/devoluciones/clientes", "Ventas", Map.of("ventaId", idVenta, "motivo", "Otra",
                "tratamiento", "REEMBOLSO", "lineas", List.of(Map.of("idLineaVenta", idLinea, "cantidad", 2, "estadoProducto", "APTO"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("DEVOLUCION_EXCEDE"));
        assertThat(disponible(p.getId())).isEqualByComparingTo("9");
        mvc.perform(get("/api/ventas/" + idVenta + "/detalle").header("Authorization", bearer("Ventas")))
                .andExpect(jsonPath("$.devoluciones", hasSize(1)))
                .andExpect(jsonPath("$.venta.lineas[0].cantidadDevuelta").value(1))
                .andExpect(jsonPath("$.venta.total").value(3213));   // la factura original no cambia
        // Importes globales manuales no se aceptan
        postJson("/api/ventas", "Ventas", Map.of("clienteId", cliente, "impuestos", 5,
                "lineas", List.of(Map.of("productoId", p.getId(), "cantidad", 1)))).andExpect(status().isBadRequest());
    }

    @Test @DisplayName("IT-FLU-03 ajuste: pendiente sin efecto, aprobado por otro usuario, trazado en movimientos y eventos")
    void ajusteTrazable() throws Exception {
        var p = productoConStock("5", null);
        JsonNode aj = leer(postJson("/api/ajustes", "Inventario", Map.of("productoId", p.getId(), "tipo", "ENTRADA",
                "cantidad", 3, "motivo", "Sobrante encontrado")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE")));
        assertThat(disponible(p.getId())).isEqualByComparingTo("5");
        postJson("/api/ajustes/" + aj.get("id").asLong() + "/aprobar", "Ventas", Map.of()).andExpect(status().isForbidden());
        postJson("/api/ajustes/" + aj.get("id").asLong() + "/aprobar", "Gerencia", Map.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"));
        assertThat(disponible(p.getId())).isEqualByComparingTo("8");
        mvc.perform(get("/api/auditoria/movimientos").param("idProducto", p.getId().toString()).param("tipo", "AJUSTE")
                .header("Authorization", bearer("Gerencia"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].valorAnterior").value(5))
                .andExpect(jsonPath("$.contenido[0].valorNuevo").value(8))
                .andExpect(jsonPath("$.contenido[0].documentoRelacionado").value(aj.get("numero").asText()));
        mvc.perform(get("/api/auditoria/eventos").param("q", aj.get("numero").asText()).header("Authorization", bearer("Inventario")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(2));   // solicitar + aprobar
        mvc.perform(get("/api/auditoria/eventos").header("Authorization", bearer("Ventas"))).andExpect(status().isForbidden());
    }
}
