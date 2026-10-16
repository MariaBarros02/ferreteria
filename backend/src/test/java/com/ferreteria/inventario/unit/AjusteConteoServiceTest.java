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
}
