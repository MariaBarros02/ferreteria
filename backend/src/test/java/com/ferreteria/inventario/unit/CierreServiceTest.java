/*
 * Nombre: CierreServiceTest.java
 * Descripción: Pruebas unitarias del cierre mensual (RF-08): resumen del período, pendientes que bloquean, conciliación con observación obligatoria, cierre, re-cierre, reapertura auditada, períodos inválidos o futuros e historial.
 * Fecha de creación: 2026-10-19
 * Fecha de actualización: 2026-10-19
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferreteria.inventario.config.ParametrosNegocio;
import com.ferreteria.inventario.domain.enums.EstadoCierre;
import com.ferreteria.inventario.domain.model.CierrePeriodo;
import com.ferreteria.inventario.domain.model.Usuario;
import com.ferreteria.inventario.dto.CierreResponse;
import com.ferreteria.inventario.dto.CierreResumenResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.*;
import com.ferreteria.inventario.service.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

/** Casos UT-CIE-01 … UT-CIE-14 (cierre mensual, RF-08). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CierreServiceTest {

    @Mock CierrePeriodoRepository cierres;
    @Mock UsuarioRepository usuarios;
    @Mock JdbcTemplate jdbc;
    @Mock InventarioRepository inventarios;
    @Mock ProductoRepository productos;
    @Mock AuditoriaService auditoria;
    @Mock EventoAuditoriaService eventos;

    static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 16, 15, 0);
    static final LocalDate SEPTIEMBRE = LocalDate.of(2026, 9, 1);
    final Actor gerencia = new Actor(5, "gerencia@ferreteria.com", "10.0.0.5");

    ParametrosNegocio parametros;
    KardexService kardex;
    CierreService servicio;
    CierrePeriodo guardado;
    final List<Map<String, Object>> diferencias = new ArrayList<>();

    @BeforeEach
    void preparar() {
        Clock reloj = Clock.fixed(Instant.parse("2026-10-16T15:00:00Z"), ZoneOffset.UTC);
        parametros = ParametrosNegocio.porDefecto();
        kardex = new KardexService(inventarios, productos, auditoria, cierres, parametros, reloj);
        servicio = new CierreService(cierres, usuarios, jdbc, kardex, eventos, parametros,
                new ObjectMapper().findAndRegisterModules());

        // Ventas: n y totales como los devuelve PostgreSQL (Long y BigDecimal)
        when(jdbc.queryForMap(contains("from venta"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(fila("n", 3L, "s", new BigDecimal("300000"), "d", new BigDecimal("10000"),
                        "i", new BigDecimal("55100"), "t", new BigDecimal("345100")));
        // Devoluciones de clientes: Integer y BigDecimal con 3 decimales (se redondea al cerrar)
        when(jdbc.queryForMap(contains("tipo = 'cliente'"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(fila("n", 1, "t", new BigDecimal("50000.255")));
        // Devoluciones a proveedor: valores nulos → 0
        when(jdbc.queryForMap(contains("tipo = 'proveedor'"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(fila("n", null, "t", null));
        // Compras recibidas: total como Double (se convierte y redondea a 2 decimales)
        when(jdbc.queryForMap(contains("from recepcion r"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(fila("n", 2L, "t", 1234.567));
        when(jdbc.queryForList(contains("from movimiento_inventario where"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(
                        fila("tipo", "venta", "estado", "disponible", "n", 3L, "u", new BigDecimal("-5")),
                        fila("tipo", "compra", "estado", null, "n", 2L, "u", 10)));
        when(jdbc.queryForList(contains("from inventario i"))).thenAnswer(a -> diferencias);
        pendientes(0L, null, 2L, 1L);

        when(cierres.findByPeriodo(any())).thenReturn(Optional.empty());
        when(cierres.findForUpdate(any())).thenReturn(Optional.empty());
        when(cierres.save(any(CierrePeriodo.class))).thenAnswer(a -> {
            CierrePeriodo c = a.getArgument(0);
            if (c.getId() == null) {
                c.setId(1L);
            }
            guardado = c;
            return c;
        });
        when(usuarios.findById(5)).thenReturn(Optional.of(Usuario.builder().id(5).correo("gerencia@ferreteria.com").build()));
    }

    /** Fila de resultado JDBC (admite valores nulos, a diferencia de Map.of). */
    private static Map<String, Object> fila(Object... claveValor) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < claveValor.length; i += 2) {
            m.put((String) claveValor[i], claveValor[i + 1]);
        }
        return m;
    }

    private void pendientes(Long ajustes, Long conteos, Long precios, Long reservas) {
        when(jdbc.queryForObject(contains("ajuste_inventario"), eq(Long.class), any(LocalDateTime.class))).thenReturn(ajustes);
        when(jdbc.queryForObject(contains("sesion_conteo"), eq(Long.class), any(LocalDateTime.class))).thenReturn(conteos);
        when(jdbc.queryForObject(contains("from precio"), eq(Long.class), any(LocalDateTime.class))).thenReturn(precios);
        when(jdbc.queryForObject(contains("from reserva"), eq(Long.class), any(LocalDate.class))).thenReturn(reservas);
    }

    private void conDiferencia() {
        diferencias.add(fila("id", 7L, "producto", "Taladro Bosch 500W", "registrado", new BigDecimal("10"), "esperado", 8));
    }

    private static String codigo(Throwable e) {
        return ((ApiException) e).getCodigo();
    }

    private CierrePeriodo cierre(LocalDate periodo, EstadoCierre estado) {
        return CierrePeriodo.builder().id(40L).periodo(periodo).estado(estado).idUsuario(5)
                .fechaCierre(LocalDateTime.of(2026, 10, 1, 12, 0)).numeroVentas(9).totalVentas(new BigDecimal("900"))
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> detalleEvento(String accion) {
        ArgumentCaptor<Map<String, Object>> c = ArgumentCaptor.forClass(Map.class);
        verify(eventos).registrar(any(), eq(Auditables.CIERRES), eq(accion), eq("CIERRE"), any(), anyString(), c.capture());
        return c.getValue();
    }

    @Test @DisplayName("UT-CIE-01 resumen de un mes abierto: ventas, devoluciones, compras, movimientos y pendientes")
    void resumenAbierto() {
        CierreResumenResponse r = servicio.resumen("2026-10");

        assertThat(r).isNotNull();
        assertThat(r.periodo()).isEqualTo("2026-10");
        assertThat(r.desde()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(r.hasta()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(r.estado()).isEqualTo("ABIERTO");
        assertThat(r.cierre()).isNull();

        assertThat(r.numeroVentas()).isEqualTo(3);
        assertThat(r.subtotalVentas()).isEqualByComparingTo("300000");
        assertThat(r.descuentosVentas()).isEqualByComparingTo("10000");
        assertThat(r.impuestosVentas()).isEqualByComparingTo("55100");
        assertThat(r.totalVentas()).isEqualByComparingTo("345100");
        assertThat(r.numeroDevolucionesClientes()).isEqualTo(1);
        assertThat(r.totalDevolucionesClientes()).isEqualByComparingTo("50000.255");
        assertThat(r.numeroDevolucionesProveedor()).isZero();
        assertThat(r.totalDevolucionesProveedor()).isEqualByComparingTo("0");
        assertThat(r.documentosIngreso()).isEqualTo(2);
        assertThat(r.valorComprasRecibidas()).isEqualByComparingTo("1234.57");

        assertThat(r.movimientos()).containsExactly(
                new CierreResumenResponse.Movimientos("venta", "disponible", 3, new BigDecimal("-5")),
                new CierreResumenResponse.Movimientos("compra", null, 2, new BigDecimal("10")));
        assertThat(r.diferenciasConciliacion()).isEmpty();
        assertThat(r.pendientes()).isEqualTo(new CierreResumenResponse.Pendientes(0, 0, 2, 1));
        assertThat(r.bloqueantes()).isEmpty();

        // El rango del mes se calcula en la zona horaria del negocio y se consulta en UTC
        ZoneId zona = parametros.getZonaHoraria();
        verify(jdbc).queryForMap(contains("from venta"), eq(Fechas.inicioUtc(LocalDate.of(2026, 10, 1), zona)),
                eq(Fechas.finExclusivoUtc(LocalDate.of(2026, 10, 31), zona)));
        verify(jdbc).queryForObject(contains("from reserva"), eq(Long.class), eq(LocalDate.of(2026, 10, 16)));
    }

    @Test @DisplayName("UT-CIE-02 ajustes y conteos sin decidir aparecen como bloqueantes con su cantidad")
    void bloqueantes() {
        pendientes(1L, 2L, 0L, 0L);
        CierreResumenResponse r = servicio.resumen("2026-10");
        assertThat(r.pendientes()).isEqualTo(new CierreResumenResponse.Pendientes(1, 2, 0, 0));
        assertThat(r.bloqueantes()).containsExactly(
                "Hay 1 ajuste(s) de inventario pendiente(s) de aprobación.",
                "Hay 2 sesión(es) de conteo físico sin aprobar o rechazar.");
    }

    @Test @DisplayName("UT-CIE-03 el resumen muestra el estado CERRADO o REABIERTO y los datos del cierre")
    void resumenConCierre() {
        when(cierres.findByPeriodo(SEPTIEMBRE)).thenReturn(Optional.of(cierre(SEPTIEMBRE, EstadoCierre.CERRADO)));
        CierreResumenResponse cerrado = servicio.resumen("2026-09");
        assertThat(cerrado.estado()).isEqualTo("CERRADO");
        assertThat(cerrado.cierre()).isNotNull();
        assertThat(cerrado.cierre().periodo()).isEqualTo("2026-09");
        assertThat(cerrado.cierre().usuario()).isEqualTo("gerencia@ferreteria.com");
        assertThat(cerrado.cierre().usuarioReapertura()).isNull();

        when(cierres.findByPeriodo(SEPTIEMBRE)).thenReturn(Optional.of(cierre(SEPTIEMBRE, EstadoCierre.REABIERTO)));
        assertThat(servicio.resumen("2026-09").estado()).isEqualTo("REABIERTO");
    }

    @Test @DisplayName("UT-CIE-04 la conciliación lista los productos cuyo saldo no coincide con sus movimientos")
    void conciliacion() {
        conDiferencia();
        CierreResumenResponse r = servicio.resumen("2026-10");
        assertThat(r.diferenciasConciliacion()).containsExactly(new CierreResumenResponse.Diferencia(7L,
                "Taladro Bosch 500W", new BigDecimal("10"), new BigDecimal("8")));
    }

    @Test @DisplayName("UT-CIE-05 cerrar un mes guarda la constancia completa, exige y limpia la observación si hay diferencias")
    void cerrarConDiferencias() {
        conDiferencia();
        CierreResponse r = servicio.cerrar("2026-09", "  Diferencia por inventario inicial  ", gerencia);

        assertThat(r).isNotNull();
        assertThat(r.periodo()).isEqualTo("2026-09");
        assertThat(r.estado()).isEqualTo(EstadoCierre.CERRADO);
        assertThat(r.usuario()).isEqualTo("gerencia@ferreteria.com");
        assertThat(r.fechaCierre()).isEqualTo(AHORA);

        assertThat(guardado.getPeriodo()).isEqualTo(SEPTIEMBRE);
        assertThat(guardado.getEstado()).isEqualTo(EstadoCierre.CERRADO);
        assertThat(guardado.getFechaCierre()).isEqualTo(AHORA);
        assertThat(guardado.getIdUsuario()).isEqualTo(5);
        assertThat(guardado.getNumeroVentas()).isEqualTo(3);
        assertThat(guardado.getTotalVentas()).isEqualByComparingTo("345100.00");
        assertThat(guardado.getTotalDevoluciones()).isEqualByComparingTo("50000.26");
        assertThat(guardado.getTotalComprasRecibidas()).isEqualByComparingTo("1234.57");
        assertThat(guardado.getNumeroMovimientos()).isEqualTo(5);
        assertThat(guardado.getObservaciones()).isEqualTo("Diferencia por inventario inicial");
        assertThat(guardado.getResumen()).contains("\"periodo\":\"2026-09\"").contains("\"numeroVentas\":3");

        assertThat(detalleEvento(Auditables.CERRAR)).containsEntry("periodo", "2026-09").containsEntry("ventas", 3L)
                .containsEntry("diferenciasConciliacion", 1).containsEntry("reapertura", false);
    }

    @Test @DisplayName("UT-CIE-06 sin diferencias se cierra sin observación; una observación en blanco se guarda vacía")
    void cerrarSinObservacion() {
        servicio.cerrar("2026-09", null, gerencia);
        assertThat(guardado.getObservaciones()).isNull();

        when(cierres.findForUpdate(SEPTIEMBRE)).thenReturn(Optional.empty());
        servicio.cerrar("2026-08", "   ", gerencia);
        assertThat(guardado.getObservaciones()).isNull();
        assertThat(guardado.getPeriodo()).isEqualTo(LocalDate.of(2026, 8, 1));
    }

    @Test @DisplayName("UT-CIE-07 con diferencias de conciliación no se cierra sin una observación que las explique")
    void diferenciaSinObservacion() {
        conDiferencia();
        assertThatThrownBy(() -> servicio.cerrar("2026-09", null, gerencia))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("CIERRE_REQUIERE_OBSERVACION"));
        assertThatThrownBy(() -> servicio.cerrar("2026-09", "   ", gerencia))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("CIERRE_REQUIERE_OBSERVACION"));
        verify(cierres, never()).save(any());
    }

    @Test @DisplayName("UT-CIE-08 con ajustes o conteos pendientes el mes no se cierra")
    void cierreConPendientes() {
        pendientes(1L, 0L, 0L, 0L);
        assertThatThrownBy(() -> servicio.cerrar("2026-09", "ok", gerencia))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("CIERRE_CON_PENDIENTES"));
        pendientes(0L, 1L, 0L, 0L);
        assertThatThrownBy(() -> servicio.cerrar("2026-09", "ok", gerencia))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("CIERRE_CON_PENDIENTES"));
        verify(cierres, never()).save(any());
    }

    @Test @DisplayName("UT-CIE-09 un mes futuro no se cierra; el mes en curso sí")
    void periodoFuturo() {
        assertThatThrownBy(() -> servicio.cerrar("2026-11", null, gerencia))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("PERIODO_FUTURO"));
        CierreResponse r = servicio.cerrar("2026-10", null, gerencia);
        assertThat(r.periodo()).isEqualTo("2026-10");
        assertThat(guardado.getPeriodo()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test @DisplayName("UT-CIE-10 un mes ya cerrado no se vuelve a cerrar; uno reabierto se re-cierra sobre el mismo registro")
    void reCierre() {
        when(cierres.findForUpdate(SEPTIEMBRE)).thenReturn(Optional.of(cierre(SEPTIEMBRE, EstadoCierre.CERRADO)));
        assertThatThrownBy(() -> servicio.cerrar("2026-09", null, gerencia))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("PERIODO_YA_CERRADO"));

        CierrePeriodo reabierto = cierre(SEPTIEMBRE, EstadoCierre.REABIERTO);
        when(cierres.findForUpdate(SEPTIEMBRE)).thenReturn(Optional.of(reabierto));
        CierreResponse r = servicio.cerrar("2026-09", null, gerencia);
        assertThat(guardado).isSameAs(reabierto);
        assertThat(r.id()).isEqualTo(40L);
        assertThat(reabierto.getEstado()).isEqualTo(EstadoCierre.CERRADO);
        assertThat(reabierto.getNumeroVentas()).isEqualTo(3);
        assertThat(detalleEvento(Auditables.CERRAR)).containsEntry("reapertura", true);
    }

    @Test @DisplayName("UT-CIE-11 reabrir deja constancia de quién, cuándo y por qué; solo se reabre un mes cerrado")
    void reabrir() {
        CierrePeriodo c = cierre(SEPTIEMBRE, EstadoCierre.CERRADO);
        when(cierres.findForUpdate(SEPTIEMBRE)).thenReturn(Optional.of(c));

        CierreResponse r = servicio.reabrir("2026-09", "  Factura registrada tarde  ", gerencia);

        assertThat(r).isNotNull();
        assertThat(r.estado()).isEqualTo(EstadoCierre.REABIERTO);
        assertThat(r.fechaReapertura()).isEqualTo(AHORA);
        assertThat(r.usuarioReapertura()).isEqualTo("gerencia@ferreteria.com");
        assertThat(r.motivoReapertura()).isEqualTo("Factura registrada tarde");
        assertThat(c.getIdUsuarioReapertura()).isEqualTo(5);
        assertThat(detalleEvento(Auditables.REABRIR)).containsEntry("motivo", "Factura registrada tarde");

        assertThatThrownBy(() -> servicio.reabrir("2026-09", "otra vez", gerencia))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("PERIODO_NO_CERRADO"));
        assertThatThrownBy(() -> servicio.reabrir("2026-07", "x", gerencia))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
    }

    @Test @DisplayName("UT-CIE-12 el período debe tener formato AAAA-MM (se toleran espacios)")
    void periodoInvalido() {
        assertThatThrownBy(() -> servicio.resumen("2026-13"))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("PERIODO_INVALIDO"));
        assertThatThrownBy(() -> servicio.resumen("septiembre"))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("PERIODO_INVALIDO"));
        assertThatThrownBy(() -> servicio.resumen(null))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("PERIODO_INVALIDO"));
        assertThatThrownBy(() -> servicio.cerrar("2026/09", null, gerencia))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("PERIODO_INVALIDO"));
        assertThat(servicio.resumen("  2026-09 ").periodo()).isEqualTo("2026-09");
    }

    @Test @DisplayName("UT-CIE-13 el historial lista los cierres del más reciente al más antiguo")
    void historial() {
        CierrePeriodo octubre = cierre(LocalDate.of(2026, 10, 1), EstadoCierre.CERRADO);
        CierrePeriodo sinUsuario = CierrePeriodo.builder().id(41L).periodo(SEPTIEMBRE).estado(EstadoCierre.REABIERTO)
                .idUsuarioReapertura(5).motivoReapertura("Ajuste").build();
        when(cierres.findAll(any(Sort.class))).thenReturn(List.of(octubre, sinUsuario));

        List<CierreResponse> l = servicio.listar();

        assertThat(l).extracting(CierreResponse::periodo).containsExactly("2026-10", "2026-09");
        assertThat(l.get(0).usuario()).isEqualTo("gerencia@ferreteria.com");
        assertThat(l.get(0).numeroVentas()).isEqualTo(9);
        assertThat(l.get(1).usuario()).isNull();
        assertThat(l.get(1).usuarioReapertura()).isEqualTo("gerencia@ferreteria.com");
        ArgumentCaptor<Sort> orden = ArgumentCaptor.forClass(Sort.class);
        verify(cierres).findAll(orden.capture());
        assertThat(orden.getValue().getOrderFor("periodo")).isNotNull();
        assertThat(orden.getValue().getOrderFor("periodo").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test @DisplayName("UT-CIE-14 si el resumen no se puede serializar, el cierre se registra igual sin el JSON")
    void resumenNoSerializable() throws Exception {
        ObjectMapper roto = mock(ObjectMapper.class);
        when(roto.writeValueAsString(any())).thenThrow(new JsonProcessingException("falla") { });
        CierreService conJsonRoto = new CierreService(cierres, usuarios, jdbc, kardex, eventos, parametros, roto);

        CierreResponse r = conJsonRoto.cerrar("2026-09", null, gerencia);

        assertThat(r.estado()).isEqualTo(EstadoCierre.CERRADO);
        assertThat(guardado.getResumen()).isNull();
    }
}