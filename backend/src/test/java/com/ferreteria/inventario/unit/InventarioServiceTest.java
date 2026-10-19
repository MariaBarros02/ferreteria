/*
 * Nombre: InventarioServiceTest.java
 * Descripción: Pruebas unitarias del servicio de inventario (RF-05): existencias con alertas, alertas de vencimiento/agotados/stock bajo, lotes, cambio disponible↔dañado con y sin lote, baja de lotes vencidos y umbral de días.
 * Fecha de creación: 2026-10-19
 * Fecha de actualización: 2026-10-19
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ferreteria.inventario.config.ParametrosNegocio;
import com.ferreteria.inventario.domain.enums.EstadoInventario;
import com.ferreteria.inventario.domain.enums.TipoMovimiento;
import com.ferreteria.inventario.domain.enums.TipoRastreo;
import com.ferreteria.inventario.domain.model.Inventario;
import com.ferreteria.inventario.domain.model.Lote;
import com.ferreteria.inventario.domain.model.MovimientoInventario;
import com.ferreteria.inventario.domain.model.Producto;
import com.ferreteria.inventario.dto.AlertasInventarioResponse;
import com.ferreteria.inventario.dto.CambioEstadoRequest;
import com.ferreteria.inventario.dto.ExistenciaResponse;
import com.ferreteria.inventario.dto.InventarioResponse;
import com.ferreteria.inventario.dto.LoteResponse;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.*;
import com.ferreteria.inventario.service.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

/** Casos UT-INV-05 … UT-INV-16 (UT-INV-01 … 04 están en SoporteServiciosTest). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventarioServiceTest {

    @Mock ProductoRepository productos;
    @Mock InventarioRepository inventarios;
    @Mock LoteRepository lotes;
    @Mock CierrePeriodoRepository cierres;
    @Mock AuditoriaService auditoria;
    @Mock PrecioService precios;
    @Mock EventoAuditoriaService eventos;

    static final LocalDate HOY = LocalDate.of(2026, 10, 16);
    final Actor actor = new Actor(3, "marta.inventario@ferreteria.com", "10.0.0.3");

    InventarioService servicio;
    Producto taladro;
    Producto pegante;

    @BeforeEach
    void preparar() {
        Clock reloj = Clock.fixed(Instant.parse("2026-10-16T15:00:00Z"), ZoneOffset.UTC);
        ParametrosNegocio parametros = ParametrosNegocio.porDefecto();   // umbral de vencimiento: 30 días
        KardexService kardex = new KardexService(inventarios, productos, auditoria, cierres, parametros, reloj);
        servicio = new InventarioService(productos, inventarios, lotes, precios, kardex, new LoteService(lotes), eventos,
                parametros);

        taladro = Producto.builder().id(10L).nombre("Taladro").marca("Bosch").presentacion("500W")
                .codigoBarras("7701234500015").categoria("Herramientas").unidadBase("unidad")
                .tipoRastreo(TipoRastreo.NINGUNO).stockMinimo(new BigDecimal("2")).costoPromedio(new BigDecimal("100")).build();
        pegante = Producto.builder().id(11L).nombre("Pegante").marca("Sika").presentacion("250ml")
                .codigoBarras("7701234500022").unidadBase("unidad").tipoRastreo(TipoRastreo.LOTE).tieneVencimiento(true)
                .stockMinimo(new BigDecimal("3")).build();
        when(productos.findById(10L)).thenReturn(Optional.of(taladro));
        when(productos.findById(11L)).thenReturn(Optional.of(pegante));
        when(precios.vigente(anyLong())).thenReturn(Optional.empty());
        when(lotes.save(any(Lote.class))).thenAnswer(a -> a.getArgument(0));
        when(auditoria.registrar(any(), anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(a -> MovimientoInventario.builder().id(900L).build());
    }

    private static Inventario inventario(Long id, String disponible, String reservado, String danado) {
        return Inventario.builder().idProducto(id).disponible(new BigDecimal(disponible))
                .reservado(new BigDecimal(reservado)).danado(new BigDecimal(danado)).build();
    }

    private Inventario saldo(Long id, String disponible, String reservado, String danado) {
        Inventario i = inventario(id, disponible, reservado, danado);
        when(inventarios.findById(id)).thenReturn(Optional.of(i));
        when(inventarios.findForUpdate(id)).thenReturn(Optional.of(i));
        return i;
    }

    private Lote lote(Long id, Long idProducto, String codigo, LocalDate vence, String disponible, String danado) {
        Lote l = Lote.builder().id(id).idProducto(idProducto).codigo(codigo).fechaVencimiento(vence)
                .disponible(new BigDecimal(disponible)).danado(new BigDecimal(danado)).build();
        when(lotes.findById(id)).thenReturn(Optional.of(l));
        when(lotes.findForUpdate(id)).thenReturn(Optional.of(l));
        return l;
    }

    private InventarioResponse cambiar(Long idProducto, Long idLote, EstadoInventario desde, EstadoInventario hacia,
                                       String cantidad) {
        return servicio.cambiarEstado(new CambioEstadoRequest(idProducto, idLote, desde, hacia, new BigDecimal(cantidad),
                "  Revisión de bodega  "), actor);
    }

    private static String codigo(Throwable e) {
        return ((ApiException) e).getCodigo();
    }

    @Test @DisplayName("UT-INV-05 existencias: saldos, total, valor, precio vigente y alertas AGOTADO / STOCK_BAJO / VENCIDO / POR_VENCER")
    void existencias() {
        Producto martillo = Producto.builder().id(12L).nombre("Martillo").marca("Stanley").presentacion("16oz")
                .unidadBase("unidad").tipoRastreo(TipoRastreo.NINGUNO).stockMinimo(new BigDecimal("3")).build();
        Producto brocha = Producto.builder().id(13L).nombre("Brocha").marca("Goya").presentacion("2in")
                .unidadBase("unidad").tipoRastreo(TipoRastreo.NINGUNO).build();        // sin stock mínimo
        Producto nuevo = Producto.builder().id(14L).nombre("Lija").marca("3M").presentacion("80")
                .unidadBase("unidad").tipoRastreo(TipoRastreo.NINGUNO).build();        // sin registro de inventario
        when(productos.buscar(anyString(), any(Pageable.class)))
                .thenAnswer(a -> new PageImpl<>(List.of(taladro, pegante, martillo, brocha, nuevo), a.getArgument(1), 5));
        saldo(10L, "0", "1", "1");
        saldo(11L, "3", "0", "0");
        saldo(12L, "10", "0", "0");
        saldo(13L, "5", "0", "0");
        when(precios.vigente(10L)).thenReturn(Optional.of(new BigDecimal("150")));
        when(lotes.sumaVencida(11L, HOY)).thenReturn(new BigDecimal("1"));
        when(lotes.sumaPorVencer(11L, HOY, HOY.plusDays(30))).thenReturn(new BigDecimal("2"));

        PaginaResponse<ExistenciaResponse> r = servicio.existencias(null, null, -1, 500);

        assertThat(r).isNotNull();
        assertThat(r.pagina()).isZero();
        assertThat(r.tamano()).isEqualTo(100);
        verify(productos).buscar(eq(""), any(Pageable.class));

        ExistenciaResponse t = r.contenido().get(0);
        assertThat(t.productoId()).isEqualTo(10L);
        assertThat(t.producto()).isEqualTo("Taladro Bosch 500W");
        assertThat(t.codigoBarras()).isEqualTo("7701234500015");
        assertThat(t.categoria()).isEqualTo("Herramientas");
        assertThat(t.unidadBase()).isEqualTo("unidad");
        assertThat(t.tipoRastreo()).isEqualTo(TipoRastreo.NINGUNO);
        assertThat(t.activo()).isTrue();
        assertThat(t.total()).isEqualByComparingTo("2");
        assertThat(t.valorInventario()).isEqualByComparingTo("200");
        assertThat(t.precioVigente()).isEqualByComparingTo("150");
        assertThat(t.vencidoSinBaja()).isEqualByComparingTo("0");
        assertThat(t.porVencer()).isEqualByComparingTo("0");
        assertThat(t.alertas()).containsExactly("AGOTADO");
        verify(lotes, never()).sumaVencida(eq(10L), any());

        ExistenciaResponse p = r.contenido().get(1);
        assertThat(p.alertas()).containsExactly("STOCK_BAJO", "VENCIDO", "POR_VENCER");   // 3 disponibles = stock mínimo
        assertThat(p.vencidoSinBaja()).isEqualByComparingTo("1");
        assertThat(p.porVencer()).isEqualByComparingTo("2");
        assertThat(p.valorInventario()).isNull();                                          // sin costo promedio
        assertThat(p.precioVigente()).isNull();

        assertThat(r.contenido().get(2).alertas()).isEmpty();                            // 10 > 3
        assertThat(r.contenido().get(3).alertas()).isEmpty();                            // sin stock mínimo
        ExistenciaResponse sinSaldo = r.contenido().get(4);
        assertThat(sinSaldo.disponible()).isEqualByComparingTo("0");
        assertThat(sinSaldo.total()).isEqualByComparingTo("0");
        assertThat(sinSaldo.alertas()).containsExactly("AGOTADO");
    }

    @Test @DisplayName("UT-INV-06 existencias: filtro recortado y umbral de días acotado entre 0 y 365")
    void existenciasFiltroYUmbral() {
        when(productos.buscar(anyString(), any(Pageable.class)))
                .thenAnswer(a -> new PageImpl<>(List.of(pegante), a.getArgument(1), 1));
        saldo(11L, "3", "0", "0");
        when(lotes.sumaVencida(anyLong(), any())).thenReturn(BigDecimal.ZERO);
        when(lotes.sumaPorVencer(anyLong(), any(), any())).thenReturn(BigDecimal.ZERO);

        servicio.existencias("  pegante  ", 400, 0, 10);
        verify(productos).buscar(eq("pegante"), any(Pageable.class));
        verify(lotes).sumaPorVencer(11L, HOY, HOY.plusDays(365));

        servicio.existencias("", -5, 0, 10);
        verify(lotes).sumaPorVencer(11L, HOY, HOY);

        servicio.existencias("", 7, 0, 10);
        verify(lotes).sumaPorVencer(11L, HOY, HOY.plusDays(7));
    }

    @Test @DisplayName("UT-INV-07 alertas: lotes vencidos y por vencer con nombre del producto, agotados activos y stock bajo con saldo")
    void alertas() {
        Lote vencido = Lote.builder().id(1L).idProducto(11L).codigo("L-OLD").fechaVencimiento(LocalDate.of(2026, 10, 1))
                .disponible(new BigDecimal("2")).build();
        Lote proximo = Lote.builder().id(2L).idProducto(11L).codigo("L-NEW").fechaVencimiento(LocalDate.of(2026, 10, 20))
                .disponible(new BigDecimal("4")).build();
        Lote huerfano = Lote.builder().id(3L).idProducto(99L).codigo("L-X").fechaVencimiento(LocalDate.of(2026, 10, 25))
                .disponible(BigDecimal.ONE).build();
        when(lotes.conVencimientoHasta(HOY.plusDays(30))).thenReturn(List.of(vencido, proximo, huerfano));

        Producto inactivo = Producto.builder().id(12L).nombre("Viejo").marca("X").presentacion("1").activo(false)
                .tipoRastreo(TipoRastreo.NINGUNO).build();
        when(productos.findById(12L)).thenReturn(Optional.of(inactivo));
        when(inventarios.agotados()).thenReturn(List.of(inventario(10L, "0", "0", "0"), inventario(12L, "0", "0", "0"),
                inventario(13L, "0", "0", "0")));
        saldo(10L, "0", "0", "0");

        Producto sinSaldo = Producto.builder().id(14L).nombre("Lija").marca("3M").presentacion("80")
                .tipoRastreo(TipoRastreo.NINGUNO).stockMinimo(BigDecimal.ONE).build();
        Producto sinInventario = Producto.builder().id(15L).nombre("Cinta").marca("3M").presentacion("1")
                .tipoRastreo(TipoRastreo.NINGUNO).stockMinimo(BigDecimal.ONE).build();
        when(productos.conStockBajo()).thenReturn(List.of(pegante, sinSaldo, sinInventario));
        saldo(11L, "3", "0", "0");
        saldo(14L, "0", "0", "0");
        when(lotes.sumaVencida(anyLong(), any())).thenReturn(BigDecimal.ZERO);
        when(lotes.sumaPorVencer(anyLong(), any(), any())).thenReturn(BigDecimal.ZERO);

        AlertasInventarioResponse r = servicio.alertas(null);

        assertThat(r).isNotNull();
        assertThat(r.fechaReferencia()).isEqualTo(HOY);
        assertThat(r.diasUmbral()).isEqualTo(30);
        assertThat(r.vencidos()).extracting(LoteResponse::codigo).containsExactly("L-OLD");
        assertThat(r.vencidos().get(0).producto()).isEqualTo("Pegante Sika 250ml");
        assertThat(r.porVencer()).extracting(LoteResponse::codigo).containsExactly("L-NEW", "L-X");
        assertThat(r.porVencer()).extracting(LoteResponse::producto).containsExactly("Pegante Sika 250ml", "99");
        assertThat(r.agotados()).extracting(ExistenciaResponse::productoId).containsExactly(10L);
        assertThat(r.agotados().get(0).producto()).isEqualTo("Taladro Bosch 500W");
        assertThat(r.stockBajo()).extracting(ExistenciaResponse::productoId).containsExactly(11L);
        assertThat(r.stockBajo().get(0).alertas()).containsExactly("STOCK_BAJO");
    }

    @Test @DisplayName("UT-INV-08 umbral de alertas: sin valor usa el configurado (30); se acota a 0–365")
    void umbralDeAlertas() {
        when(lotes.conVencimientoHasta(any())).thenReturn(List.of());
        when(inventarios.agotados()).thenReturn(List.of());
        when(productos.conStockBajo()).thenReturn(List.of());

        assertThat(servicio.alertas(null).diasUmbral()).isEqualTo(30);
        verify(lotes).conVencimientoHasta(HOY.plusDays(30));
        assertThat(servicio.alertas(400).diasUmbral()).isEqualTo(365);
        assertThat(servicio.alertas(-3).diasUmbral()).isZero();
        assertThat(servicio.alertas(10).diasUmbral()).isEqualTo(10);
    }

    @Test @DisplayName("UT-INV-09 lotes de un producto con su estado según la fecha; producto inexistente → 404")
    void lotesDeProducto() {
        Lote proximo = Lote.builder().id(1L).idProducto(11L).codigo("L-1").fechaVencimiento(LocalDate.of(2026, 11, 10))
                .disponible(new BigDecimal("4")).build();
        Lote lejano = Lote.builder().id(2L).idProducto(11L).codigo("L-2").fechaVencimiento(LocalDate.of(2027, 6, 30))
                .disponible(new BigDecimal("8")).build();
        when(lotes.deProducto(11L)).thenReturn(List.of(proximo, lejano));

        List<LoteResponse> r = servicio.lotes(11L);

        assertThat(r).extracting(LoteResponse::codigo).containsExactly("L-1", "L-2");
        assertThat(r).extracting(LoteResponse::estado).containsExactly("POR_VENCER", "VIGENTE");
        assertThat(r).extracting(LoteResponse::producto).containsOnly("Pegante");
        assertThatThrownBy(() -> servicio.lotes(404L)).satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
    }

    @Test @DisplayName("UT-INV-10 recuperar de no apto a disponible sin lote: mueve saldos, no toca lotes y audita")
    @SuppressWarnings("unchecked")
    void recuperarSinLote() {
        Inventario inv = saldo(11L, "6", "0", "3");
        Lote l = lote(7L, 11L, "L-7", LocalDate.of(2026, 12, 31), "6", "0");
        when(lotes.sumaDisponible(11L)).thenReturn(new BigDecimal("6"));
        when(lotes.deProducto(11L)).thenReturn(List.of(l));

        InventarioResponse r = cambiar(11L, null, EstadoInventario.DANADO, EstadoInventario.DISPONIBLE, "2");

        assertThat(r).isNotNull();
        assertThat(r.disponible()).isEqualByComparingTo("8");
        assertThat(r.danado()).isEqualByComparingTo("1");
        assertThat(inv.getDanado()).isEqualByComparingTo("1");
        assertThat(l.getDisponible()).isEqualByComparingTo("6");    // un lote no se descuenta al recuperar
        verify(lotes, never()).save(any());

        ArgumentCaptor<Map<String, Object>> d = ArgumentCaptor.forClass(Map.class);
        verify(eventos).registrar(eq(actor), eq(Auditables.INVENTARIO), eq(Auditables.CAMBIO_ESTADO), eq("PRODUCTO"),
                eq(11L), eq("Cambio de estado de Pegante: danado → disponible"), d.capture());
        assertThat(d.getValue()).containsEntry("producto", "Pegante").containsEntry("desde", "danado")
                .containsEntry("hacia", "disponible").containsEntry("motivo", "Revisión de bodega");
    }

    @Test @DisplayName("UT-INV-11 marcar como dañado sin lote en un producto por lote descuenta de los lotes; saldo exacto es válido")
    void danarSinLote() {
        Inventario inv = saldo(11L, "4", "0", "0");
        Lote l = lote(7L, 11L, "L-7", LocalDate.of(2026, 12, 31), "4", "0");
        when(lotes.sumaDisponible(11L)).thenReturn(new BigDecimal("4"));
        when(lotes.deProducto(11L)).thenReturn(List.of(l));

        cambiar(11L, null, EstadoInventario.DISPONIBLE, EstadoInventario.DANADO, "4");   // exactamente el disponible

        assertThat(inv.getDisponible()).isEqualByComparingTo("0");
        assertThat(inv.getDanado()).isEqualByComparingTo("4");
        assertThat(l.getDisponible()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> cambiar(11L, null, EstadoInventario.DISPONIBLE, EstadoInventario.DANADO, "1"))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("SALDO_INSUFICIENTE"));
    }

    @Test @DisplayName("UT-INV-12 con lote: dañar mueve el saldo del lote (aunque esté vencido); recuperar un lote vencido no se permite")
    void cambioConLote() {
        Inventario inv = saldo(11L, "5", "0", "0");
        Lote vencido = lote(7L, 11L, "L-7", LocalDate.of(2026, 1, 1), "5", "0");

        cambiar(11L, 7L, EstadoInventario.DISPONIBLE, EstadoInventario.DANADO, "2");
        assertThat(vencido.getDisponible()).isEqualByComparingTo("3");
        assertThat(vencido.getDanado()).isEqualByComparingTo("2");
        assertThat(inv.getDanado()).isEqualByComparingTo("2");
        verify(auditoria).registrar(eq(TipoMovimiento.CAMBIO_ESTADO), eq(11L), eq(new BigDecimal("-2")), any(), any(),
                any(), any(), any(), any(), eq(EstadoInventario.DISPONIBLE), eq(7L), any());

        assertThatThrownBy(() -> cambiar(11L, 7L, EstadoInventario.DANADO, EstadoInventario.DISPONIBLE, "1"))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("LOTE_VENCIDO"));

        Lote vigente = lote(8L, 11L, "L-8", LocalDate.of(2026, 12, 31), "0", "3");
        inv.setDanado(new BigDecimal("5"));
        cambiar(11L, 8L, EstadoInventario.DANADO, EstadoInventario.DISPONIBLE, "1");
        assertThat(vigente.getDisponible()).isEqualByComparingTo("1");
        assertThat(vigente.getDanado()).isEqualByComparingTo("2");

        Lote sinFecha = lote(9L, 11L, "L-9", null, "0", "1");
        cambiar(11L, 9L, EstadoInventario.DANADO, EstadoInventario.DISPONIBLE, "1");
        assertThat(sinFecha.getDisponible()).isEqualByComparingTo("1");
    }

    @Test @DisplayName("UT-INV-13 transiciones distintas de disponible↔dañado se rechazan; producto inexistente → 404")
    void transicionesInvalidas() {
        assertThatThrownBy(() -> cambiar(11L, null, EstadoInventario.RESERVADO, EstadoInventario.DISPONIBLE, "1"))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("TRANSICION_INVALIDA"));
        assertThatThrownBy(() -> cambiar(11L, null, EstadoInventario.DANADO, EstadoInventario.RESERVADO, "1"))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("TRANSICION_INVALIDA"));
        assertThatThrownBy(() -> cambiar(11L, null, EstadoInventario.DISPONIBLE, EstadoInventario.DISPONIBLE, "1"))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("TRANSICION_INVALIDA"));
        assertThatThrownBy(() -> cambiar(404L, null, EstadoInventario.DISPONIBLE, EstadoInventario.DANADO, "1"))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
    }

    @Test @DisplayName("UT-INV-14 baja de lote vencido: con disponible igual al del lote se permite y devuelve el lote actualizado")
    void bajaExacta() {
        Inventario inv = saldo(11L, "5", "0", "0");
        Lote l = lote(7L, 11L, "L-7", LocalDate.of(2026, 10, 1), "5", "0");

        LoteResponse r = servicio.darDeBajaLote(7L, actor);

        assertThat(r).isNotNull();
        assertThat(r.codigo()).isEqualTo("L-7");
        assertThat(r.producto()).isEqualTo("Pegante");
        assertThat(r.estado()).isEqualTo("VENCIDO");
        assertThat(r.disponible()).isEqualByComparingTo("0");
        assertThat(r.danado()).isEqualByComparingTo("5");
        assertThat(l.getDanado()).isEqualByComparingTo("5");
        assertThat(inv.getDisponible()).isEqualByComparingTo("0");
        assertThat(inv.getDanado()).isEqualByComparingTo("5");
    }

    @Test @DisplayName("UT-INV-15 baja de lote: parte reservada → LOTE_RESERVADO; lote o producto inexistentes → 404")
    void bajaRechazada() {
        saldo(11L, "4", "1", "0");
        lote(7L, 11L, "L-7", LocalDate.of(2026, 10, 1), "5", "0");
        assertThatThrownBy(() -> servicio.darDeBajaLote(7L, actor))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("LOTE_RESERVADO"));

        assertThatThrownBy(() -> servicio.darDeBajaLote(404L, actor))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
        lote(8L, 404L, "L-8", LocalDate.of(2026, 10, 1), "1", "0");
        assertThatThrownBy(() -> servicio.darDeBajaLote(8L, actor))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
    }

    @Test @DisplayName("UT-INV-16 existencias con producto por lote y saldo sobre el mínimo no genera alertas de stock")
    void porLoteSinAlertas() {
        when(productos.buscar(anyString(), any(Pageable.class)))
                .thenAnswer(a -> new PageImpl<>(List.of(pegante), a.getArgument(1), 1));
        saldo(11L, "10", "0", "0");
        when(lotes.sumaVencida(11L, HOY)).thenReturn(BigDecimal.ZERO);
        when(lotes.sumaPorVencer(eq(11L), eq(HOY), any())).thenReturn(BigDecimal.ZERO);

        ExistenciaResponse e = servicio.existencias(null, null, 0, 10).contenido().get(0);

        assertThat(e.alertas()).isEmpty();
        verify(lotes).sumaVencida(11L, HOY);
        verify(lotes).sumaPorVencer(11L, HOY, HOY.plusDays(30));
    }
}