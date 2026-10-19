/*
 * Nombre: PrecioServiceTest.java
 * Descripción: Pruebas unitarias de propuesta y aprobación de precios (RF-07, RF-16, RN-07): cálculo por recargo, margen y manual, recargo implícito, propuesta automática, segregación de funciones, rechazo, historial y consultas por estado.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-19
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
package com.ferreteria.inventario.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ferreteria.inventario.config.ParametrosNegocio;
import com.ferreteria.inventario.domain.model.Precio;
import com.ferreteria.inventario.domain.model.Producto;
import com.ferreteria.inventario.domain.model.Usuario;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.dto.PrecioPropuestaRequest;
import com.ferreteria.inventario.dto.PrecioResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.PrecioRepository;
import com.ferreteria.inventario.repository.ProductoRepository;
import com.ferreteria.inventario.repository.UsuarioRepository;
import com.ferreteria.inventario.service.Actor;
import com.ferreteria.inventario.service.Auditables;
import com.ferreteria.inventario.service.EventoAuditoriaService;
import com.ferreteria.inventario.service.PrecioService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/** Casos UT-PRE-01 … UT-PRE-19 (UT-PRE-08 en adelante: casos para matar mutantes de PIT). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrecioServiceTest {

    @Mock PrecioRepository precios;
    @Mock ProductoRepository productos;
    @Mock UsuarioRepository usuarios;
    @Mock EventoAuditoriaService eventos;

    PrecioService servicio;
    Producto producto;
    final List<Precio> guardados = new ArrayList<>();
    final Actor inventario = new Actor(3, "marta.inventario@ferreteria.com", "x");
    final Actor gerencia = new Actor(5, "gerencia@ferreteria.com", "x");

    @BeforeEach
    void preparar() {
        servicio = new PrecioService(precios, productos, usuarios, eventos, ParametrosNegocio.porDefecto(),
                Clock.fixed(Instant.parse("2026-10-16T15:00:00Z"), ZoneOffset.UTC));
        producto = Producto.builder().id(1L).nombre("Taladro").marca("Bosch").presentacion("500W")
                .costoPromedio(new BigDecimal("12000")).porcentajeIncremento(new BigDecimal("0.30")).build();
        when(productos.findById(1L)).thenReturn(Optional.of(producto));
        when(precios.save(any(Precio.class))).thenAnswer(a -> {
            Precio p = a.getArgument(0);
            if (p.getId() == null) {
                p.setId((long) guardados.size() + 1);
                guardados.add(p);
            }
            return p;
        });
        when(precios.findForUpdate(anyLong())).thenAnswer(a -> guardados.stream()
                .filter(p -> p.getId().equals(a.getArgument(0))).findFirst());
        when(precios.vigentes(eq(1L), any())).thenAnswer(a -> guardados.stream()
                .filter(p -> p.getPrecioVigente() != null).toList());
        when(precios.findByIdProductoAndEstadoAprobacion(anyLong(), any())).thenReturn(List.of());
        when(usuarios.findById(any())).thenReturn(Optional.empty());
    }

    private PrecioResponse proponer() {
        return servicio.proponer(new PrecioPropuestaRequest(1L, null, null, null, null), inventario);
    }

    @Test @DisplayName("UT-PRE-01 propuesta por recargo: costo $12.000 × 1,30 = $15.600 y no queda vigente")
    void proponerRecargo() {
        PrecioResponse r = proponer();
        assertThat(r.precioPropuesto()).isEqualByComparingTo("15600");
        assertThat(r.estado()).isEqualTo("propuesto");
        assertThat(r.precioVigente()).isNull();
        assertThat(r.precioActual()).isNull();
    }

    @Test @DisplayName("UT-PRE-02 sin costo promedio no se calcula (422); el precio manual exige justificación")
    void sinCosto() {
        producto.setCostoPromedio(null);
        assertThatThrownBy(this::proponer).satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("SIN_COSTO"));
        PrecioResponse r = servicio.proponer(new PrecioPropuestaRequest(1L, "manual", null, new BigDecimal("20000"),
                "Precio de lanzamiento"), inventario);
        assertThat(r.precioPropuesto()).isEqualByComparingTo("20000");
        assertThat(r.modoCalculo()).isEqualTo("manual");
    }

    @Test @DisplayName("UT-PRE-03 aprobar vuelve vigente el precio y registra aprobador y fechas")
    void aprobar() {
        proponer();
        PrecioResponse r = servicio.aprobar(1L, gerencia);
        assertThat(r.estado()).isEqualTo("aprobado");
        assertThat(r.precioVigente()).isEqualByComparingTo("15600");
        assertThat(guardados.get(0).getUsuarioAprobador()).isEqualTo(5);
        assertThat(guardados.get(0).getFechaVigencia()).isNotNull();
    }

    @Test @DisplayName("UT-PRE-04 quien propone no puede aprobar su propia propuesta")
    void segregacion() {
        proponer();
        assertThatThrownBy(() -> servicio.aprobar(1L, inventario))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("MISMA_PERSONA"));
        assertThat(guardados.get(0).getPrecioVigente()).isNull();
    }

    @Test @DisplayName("UT-PRE-05 una propuesta rechazada no afecta el precio vigente y no se puede aprobar después")
    void rechazar() {
        proponer();
        servicio.rechazar(1L, "Muy alto", gerencia);
        assertThat(guardados.get(0).getEstadoAprobacion()).isEqualTo("rechazado");
        assertThat(guardados.get(0).getPrecioVigente()).isNull();
        assertThatThrownBy(() -> servicio.aprobar(1L, gerencia))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("PRECIO_YA_DECIDIDO"));
    }

    @Test @DisplayName("UT-PRE-06 el rechazo exige motivo")
    void rechazoSinMotivo() {
        proponer();
        assertThatThrownBy(() -> servicio.rechazar(1L, " ", gerencia))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("MOTIVO_OBLIGATORIO"));
    }

    @Test @DisplayName("UT-PRE-07 la propuesta automática no se duplica si el precio vigente ya es el calculado")
    void automaticaSinDuplicados() {
        proponer();
        servicio.aprobar(1L, gerencia);
        assertThat(servicio.proponerAutomatico(producto, inventario)).isEmpty();
        verify(eventos, never()).registrar(eq(inventario), any(), eq("PROPONER"), any(), eq(2L), any(), any());
    }

    // ---------------------------------------------------------------------------------------------
    // Casos para matar mutantes de PIT en PrecioService (UT-PRE-08 … UT-PRE-19)
    // ---------------------------------------------------------------------------------------------

    static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 16, 15, 0);

    private static String codigo(Throwable e) {
        return ((ApiException) e).getCodigo();
    }

    private PrecioResponse manual(String precio, BigDecimal porcentaje, String motivo) {
        return servicio.proponer(new PrecioPropuestaRequest(1L, "manual", porcentaje, new BigDecimal(precio), motivo),
                inventario);
    }

    private void conCorreos() {
        when(usuarios.findById(3)).thenReturn(Optional.of(Usuario.builder().id(3).correo("marta.inventario@ferreteria.com").build()));
        when(usuarios.findById(5)).thenReturn(Optional.of(Usuario.builder().id(5).correo("gerencia@ferreteria.com").build()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> detalleEvento(String accion, Long idPrecio) {
        ArgumentCaptor<Map<String, Object>> c = ArgumentCaptor.forClass(Map.class);
        verify(eventos).registrar(any(), eq(Auditables.PRECIOS), eq(accion), eq("PRECIO"), eq(idPrecio), anyString(),
                c.capture());
        return c.getValue();
    }

    @Test @DisplayName("UT-PRE-08 precio manual sin porcentaje: se calcula el recargo implícito y el motivo se limpia")
    void manualRecargoImplicito() {
        producto.setPorcentajeIncremento(null);
        PrecioResponse r = manual("15000", null, "  Precio de competencia  ");
        assertThat(r.porcentajeIncremento()).isEqualByComparingTo("0.25");     // 15.000 / 12.000 − 1
        assertThat(r.motivo()).isEqualTo("Precio de competencia");
        assertThat(r.costoPromedio()).isEqualByComparingTo("12000");

        PrecioResponse conPorcentaje = manual("15000", new BigDecimal("0.10"), "Promoción");
        assertThat(conPorcentaje.porcentajeIncremento()).isEqualByComparingTo("0.10");   // se respeta el indicado
    }

    @Test @DisplayName("UT-PRE-09 recargo implícito: sin costo o costo 0 → 0; precio bajo el costo → 0; tope de 99,9999")
    void recargoImplicitoLimites() {
        producto.setPorcentajeIncremento(null);

        producto.setCostoPromedio(null);
        PrecioResponse sinCosto = manual("15000", null, "x");
        assertThat(sinCosto.porcentajeIncremento()).isEqualByComparingTo("0");
        assertThat(sinCosto.costoPromedio()).isEqualByComparingTo("0");               // se guarda 0, no nulo

        producto.setCostoPromedio(BigDecimal.ZERO);
        assertThat(manual("15000", null, "x").porcentajeIncremento()).isEqualByComparingTo("0");

        producto.setCostoPromedio(new BigDecimal("12000"));
        assertThat(manual("10000", null, "x").porcentajeIncremento()).isEqualByComparingTo("0");
        assertThat(manual("2000000", null, "x").porcentajeIncremento()).isEqualByComparingTo("99.9999");
    }

    @Test @DisplayName("UT-PRE-10 el porcentaje de la solicitud tiene prioridad; modo margen calcula P = C / (1 − m)")
    void porcentajeYMargen() {
        PrecioResponse recargo = servicio.proponer(new PrecioPropuestaRequest(1L, "recargo", new BigDecimal("0.50"), null,
                null), inventario);
        assertThat(recargo.precioPropuesto()).isEqualByComparingTo("18000");
        assertThat(recargo.porcentajeIncremento()).isEqualByComparingTo("0.50");
        assertThat(recargo.motivo()).isNull();

        PrecioResponse margen = servicio.proponer(new PrecioPropuestaRequest(1L, "margen", new BigDecimal("0.25"), null,
                "   "), inventario);
        assertThat(margen.precioPropuesto()).isEqualByComparingTo("16000");
        assertThat(margen.modoCalculo()).isEqualTo("margen");
        assertThat(margen.motivo()).isNull();
    }

    @Test @DisplayName("UT-PRE-11 sin porcentaje en el producto ni en la solicitud no se calcula (SIN_PORCENTAJE)")
    void sinPorcentaje() {
        producto.setPorcentajeIncremento(null);
        assertThatThrownBy(this::proponer).satisfies(e -> assertThat(codigo(e)).isEqualTo("SIN_PORCENTAJE"));
        verify(precios, never()).save(any());
    }

    @Test @DisplayName("UT-PRE-12 producto o propuesta inexistentes responden 404")
    void inexistentes() {
        assertThatThrownBy(() -> servicio.proponer(new PrecioPropuestaRequest(99L, null, null, null, null), inventario))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
        assertThatThrownBy(() -> servicio.aprobar(999L, gerencia))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
        assertThatThrownBy(() -> servicio.historial(99L, 0, 10))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));

        // propuesta de un producto que ya no existe
        guardados.add(Precio.builder().id(50L).idProducto(99L).precioPropuesto(BigDecimal.TEN)
                .estadoAprobacion("propuesto").usuarioProponente(3).build());
        assertThatThrownBy(() -> servicio.aprobar(50L, gerencia))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
        assertThatThrownBy(() -> servicio.rechazar(50L, "x", gerencia))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
    }

    @Test @DisplayName("UT-PRE-13 propuesta automática: sin porcentaje o sin costo no se propone nada")
    void automaticaSinDatos() {
        producto.setPorcentajeIncremento(null);
        assertThat(servicio.proponerAutomatico(producto, inventario)).isEmpty();
        producto.setPorcentajeIncremento(new BigDecimal("0.30"));
        producto.setCostoPromedio(null);
        assertThat(servicio.proponerAutomatico(producto, inventario)).isEmpty();
        verify(precios, never()).save(any());
    }

    @Test @DisplayName("UT-PRE-14 propuesta automática: se crea cuando no hay vigente igual ni pendiente con el mismo valor")
    void automaticaCrea() {
        // precio vigente distinto al calculado (14.000 vs 15.600)
        guardados.add(Precio.builder().id(100L).idProducto(1L).precioVigente(new BigDecimal("14000"))
                .estadoAprobacion("aprobado").build());
        // pendiente con otro valor
        when(precios.findByIdProductoAndEstadoAprobacion(1L, "propuesto")).thenReturn(List.of(
                Precio.builder().precioPropuesto(new BigDecimal("15000")).build()));

        Optional<Precio> r = servicio.proponerAutomatico(producto, inventario);

        assertThat(r).isPresent();
        Precio p = r.get();
        assertThat(p.getPrecioPropuesto()).isEqualByComparingTo("15600");
        assertThat(p.getEstadoAprobacion()).isEqualTo("propuesto");
        assertThat(p.getModoCalculo()).isEqualTo("recargo");
        assertThat(p.getUsuarioProponente()).isEqualTo(3);
        assertThat(p.getMotivo()).startsWith("Propuesta automática");
        assertThat(detalleEvento(Auditables.PROPONER, p.getId())).containsEntry("precioPropuesto", p.getPrecioPropuesto());
    }

    @Test @DisplayName("UT-PRE-15 propuesta automática: si ya hay una pendiente con el mismo valor no se duplica")
    void automaticaPendienteIgual() {
        when(precios.findByIdProductoAndEstadoAprobacion(1L, "propuesto")).thenReturn(List.of(
                Precio.builder().precioPropuesto(new BigDecimal("15600.00")).build()));
        assertThat(servicio.proponerAutomatico(producto, inventario)).isEmpty();
        verify(precios, never()).save(any());
    }

    @Test @DisplayName("UT-PRE-16 aprobar registra fecha de decisión, correos y el precio anterior en la auditoría")
    void aprobarCompleto() {
        conCorreos();
        proponer();
        PrecioResponse primero = servicio.aprobar(1L, gerencia);
        assertThat(primero.fechaDecision()).isEqualTo(AHORA);
        assertThat(primero.fechaVigencia()).isEqualTo(AHORA);
        assertThat(primero.proponente()).isEqualTo("marta.inventario@ferreteria.com");
        assertThat(primero.aprobador()).isEqualTo("gerencia@ferreteria.com");
        assertThat(primero.producto()).isEqualTo("Taladro Bosch 500W");
        assertThat(detalleEvento(Auditables.APROBAR, 1L)).containsEntry("precioAnterior", null);

        PrecioResponse segundo = manual("17000", null, "Ajuste");
        assertThat(segundo.aprobador()).isNull();                     // aún sin decidir
        assertThat(segundo.precioActual()).isEqualByComparingTo("15600");
        servicio.aprobar(segundo.id(), gerencia);
        Map<String, Object> d = detalleEvento(Auditables.APROBAR, segundo.id());
        assertThat((BigDecimal) d.get("precioAnterior")).isEqualByComparingTo("15600");
    }

    @Test @DisplayName("UT-PRE-17 rechazar registra quién decide, cuándo y el motivo recortado")
    void rechazarCompleto() {
        conCorreos();
        proponer();
        PrecioResponse r = servicio.rechazar(1L, "  Fuera de mercado  ", gerencia);

        assertThat(r).isNotNull();
        assertThat(r.estado()).isEqualTo("rechazado");
        assertThat(r.motivoRechazo()).isEqualTo("Fuera de mercado");
        assertThat(r.aprobador()).isEqualTo("gerencia@ferreteria.com");
        assertThat(r.fechaDecision()).isEqualTo(AHORA);
        assertThat(guardados.get(0).getUsuarioAprobador()).isEqualTo(5);
        assertThat(detalleEvento(Auditables.RECHAZAR, 1L)).containsEntry("estado", "rechazado");
    }

    @Test @DisplayName("UT-PRE-18 historial paginado (página ≥ 0, tamaño 1–100) con correos consultados una sola vez")
    void historial() {
        conCorreos();
        proponer();
        proponer();
        when(precios.findByIdProductoOrderByIdDesc(eq(1L), any(Pageable.class)))
                .thenAnswer(a -> new PageImpl<>(guardados, a.getArgument(1), guardados.size()));
        // las dos propuestas ya consultaron el correo; se cuenta solo lo que hace el historial
        org.mockito.Mockito.clearInvocations(usuarios);

        PaginaResponse<PrecioResponse> h = servicio.historial(1L, -1, 500);

        assertThat(h).isNotNull();
        assertThat(h.pagina()).isZero();
        assertThat(h.tamano()).isEqualTo(100);
        assertThat(h.contenido()).hasSize(2);
        assertThat(h.contenido()).extracting(PrecioResponse::proponente).containsOnly("marta.inventario@ferreteria.com");
        assertThat(h.contenido()).extracting(PrecioResponse::producto).containsOnly("Taladro Bosch 500W");
        verify(usuarios, times(1)).findById(3);   // dos filas del mismo proponente: una sola consulta (caché)
    }
    @Test @DisplayName("UT-PRE-19 consulta por estado: sin estado lista las pendientes; producto inexistente se muestra sin nombre")
    void porEstado() {
        proponer();
        guardados.add(Precio.builder().id(60L).idProducto(99L).precioPropuesto(BigDecimal.TEN).costoPromedio(BigDecimal.ONE)
                .estadoAprobacion("propuesto").build());
        when(precios.findByEstadoAprobacionOrderByIdDesc(anyString(), any(Pageable.class)))
                .thenAnswer(a -> new PageImpl<>(guardados, a.getArgument(1), guardados.size()));

        PaginaResponse<PrecioResponse> r = servicio.porEstado(null, 0, 0);

        assertThat(r).isNotNull();
        assertThat(r.tamano()).isEqualTo(1);
        verify(precios).findByEstadoAprobacionOrderByIdDesc(eq("propuesto"), any(Pageable.class));
        assertThat(r.contenido()).extracting(PrecioResponse::producto).containsExactly("Taladro Bosch 500W", null);
        assertThat(r.contenido().get(1).proponente()).isNull();

        servicio.porEstado("aprobado", 0, 10);
        verify(precios).findByEstadoAprobacionOrderByIdDesc(eq("aprobado"), any(Pageable.class));
    }
}