/*
 * Nombre: AjusteConteoServiceTest.java
 * Descripción: Pruebas unitarias de ajustes (RF-14) y conteos físicos (RF-15): pendiente sin efecto, aprobación atómica, segregación de funciones, diferencias y ajustes derivados.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ferreteria.inventario.config.ParametrosNegocio;
import com.ferreteria.inventario.domain.enums.*;
import com.ferreteria.inventario.domain.model.*;
import com.ferreteria.inventario.dto.AjusteRequest;
import com.ferreteria.inventario.dto.AjusteResponse;
import com.ferreteria.inventario.dto.ConteoRegistroRequest;
import com.ferreteria.inventario.dto.SesionConteoResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.*;
import com.ferreteria.inventario.service.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.ferreteria.inventario.dto.PaginaResponse;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDateTime;
import java.util.Map;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
/** Casos UT-AJU-01 … UT-AJU-05 y UT-CON-01 … UT-CON-04. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AjusteConteoServiceTest {

    @Mock AjusteInventarioRepository ajustes;
    @Mock ProductoRepository productos;
    @Mock InventarioRepository inventarios;
    @Mock MovimientoInventarioRepository movimientos;
    @Mock LoteRepository lotes;
    @Mock SesionConteoRepository sesiones;
    @Mock ConteoFisicoRepository conteos;
    @Mock UsuarioRepository usuarios;
    @Mock CierrePeriodoRepository cierres;
    @Mock AuditoriaService auditoria;
    @Mock EventoAuditoriaService eventos;

    AjusteService ajusteService;
    ConteoService conteoService;
    Inventario inv;
    final AtomicLong ids = new AtomicLong(1);
    final Actor inventario = new Actor(3, "marta.inventario@ferreteria.com", "10.0.0.3");
    final Actor gerencia = new Actor(5, "gerencia@ferreteria.com", "10.0.0.5");
    AjusteInventario guardado;
    SesionConteo sesion;
    final List<ConteoFisico> lineasConteo = new ArrayList<>();

    @BeforeEach
    void preparar() {
        Clock reloj = Clock.fixed(Instant.parse("2026-10-16T15:00:00Z"), ZoneOffset.UTC);
        KardexService kardex = new KardexService(inventarios, productos, auditoria, cierres, ParametrosNegocio.porDefecto(), reloj);
        ajusteService = new AjusteService(ajustes, productos, inventarios, movimientos, lotes, sesiones, conteos, usuarios,
                kardex, new LoteService(lotes), eventos);
        conteoService = new ConteoService(sesiones, conteos, productos, inventarios, ajustes, usuarios, ajusteService, kardex, eventos);
        inv = Inventario.builder().idProducto(2L).disponible(new BigDecimal("283")).reservado(BigDecimal.ZERO)
                .danado(BigDecimal.ZERO).build();
        when(productos.findById(2L)).thenReturn(Optional.of(Producto.builder().id(2L).nombre("Tornillo").marca("G")
                .presentacion("1").tipoRastreo(TipoRastreo.NINGUNO).build()));
        when(inventarios.findById(2L)).thenReturn(Optional.of(inv));
        when(inventarios.findForUpdate(2L)).thenReturn(Optional.of(inv));
        when(ajustes.siguienteNumero()).thenAnswer(a -> ids.incrementAndGet());
        when(ajustes.save(any(AjusteInventario.class))).thenAnswer(a -> {
            AjusteInventario x = a.getArgument(0);
            if (x.getId() == null) {
                x.setId(ids.incrementAndGet());
            }
            guardado = x;
            return x;
        });
        when(ajustes.findForUpdate(anyLong())).thenAnswer(a -> Optional.ofNullable(guardado));
        when(ajustes.findByIdOperacion(any())).thenReturn(Optional.empty());
        when(auditoria.registrar(any(), anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(a -> MovimientoInventario.builder().id(ids.incrementAndGet()).tipo(a.getArgument(0)).build());
        when(sesiones.siguienteNumero()).thenReturn(1L);
        when(sesiones.save(any(SesionConteo.class))).thenAnswer(a -> {
            SesionConteo s = a.getArgument(0);
            if (s.getId() == null) {
                s.setId(70L);
            }
            sesion = s;
            return s;
        });
        when(sesiones.findForUpdate(70L)).thenAnswer(a -> Optional.ofNullable(sesion));
        when(sesiones.findById(70L)).thenAnswer(a -> Optional.ofNullable(sesion));
        when(conteos.findByIdSesionAndIdProducto(eq(70L), anyLong())).thenAnswer(a -> lineasConteo.stream()
                .filter(c -> c.getIdProducto().equals(a.getArgument(1))).findFirst());
        when(conteos.save(any(ConteoFisico.class))).thenAnswer(a -> {
            ConteoFisico c = a.getArgument(0);
            if (c.getId() == null) {
                c.setId(ids.incrementAndGet());
                lineasConteo.add(c);
            }
            return c;
        });
        when(conteos.findByIdSesionOrderByIdAsc(70L)).thenAnswer(a -> lineasConteo);
        when(ajustes.findFirstByIdConteo(anyLong())).thenReturn(Optional.empty());
    }

    private AjusteResponse solicitar(TipoAjuste tipo, String cantidad) {
        return ajusteService.solicitar(new AjusteRequest(2L, null, tipo, new BigDecimal(cantidad), "Rotura en bodega",
                null, null, null), inventario);
    }

    @Test @DisplayName("UT-AJU-01 solicitar deja el ajuste pendiente y no modifica el saldo")
    void pendienteSinEfecto() {
        AjusteResponse r = solicitar(TipoAjuste.SALIDA, "3");
        assertThat(r.estado()).isEqualTo(EstadoAprobacion.PENDIENTE);
        assertThat(inv.getDisponible()).isEqualByComparingTo("283");
        verify(auditoria, never()).registrar(any(), anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test @DisplayName("UT-AJU-02 aprobar aplica el ajuste y registra el movimiento vinculado")
    void aprobar() {
        solicitar(TipoAjuste.SALIDA, "3");
        AjusteResponse r = ajusteService.aprobar(guardado.getId(), gerencia);
        assertThat(r.estado()).isEqualTo(EstadoAprobacion.APROBADO);
        assertThat(inv.getDisponible()).isEqualByComparingTo("280");
        assertThat(guardado.getIdMovimiento()).isNotNull();
        verify(auditoria).registrar(eq(TipoMovimiento.AJUSTE), eq(2L), eq(new BigDecimal("-3")), eq(5), any(), any(),
                eq(new BigDecimal("283")), eq(new BigDecimal("280")), any(), eq(EstadoInventario.DISPONIBLE), any(), any());
    }

    @Test @DisplayName("UT-AJU-03 quien solicita no puede aprobar su propio ajuste")
    void segregacion() {
        solicitar(TipoAjuste.ENTRADA, "3");
        assertThatThrownBy(() -> ajusteService.aprobar(guardado.getId(), inventario))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("MISMA_PERSONA"));
        assertThat(inv.getDisponible()).isEqualByComparingTo("283");
    }

    @Test @DisplayName("UT-AJU-04 una salida mayor al disponible se rechaza (nunca saldo negativo)")
    void salidaInsuficiente() {
        assertThatThrownBy(() -> solicitar(TipoAjuste.SALIDA, "284"))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("SALDO_INSUFICIENTE"));
    }

    @Test @DisplayName("UT-AJU-05 un ajuste rechazado no altera saldos y no puede volver a decidirse")
    void rechazar() {
        solicitar(TipoAjuste.ENTRADA, "3");
        ajusteService.rechazar(guardado.getId(), "No corresponde", gerencia);
        assertThat(guardado.getEstado()).isEqualTo(EstadoAprobacion.RECHAZADO);
        assertThat(inv.getDisponible()).isEqualByComparingTo("283");
        assertThatThrownBy(() -> ajusteService.aprobar(guardado.getId(), gerencia))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("AJUSTE_YA_DECIDIDO"));
    }

    @Test @DisplayName("UT-CON-01 registrar un conteo calcula la diferencia y no modifica el inventario")
    void conteoSinEfecto() {
        conteoService.abrir(null, inventario);
        SesionConteoResponse r = conteoService.registrar(70L, new ConteoRegistroRequest(2L, new BigDecimal("280"), null), inventario);
        assertThat(r.lineas().get(0).diferencia()).isEqualByComparingTo("-3");
        assertThat(r.productosConDiferencia()).isEqualTo(1);
        assertThat(inv.getDisponible()).isEqualByComparingTo("283");
    }

    @Test @DisplayName("UT-CON-02 aprobar la sesión genera el ajuste por la diferencia con tipo de movimiento conteo")
    void aprobarGeneraAjuste() {
        conteoService.abrir(null, inventario);
        conteoService.registrar(70L, new ConteoRegistroRequest(2L, new BigDecimal("280"), null), inventario);
        conteoService.cerrar(70L, inventario);
        conteoService.aprobar(70L, gerencia);
        assertThat(inv.getDisponible()).isEqualByComparingTo("280");
        assertThat(sesion.getEstado()).isEqualTo(EstadoSesionConteo.APROBADA);
        assertThat(lineasConteo.get(0).getIdMovimientoAjuste()).isNotNull();
        verify(auditoria).registrar(eq(TipoMovimiento.CONTEO), eq(2L), eq(new BigDecimal("-3")), any(), any(), any(),
                any(), any(), any(), any(), any(), any());
    }

    @Test @DisplayName("UT-CON-03 rechazar la sesión no altera saldos")
    void rechazarConteo() {
        conteoService.abrir(null, inventario);
        conteoService.registrar(70L, new ConteoRegistroRequest(2L, new BigDecimal("1"), null), inventario);
        conteoService.cerrar(70L, inventario);
        conteoService.rechazar(70L, "Recontar", gerencia);
        assertThat(inv.getDisponible()).isEqualByComparingTo("283");
        assertThat(lineasConteo.get(0).getEstado()).isEqualTo(EstadoConteo.RECHAZADO);
    }

    @Test @DisplayName("UT-CON-04 no se aprueba una sesión abierta ni la aprueba quien la abrió")
    void reglasDeAprobacion() {
        conteoService.abrir(null, inventario);
        conteoService.registrar(70L, new ConteoRegistroRequest(2L, new BigDecimal("1"), null), inventario);
        assertThatThrownBy(() -> conteoService.aprobar(70L, gerencia))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("CONTEO_ESTADO_INVALIDO"));
        conteoService.cerrar(70L, inventario);
        assertThatThrownBy(() -> conteoService.aprobar(70L, inventario))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("MISMA_PERSONA"));
    }


        // ---------------------------------------------------------------------------------------------
    // Casos para matar mutantes de PIT en AjusteService (UT-AJU-06 … UT-AJU-21)
    // ---------------------------------------------------------------------------------------------

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 16, 15, 0);

    private AjusteResponse solicitar(AjusteRequest req) {
        return ajusteService.solicitar(req, inventario);
    }

    private static AjusteRequest req(Long idLote, TipoAjuste tipo, String cantidad, String observaciones,
                                     Long idMovimientoOriginal, String idOperacion) {
        return new AjusteRequest(2L, idLote, tipo, new BigDecimal(cantidad), "Rotura en bodega", observaciones,
                idMovimientoOriginal, idOperacion);
    }

    private static String codigo(Throwable e) {
        return ((ApiException) e).getCodigo();
    }

    private void usuariosConCorreo() {
        when(usuarios.findById(3)).thenReturn(Optional.of(Usuario.builder().id(3).correo("marta.inventario@ferreteria.com").build()));
        when(usuarios.findById(5)).thenReturn(Optional.of(Usuario.builder().id(5).correo("gerencia@ferreteria.com").build()));
    }

    private Lote lote(Long idProducto) {
        Lote l = Lote.builder().id(9L).idProducto(idProducto).codigo("L-01").disponible(new BigDecimal("283")).build();
        when(lotes.findById(9L)).thenReturn(Optional.of(l));
        when(lotes.findForUpdate(9L)).thenReturn(Optional.of(l));
        when(lotes.save(any(Lote.class))).thenAnswer(a -> a.getArgument(0));
        return l;
    }

    private void productoPorLote() {
        when(productos.findById(2L)).thenReturn(Optional.of(Producto.builder().id(2L).nombre("Tornillo").marca("G")
                .presentacion("1").tipoRastreo(TipoRastreo.LOTE).build()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> detalleRegistrado(String accion) {
        ArgumentCaptor<Map<String, Object>> c = ArgumentCaptor.forClass(Map.class);
        verify(eventos).registrar(any(), any(), eq(accion), eq("AJUSTE"), any(), anyString(), c.capture());
        return c.getValue();
    }

    @Test @DisplayName("UT-AJU-06 la misma clave de operación devuelve el ajuste existente sin crear otro")
    void idempotente() {
        AjusteInventario previo = AjusteInventario.builder().id(40L).numero("AJ-000040").idProducto(2L)
                .tipo(TipoAjuste.SALIDA).cantidad(new BigDecimal("3")).estado(EstadoAprobacion.PENDIENTE).build();
        when(ajustes.findByIdOperacion("op-12345678")).thenReturn(Optional.of(previo));

        AjusteResponse r = solicitar(req(null, TipoAjuste.SALIDA, "3", null, null, "op-12345678"));

        assertThat(r).isNotNull();
        assertThat(r.id()).isEqualTo(40L);
        assertThat(r.numero()).isEqualTo("AJ-000040");
        verify(ajustes, never()).save(any(AjusteInventario.class));
    }

    @Test @DisplayName("UT-AJU-07 sin clave de operación no se consulta la idempotencia")
    void sinClaveNoConsulta() {
        solicitar(TipoAjuste.SALIDA, "3");
        verify(ajustes, never()).findByIdOperacion(any());
    }

    @Test @DisplayName("UT-AJU-08 producto, inventario o ajuste inexistentes responden 404 (NO_ENCONTRADO)")
    void inexistentes() {
        assertThatThrownBy(() -> ajusteService.solicitar(new AjusteRequest(99L, null, TipoAjuste.ENTRADA,
                BigDecimal.ONE, "x", null, null, null), inventario))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));

        when(productos.findById(98L)).thenReturn(Optional.of(Producto.builder().id(98L).nombre("Sin saldo").build()));
        assertThatThrownBy(() -> ajusteService.solicitar(new AjusteRequest(98L, null, TipoAjuste.ENTRADA,
                BigDecimal.ONE, "x", null, null, null), inventario))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));

        assertThatThrownBy(() -> ajusteService.obtener(12345L))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
    }

    @Test @DisplayName("UT-AJU-09 lote inexistente → 404; lote de otro producto → LOTE_INVALIDO; lote propio se acepta")
    void validacionDeLote() {
        assertThatThrownBy(() -> solicitar(req(9L, TipoAjuste.ENTRADA, "1", null, null, null)))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));

        lote(77L);
        assertThatThrownBy(() -> solicitar(req(9L, TipoAjuste.ENTRADA, "1", null, null, null)))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("LOTE_INVALIDO"));

        lote(2L);
        AjusteResponse r = solicitar(req(9L, TipoAjuste.ENTRADA, "1", null, null, null));
        assertThat(r.lote()).isEqualTo("L-01");
    }

    @Test @DisplayName("UT-AJU-10 movimiento original inexistente → 404; de otro producto → MOVIMIENTO_AJENO; propio se vincula")
    void movimientoOriginal() {
        assertThatThrownBy(() -> solicitar(req(null, TipoAjuste.ENTRADA, "1", null, 50L, null)))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));

        when(movimientos.findById(50L)).thenReturn(Optional.of(MovimientoInventario.builder().id(50L).idProducto(77L).build()));
        assertThatThrownBy(() -> solicitar(req(null, TipoAjuste.ENTRADA, "1", null, 50L, null)))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("MOVIMIENTO_AJENO"));

        when(movimientos.findById(50L)).thenReturn(Optional.of(MovimientoInventario.builder().id(50L).idProducto(2L).build()));
        AjusteResponse r = solicitar(req(null, TipoAjuste.ENTRADA, "1", null, 50L, null));
        assertThat(r.idMovimientoOriginal()).isEqualTo(50L);
        assertThat(detalleRegistrado(Auditables.SOLICITAR)).containsEntry("movimientoOriginal", 50L);
    }

    @Test @DisplayName("UT-AJU-11 una salida igual al disponible se puede solicitar y aprobar (deja saldo 0, nunca negativo)")
    void salidaExactaAlBorde() {
        AjusteResponse r = solicitar(TipoAjuste.SALIDA, "283");
        assertThat(r.estado()).isEqualTo(EstadoAprobacion.PENDIENTE);
        ajusteService.aprobar(guardado.getId(), gerencia);
        assertThat(inv.getDisponible()).isEqualByComparingTo("0");
    }

    @Test @DisplayName("UT-AJU-12 una entrada mayor que el disponible se aprueba (la validación de saldo solo aplica a salidas)")
    void entradaGrande() {
        solicitar(TipoAjuste.ENTRADA, "500");
        ajusteService.aprobar(guardado.getId(), gerencia);
        assertThat(inv.getDisponible()).isEqualByComparingTo("783");
    }

    @Test @DisplayName("UT-AJU-13 si el saldo bajó entre la solicitud y la aprobación, la salida se rechaza al aprobar")
    void saldoBajoAlAprobar() {
        solicitar(TipoAjuste.SALIDA, "3");
        inv.setDisponible(new BigDecimal("2"));
        assertThatThrownBy(() -> ajusteService.aprobar(guardado.getId(), gerencia))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("SALDO_INSUFICIENTE"));
        assertThat(guardado.getEstado()).isEqualTo(EstadoAprobacion.PENDIENTE);
    }

    @Test @DisplayName("UT-AJU-14 observaciones: en blanco se guardan como null y con texto se recortan")
    void observaciones() {
        assertThat(solicitar(req(null, TipoAjuste.ENTRADA, "1", "   ", null, null)).observaciones()).isNull();
        assertThat(solicitar(req(null, TipoAjuste.ENTRADA, "1", "  Caja abierta  ", null, null)).observaciones())
                .isEqualTo("Caja abierta");
    }

    @Test @DisplayName("UT-AJU-15 la respuesta muestra producto, solicitante, aprobador, fechas y saldo actual")
    void respuestaCompleta() {
        usuariosConCorreo();
        AjusteResponse s = solicitar(TipoAjuste.SALIDA, "3");
        assertThat(s.producto()).isEqualTo("Tornillo G 1");
        assertThat(s.solicitante()).isEqualTo("marta.inventario@ferreteria.com");
        assertThat(s.aprobador()).isNull();
        assertThat(s.lote()).isNull();
        assertThat(s.saldoSistema()).isEqualByComparingTo("283");

        AjusteResponse r = ajusteService.aprobar(guardado.getId(), gerencia);
        assertThat(r.aprobador()).isEqualTo("gerencia@ferreteria.com");
        assertThat(r.fechaDecision()).isEqualTo(AHORA);
        assertThat(r.saldoActual()).isEqualByComparingTo("280");
        assertThat(guardado.getIdUsuarioDecide()).isEqualTo(5);
    }

    @Test @DisplayName("UT-AJU-16 rechazar registra quién decide, cuándo y el motivo recortado")
    void rechazoCompleto() {
        usuariosConCorreo();
        solicitar(TipoAjuste.ENTRADA, "3");
        AjusteResponse r = ajusteService.rechazar(guardado.getId(), "  No corresponde  ", gerencia);

        assertThat(r).isNotNull();
        assertThat(r.estado()).isEqualTo(EstadoAprobacion.RECHAZADO);
        assertThat(r.aprobador()).isEqualTo("gerencia@ferreteria.com");
        assertThat(r.fechaDecision()).isEqualTo(AHORA);
        assertThat(r.motivoRechazo()).isEqualTo("No corresponde");
        assertThat(guardado.getIdUsuarioDecide()).isEqualTo(5);
        assertThat(detalleRegistrado(Auditables.RECHAZAR))
                .containsEntry("producto", "Tornillo G 1").containsEntry("estado", "rechazado");
    }

    @Test @DisplayName("UT-AJU-17 rechazar un ajuste cuyo producto ya no existe audita solo el motivo")
    void rechazoSinProducto() {
        solicitar(TipoAjuste.ENTRADA, "3");
        when(productos.findById(2L)).thenReturn(Optional.empty());
        ajusteService.rechazar(guardado.getId(), " Duplicado ", gerencia);
        assertThat(detalleRegistrado(Auditables.RECHAZAR)).isEqualTo(Map.of("motivo", "Duplicado"));
    }

    @Test @DisplayName("UT-AJU-18 el evento de auditoría lleva el detalle del ajuste (producto, tipo, cantidad, motivo, saldo)")
    void detalleAuditado() {
        solicitar(TipoAjuste.SALIDA, "3");
        Map<String, Object> d = detalleRegistrado(Auditables.SOLICITAR);
        assertThat(d).containsEntry("producto", "Tornillo G 1").containsEntry("tipo", TipoAjuste.SALIDA.getValor())
                .containsEntry("motivo", "Rotura en bodega").containsKey("cantidad").containsKey("saldoSistema");
        assertThat((BigDecimal) d.get("saldoSistema")).isEqualByComparingTo("283");
    }

    @Test @DisplayName("UT-AJU-19 aprobar con lote ajusta ese lote; producto inexistente al aprobar → 404")
    void aprobarConLote() {
        Lote l = lote(2L);
        solicitar(req(9L, TipoAjuste.SALIDA, "5", null, null, null));
        ajusteService.aprobar(guardado.getId(), gerencia);
        assertThat(l.getDisponible()).isEqualByComparingTo("278");
        verify(auditoria).registrar(any(), eq(2L), any(), any(), any(), any(), any(), any(), any(), any(), eq(9L), any());

        solicitar(TipoAjuste.ENTRADA, "1");
        when(productos.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> ajusteService.aprobar(guardado.getId(), gerencia))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
    }

    @Test @DisplayName("UT-AJU-20 producto por lote: una salida sin lote descuenta del lote; una entrada sin lote no toca lotes")
    void productoPorLoteSinLoteIndicado() {
        productoPorLote();
        Lote l = lote(2L);
        when(lotes.sumaDisponible(2L)).thenReturn(new BigDecimal("283"));
        when(lotes.deProducto(2L)).thenReturn(List.of(l));

        solicitar(TipoAjuste.SALIDA, "5");
        ajusteService.aprobar(guardado.getId(), gerencia);
        assertThat(l.getDisponible()).isEqualByComparingTo("278");
        verify(auditoria).registrar(any(), eq(2L), eq(new BigDecimal("-5")), any(), any(), any(), any(), any(), any(),
                any(), eq(9L), any());

        solicitar(TipoAjuste.ENTRADA, "4");
        ajusteService.aprobar(guardado.getId(), gerencia);
        assertThat(l.getDisponible()).isEqualByComparingTo("278");
        verify(auditoria).registrar(any(), eq(2L), eq(new BigDecimal("4")), any(), any(), any(), any(), any(), any(),
                any(), org.mockito.ArgumentMatchers.isNull(), any());
    }

    @Test @DisplayName("UT-AJU-21 obtener y listar: filtros por estado/producto, paginación acotada y número de conteo de origen")
    @SuppressWarnings("unchecked")
    void obtenerYListar() {
        // obtener un ajuste generado por un conteo muestra el número de la sesión de conteo
        conteoService.abrir(null, inventario);
        conteoService.registrar(70L, new ConteoRegistroRequest(2L, new BigDecimal("280"), null), inventario);
        conteoService.cerrar(70L, inventario);
        conteoService.aprobar(70L, gerencia);
        ConteoFisico linea = lineasConteo.get(0);
        when(conteos.findById(linea.getId())).thenReturn(Optional.of(linea));
        when(ajustes.findById(guardado.getId())).thenReturn(Optional.of(guardado));
        AjusteResponse r = ajusteService.obtener(guardado.getId());
        assertThat(r).isNotNull();
        assertThat(r.conteo()).isEqualTo(sesion.getNumero());

        // listar: la consulta respeta los filtros y los límites de página
        when(ajustes.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(a -> new PageImpl<>(List.of(guardado), a.getArgument(1), 1));
        PaginaResponse<AjusteResponse> p = ajusteService.listar(EstadoAprobacion.APROBADO, null, -1, 500);
        assertThat(p).isNotNull();
        assertThat(p.contenido()).hasSize(1);
        assertThat(p.pagina()).isZero();
        assertThat(p.tamano()).isEqualTo(100);

        ArgumentCaptor<Specification<AjusteInventario>> spec = ArgumentCaptor.forClass(Specification.class);
        ArgumentCaptor<Pageable> paginaCap = ArgumentCaptor.forClass(Pageable.class);
        ajusteService.listar(null, 2L, 0, 0);
        verify(ajustes, org.mockito.Mockito.times(2)).findAll(spec.capture(), paginaCap.capture());
        assertThat(paginaCap.getAllValues().get(1).getPageSize()).isEqualTo(1);

        Root<AjusteInventario> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Object> pEstado = mock(Path.class);
        Path<Object> pProducto = mock(Path.class);
        Predicate porEstado = mock(Predicate.class);
        Predicate porProducto = mock(Predicate.class);
        Predicate todos = mock(Predicate.class);
        doReturn(pEstado).when(root).get("estado");
        doReturn(pProducto).when(root).get("idProducto");
        when(cb.equal(pEstado, EstadoAprobacion.APROBADO)).thenReturn(porEstado);
        when(cb.equal(pProducto, 2L)).thenReturn(porProducto);
        when(cb.and(porEstado)).thenReturn(todos);
        when(cb.and(porProducto)).thenReturn(todos);

        // 1.ª llamada: solo filtra por estado; 2.ª llamada: solo por producto (cb.and devuelve null con otra combinación)
        assertThat(spec.getAllValues().get(0).toPredicate(root, query, cb)).isSameAs(todos);
        assertThat(spec.getAllValues().get(1).toPredicate(root, query, cb)).isSameAs(todos);
        verify(cb, never()).equal(pProducto, null);
    }
}
