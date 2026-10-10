/*
 * Nombre: VentaApiIT.java
 * Descripción: Pruebas de integración y de sistema de ventas: criterios de aceptación de HU-05, atomicidad (RNF-02) y concurrencia (RN-11).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ferreteria.inventario.domain.model.ConversionUnidad;
import com.ferreteria.inventario.dto.VentaRequest;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.ConversionUnidadRepository;
import com.ferreteria.inventario.repository.VentaRepository;
import com.ferreteria.inventario.service.VentaService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Casos IT-VEN-01 … IT-VEN-09. */
class VentaApiIT extends BaseIT {

    @Autowired VentaService ventaService;
    @Autowired VentaRepository ventas;
    @Autowired ConversionUnidadRepository conversiones;

    private ResultActions vender(String token, Long clienteId, Object... lineas) throws Exception {
        List<Map<String, Object>> ls = new ArrayList<>();
        for (int i = 0; i < lineas.length; i += 2) {
            ls.add(Map.of("productoId", lineas[i], "cantidad", lineas[i + 1]));
        }
        return mvc.perform(post("/api/ventas").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("clienteId", clienteId, "lineas", ls))));
    }

    @Test @DisplayName("IT-VEN-01 venta con saldo suficiente descuenta una vez y emite factura (HU-05)")
    void ventaOk() throws Exception {
        var p = productoConStock("18", "650000");
        var c = cliente();
        vender(tokenPara("Ventas"), c.getId(), p.getId(), 2).andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(1300000))
                .andExpect(jsonPath("$.factura.numeracionDian").isNotEmpty())
                .andExpect(jsonPath("$.numeroInterno").value(org.hamcrest.Matchers.startsWith("VTA-")));
        assertThat(disponible(p.getId())).isEqualByComparingTo("16");
    }

    @Test @DisplayName("IT-VEN-02 saldo insuficiente → 409, no cambia inventario ni crea factura")
    void saldoInsuficiente() throws Exception {
        var p = productoConStock("3", "1000");
        long ventasAntes = ventas.count();
        vender(tokenPara("Ventas"), cliente().getId(), p.getId(), 4).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SALDO_INSUFICIENTE"));
        assertThat(disponible(p.getId())).isEqualByComparingTo("3");
        assertThat(ventas.count()).isEqualTo(ventasAntes);
    }

    @Test @DisplayName("IT-VEN-03 atomicidad: si una línea falla, ninguna línea descuenta (todo o nada)")
    void atomicidadMultilinea() throws Exception {
        var a = productoConStock("10", "100");
        var b = productoConStock("1", "100");
        long antes = ventas.count();
        vender(tokenPara("Ventas"), cliente().getId(), a.getId(), 5, b.getId(), 2).andExpect(status().isConflict());
        assertThat(disponible(a.getId())).isEqualByComparingTo("10");
        assertThat(disponible(b.getId())).isEqualByComparingTo("1");
        assertThat(ventas.count()).isEqualTo(antes);
        Integer movs = jdbc.queryForObject("select count(*) from movimiento_inventario where id_producto in (?, ?)",
                Integer.class, a.getId(), b.getId());
        assertThat(movs).isZero();
    }

    @Test @DisplayName("IT-VEN-04 la venta genera movimiento auditado con los 8 campos (RF-08)")
    void auditoriaOchoCampos() throws Exception {
        var p = productoConStock("20", "50");
        vender(tokenPara("Ventas"), cliente().getId(), p.getId(), 2).andExpect(status().isCreated());
        mvc.perform(get("/api/auditoria").param("idProducto", p.getId().toString())
                .header("Authorization", "Bearer " + tokenPara("Gerencia")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].tipo").value("VENTA"))
                .andExpect(jsonPath("$.contenido[0].cantidad").value(-2))
                .andExpect(jsonPath("$.contenido[0].valorAnterior").value(20))
                .andExpect(jsonPath("$.contenido[0].valorNuevo").value(18))
                .andExpect(jsonPath("$.contenido[0].idUsuario").isNumber())
                .andExpect(jsonPath("$.contenido[0].ip").isNotEmpty())
                .andExpect(jsonPath("$.contenido[0].documentoRelacionado").isNotEmpty())
                .andExpect(jsonPath("$.contenido[0].fechaHora").isNotEmpty());
    }

    @Test @DisplayName("IT-VEN-05 sin precio vigente aprobado → 422 (RN-07)")
    void sinPrecio() throws Exception {
        var p = productoConStock("5", null);
        vender(tokenPara("Ventas"), cliente().getId(), p.getId(), 1).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PRECIO_NO_VIGENTE"));
        assertThat(disponible(p.getId())).isEqualByComparingTo("5");
    }

    @Test @DisplayName("IT-VEN-06 venta por presentación: 3 cajas de 100 descuentan 300 unidades (RF-02)")
    void ventaPorCajas() throws Exception {
        var p = productoConStock("300", "10");
        conversiones.save(ConversionUnidad.builder().idProducto(p.getId()).unidadPresentacion("caja")
                .factorAUnidadBase(new BigDecimal("100")).precisionDecimales(0).build());
        mvc.perform(post("/api/ventas").header("Authorization", "Bearer " + tokenPara("Ventas"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("clienteId", cliente().getId(),
                        "lineas", List.of(Map.of("productoId", p.getId(), "cantidad", 3, "unidad", "caja"))))))
                .andExpect(status().isCreated());
        assertThat(disponible(p.getId())).isEqualByComparingTo("0");
    }

    @Test @DisplayName("IT-VEN-07 solo Ventas/Administrador registran ventas; Gerencia consulta (RF-09)")
    void permisos() throws Exception {
        var p = productoConStock("5", "10");
        vender(tokenPara("Gerencia"), cliente().getId(), p.getId(), 1).andExpect(status().isForbidden());
        vender(tokenPara("Compras"), cliente().getId(), p.getId(), 1).andExpect(status().isForbidden());
        mvc.perform(get("/api/ventas").header("Authorization", "Bearer " + tokenPara("Gerencia"))).andExpect(status().isOk());
    }

    @Test @DisplayName("IT-VEN-08 CONCURRENCIA RN-11: dos ventas de 8 sobre 10 → exactamente una gana, saldo 2")
    void concurrenciaDosVentas() throws Exception {
        var p = productoConStock("10", "100");
        var c1 = cliente();
        var c2 = cliente();
        var resultados = ejecutarEnParalelo(List.of(c1.getId(), c2.getId()), p.getId(), "8");
        assertThat(resultados.exitos()).isEqualTo(1);
        assertThat(resultados.saldoInsuficiente()).isEqualTo(1);
        assertThat(disponible(p.getId())).isEqualByComparingTo("2");
    }

    @Test @DisplayName("IT-VEN-09 CARGA: 20 ventas simultáneas de 1 sobre stock 5 → 5 éxitos, 15 rechazos, saldo 0 (nunca negativo)")
    void concurrenciaMuchas() throws Exception {
        var p = productoConStock("5", "100");
        var clientes = new ArrayList<Long>();
        for (int i = 0; i < 20; i++) { clientes.add(cliente().getId()); }
        var r = ejecutarEnParalelo(clientes, p.getId(), "1");
        assertThat(r.exitos()).isEqualTo(5);
        assertThat(r.saldoInsuficiente()).isEqualTo(15);
        assertThat(r.otros()).isZero();
        assertThat(disponible(p.getId())).isEqualByComparingTo("0");
        // consistencia 4.12: saldo = inicial + suma de movimientos
        BigDecimal suma = jdbc.queryForObject("select coalesce(sum(cantidad),0) from movimiento_inventario where id_producto=?",
                BigDecimal.class, p.getId());
        assertThat(suma).isEqualByComparingTo("-5");
    }

    private record Resultado(int exitos, int saldoInsuficiente, int otros) { }

    private Resultado ejecutarEnParalelo(List<Long> clientes, Long productoId, String cantidad) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(clientes.size());
        CountDownLatch salida = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger(), sin = new AtomicInteger(), otros = new AtomicInteger();
        var futuros = new ArrayList<Future<?>>();
        usuario("vendedor-conc-" + productoId + "@test.com", "Ventas", com.ferreteria.inventario.domain.enums.EstadoCuenta.ACTIVO);
        for (Long cli : clientes) {
            futuros.add(pool.submit(() -> {
                try {
                    salida.await();
                    ventaService.registrar(new VentaRequest(cli, List.of(new VentaRequest.Linea(productoId,
                            new BigDecimal(cantidad), null)), null, null), "vendedor-conc-" + productoId + "@test.com", "127.0.0.1");
                    ok.incrementAndGet();
                } catch (ApiException e) {
                    if ("SALDO_INSUFICIENTE".equals(e.getCodigo())) { sin.incrementAndGet(); } else { otros.incrementAndGet(); }
                } catch (Exception e) {
                    otros.incrementAndGet();
                }
                return null;
            }));
        }
        salida.countDown();
        for (Future<?> f : futuros) { f.get(60, TimeUnit.SECONDS); }
        pool.shutdown();
        return new Resultado(ok.get(), sin.get(), otros.get());
    }
}
