/*
 * Nombre: CompraServiceTest.java
 * Descripción: Pruebas unitarias de compras y recepción (RF-03, RF-17): conversión de unidades, recepción parcial, lotes y vencimientos, costo promedio, idempotencia y anulación.
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ferreteria.inventario.config.ParametrosNegocio;
import com.ferreteria.inventario.domain.enums.EstadoDocumento;
import com.ferreteria.inventario.domain.enums.TipoRastreo;
import com.ferreteria.inventario.domain.model.*;
import com.ferreteria.inventario.dto.CompraRequest;
import com.ferreteria.inventario.dto.CompraResponse;
import com.ferreteria.inventario.dto.RecepcionRequest;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.*;
import com.ferreteria.inventario.service.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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

/** Casos UT-COM-01 … UT-COM-11. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompraServiceTest {

    @Mock CompraRepository compras;
    @Mock LineaCompraRepository lineas;
    @Mock DocumentoIngresoRepository documentos;
    @Mock RecepcionRepository recepciones;
    @Mock TerceroRepository terceros;
    @Mock ProductoRepository productos;
    @Mock UsuarioRepository usuarios;
    @Mock LoteRepository lotes;
    @Mock ConversionUnidadRepository conversiones;
    @Mock InventarioRepository inventarios;
    @Mock CierrePeriodoRepository cierres;
    @Mock AuditoriaService auditoria;
    @Mock PrecioService precioService;
    @Mock EventoAuditoriaService eventos;
    @Mock MonedaRepository monedasRepo;
    @Mock TasaCambioRepository tasas;

    CompraService servicio;
    Producto tornillo;
    Producto pegante;
    Inventario invTornillo;
    Inventario invPegante;
    Compra compra;
    final Actor actor = new Actor(3, "marta.inventario@ferreteria.com", "10.0.0.9");
    final AtomicLong ids = new AtomicLong(100);

    @BeforeEach
    void preparar() {
        Clock reloj = Clock.fixed(Instant.parse("2026-10-16T15:00:00Z"), ZoneOffset.UTC);
        ParametrosNegocio parametros = ParametrosNegocio.porDefecto();
        KardexService kardex = new KardexService(inventarios, productos, auditoria, cierres, parametros, reloj);
        servicio = new CompraService(compras, lineas, documentos, recepciones, terceros, productos, usuarios, lotes,
                new UnidadService(conversiones), kardex, new LoteService(lotes), precioService, eventos, parametros,
                new MonedaService(monedasRepo, tasas, usuarios, kardex, eventos));
        when(monedasRepo.findFirstByFuncionalTrue()).thenReturn(Optional.of(Moneda.builder().codigo("COP").funcional(true).build()));
        tornillo = Producto.builder().id(2L).nombre("Tornillo").marca("Genérico").presentacion("1 pulgada")
                .unidadBase("unidad").tipoRastreo(TipoRastreo.NINGUNO).build();
        pegante = Producto.builder().id(3L).nombre("Pegante").marca("Sika").presentacion("250ml").unidadBase("unidad")
                .tipoRastreo(TipoRastreo.LOTE).tieneVencimiento(true).build();
        invTornillo = Inventario.builder().idProducto(2L).disponible(new BigDecimal("10")).reservado(BigDecimal.ZERO)
                .danado(BigDecimal.ZERO).build();
        invPegante = Inventario.builder().idProducto(3L).disponible(BigDecimal.ZERO).reservado(BigDecimal.ZERO)
                .danado(BigDecimal.ZERO).build();
        tornillo.setCostoPromedio(new BigDecimal("100"));
        when(productos.findById(2L)).thenReturn(Optional.of(tornillo));
        when(productos.findById(3L)).thenReturn(Optional.of(pegante));
        when(inventarios.findForUpdate(2L)).thenReturn(Optional.of(invTornillo));
        when(inventarios.findForUpdate(3L)).thenReturn(Optional.of(invPegante));
        when(terceros.findById(9L)).thenReturn(Optional.of(Tercero.builder().id(9L).proveedor(true)
                .razonSocial("Distribuidora").numeroIdentificacion("900123456").build()));
        when(compras.siguienteNumero()).thenReturn(7L);
        when(compras.save(any(Compra.class))).thenAnswer(a -> {
            Compra c = a.getArgument(0);
            if (c.getId() == null) {
                c.setId(50L);
            }
            for (LineaCompra l : c.getLineas()) {
                if (l.getId() == null) {
                    l.setId(ids.incrementAndGet());
                }
            }
            compra = c;
            return c;
        });
        when(documentos.siguienteNumero()).thenReturn(1000L);
        when(documentos.save(any(DocumentoIngreso.class))).thenAnswer(a -> {
            DocumentoIngreso d = a.getArgument(0);
            if (d.getId() == null) {
                d.setId(ids.incrementAndGet());
            }
            return d;
        });
        when(documentos.findByIdOperacion(any())).thenReturn(Optional.empty());
        when(lotes.save(any(Lote.class))).thenAnswer(a -> {
            Lote l = a.getArgument(0);
            if (l.getId() == null) {
                l.setId(ids.incrementAndGet());
            }
            return l;
        });
        when(lotes.findByIdProductoAndCodigoIgnoreCase(anyLong(), any())).thenReturn(Optional.empty());
        when(auditoria.registrar(any(), anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(a -> MovimientoInventario.builder().id(ids.incrementAndGet()).build());
        when(compras.findForUpdate(50L)).thenAnswer(a -> Optional.ofNullable(compra));
        when(compras.findById(50L)).thenAnswer(a -> Optional.ofNullable(compra));
        when(lineas.findForUpdate(anyLong())).thenAnswer(a -> compra.getLineas().stream()
                .filter(l -> l.getId().equals(a.getArgument(0))).findFirst());
        when(documentos.findByIdCompraOrderByIdDesc(anyLong())).thenReturn(List.of());
    }

    private CompraResponse crearCompra() {
        when(conversiones.findByIdProductoAndUnidadPresentacionIgnoreCase(2L, "caja")).thenReturn(Optional.of(
                ConversionUnidad.builder().unidadPresentacion("caja").factorAUnidadBase(new BigDecimal("100"))
                        .precisionDecimales(0).build()));
        return servicio.crear(new CompraRequest(9L, null, null, List.of(
                new CompraRequest.Linea(2L, new BigDecimal("3"), "caja", new BigDecimal("15000"), new BigDecimal("19"), null),
                new CompraRequest.Linea(3L, new BigDecimal("20"), null, new BigDecimal("8000"), null, new BigDecimal("10")))), actor);
    }

    private Long linea(Long idProducto) {
        return compra.getLineas().stream().filter(l -> l.getIdProducto().equals(idProducto)).findFirst().orElseThrow().getId();
    }

    @Test @DisplayName("UT-COM-01 crear: 3 cajas x 100 = 300 unidades pedidas y costo por unidad base = 15.000 / 100")
    void crearConvierte() {
        CompraResponse r = crearCompra();
        assertThat(r.numero()).isEqualTo("OC-000007");
        LineaCompra l = compra.getLineas().get(0);
        assertThat(l.getCantidadPedida()).isEqualByComparingTo("300");
        assertThat(l.getCostoPactado()).isEqualByComparingTo("150");
        assertThat(r.estadoRecepcion()).isEqualTo("PENDIENTE");
        // 300 x 150 = 45.000 + IVA 19 % = 53.550; 20 x 8.000 = 160.000 − 10 % = 144.000
        assertThat(r.total()).isEqualByComparingTo("197550");
        assertThat(invTornillo.getDisponible()).isEqualByComparingTo("10");   // una compra no mueve inventario
    }

    @Test @DisplayName("UT-COM-02 proveedor sin rol de proveedor se rechaza")
    void proveedorInvalido() {
        when(terceros.findById(9L)).thenReturn(Optional.of(Tercero.builder().id(9L).cliente(true).numeroIdentificacion("1").build()));
        assertThatThrownBy(this::crearCompra).satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("PROVEEDOR_INVALIDO"));
    }

    @Test @DisplayName("UT-COM-03 recepción parcial suma solo lo recibido y recalcula el costo promedio ponderado")
    void recepcionParcial() {
        crearCompra();
        CompraResponse r = servicio.recibir(50L, new RecepcionRequest("FAC-1", null, null,
                List.of(new RecepcionRequest.Linea(linea(2L), new BigDecimal("100"), null, null))), actor);
        assertThat(invTornillo.getDisponible()).isEqualByComparingTo("110");
        // (10 x 100 + 100 x 150) / 110 = 145.4545
        assertThat(tornillo.getCostoPromedio()).isEqualByComparingTo("145.4545");
        assertThat(r.estadoRecepcion()).isEqualTo("PARCIAL");
        verify(precioService).proponerAutomatico(tornillo, actor);
    }

    @Test @DisplayName("UT-COM-04 no se puede recibir más de lo pendiente (409) y no cambia el inventario")
    void recepcionExcede() {
        crearCompra();
        assertThatThrownBy(() -> servicio.recibir(50L, new RecepcionRequest("FAC-1", null, null,
                List.of(new RecepcionRequest.Linea(linea(2L), new BigDecimal("301"), null, null))), actor))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("RECEPCION_EXCEDE"));
        assertThat(invTornillo.getDisponible()).isEqualByComparingTo("10");
    }

    @Test @DisplayName("UT-COM-05 producto por lote exige lote y fecha de vencimiento")
    void loteObligatorio() {
        crearCompra();
        assertThatThrownBy(() -> servicio.recibir(50L, new RecepcionRequest("FAC-1", null, null,
                List.of(new RecepcionRequest.Linea(linea(3L), new BigDecimal("5"), null, null))), actor))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("LOTE_OBLIGATORIO"));
        assertThatThrownBy(() -> servicio.recibir(50L, new RecepcionRequest("FAC-1", null, null,
                List.of(new RecepcionRequest.Linea(linea(3L), new BigDecimal("5"), "L-1", null))), actor))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("VENCIMIENTO_OBLIGATORIO"));
    }

    @Test @DisplayName("UT-COM-06 recepción con lote registra la fecha de vencimiento en el lote (no en el producto)")
    void recepcionConLote() {
        crearCompra();
        List<Lote> guardados = new ArrayList<>();
        when(lotes.save(any(Lote.class))).thenAnswer(a -> { Lote l = a.getArgument(0); l.setId(77L); guardados.add(l); return l; });
        servicio.recibir(50L, new RecepcionRequest("FAC-2", null, null,
                List.of(new RecepcionRequest.Linea(linea(3L), new BigDecimal("12"), "L-2026-11", LocalDate.of(2026, 11, 30)))), actor);
        assertThat(guardados).isNotEmpty();
        Lote l = guardados.get(guardados.size() - 1);
        assertThat(l.getFechaVencimiento()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(l.getDisponible()).isEqualByComparingTo("12");
        assertThat(invPegante.getDisponible()).isEqualByComparingTo("12");
        // costo neto: 8.000 − 10 % = 7.200
        assertThat(pegante.getCostoPromedio()).isEqualByComparingTo("7200");
    }

    @Test @DisplayName("UT-COM-07 no se recibe un lote ya vencido")
    void loteVencido() {
        crearCompra();
        assertThatThrownBy(() -> servicio.recibir(50L, new RecepcionRequest("FAC-1", null, null,
                List.of(new RecepcionRequest.Linea(linea(3L), new BigDecimal("5"), "L-0", LocalDate.of(2026, 1, 1)))), actor))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("LOTE_VENCIDO"));
    }

    @Test @DisplayName("UT-COM-08 reintento de la misma recepción (misma clave) no vuelve a sumar inventario")
    void recepcionIdempotente() {
        crearCompra();
        when(documentos.findByIdOperacion("rec-123456789")).thenReturn(Optional.of(DocumentoIngreso.builder().id(1L)
                .idCompra(50L).build()));
        servicio.recibir(50L, new RecepcionRequest("FAC-1", null, "rec-123456789",
                List.of(new RecepcionRequest.Linea(linea(2L), new BigDecimal("100"), null, null))), actor);
        assertThat(invTornillo.getDisponible()).isEqualByComparingTo("10");
        verify(documentos, never()).save(any());
    }

    @Test @DisplayName("UT-COM-09 una compra anulada no admite recepciones")
    void compraAnulada() {
        crearCompra();
        compra.setEstado(EstadoDocumento.ANULADO);
        assertThatThrownBy(() -> servicio.recibir(50L, new RecepcionRequest("FAC-1", null, null,
                List.of(new RecepcionRequest.Linea(linea(2L), BigDecimal.ONE, null, null))), actor))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("COMPRA_ANULADA"));
    }

    @Test @DisplayName("UT-COM-10 no se anula una compra con mercancía recibida")
    void anularConRecepciones() {
        crearCompra();
        compra.getLineas().get(0).setCantidadRecibida(BigDecimal.ONE);
        assertThatThrownBy(() -> servicio.anular(50L, "error", actor))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("COMPRA_CON_RECEPCIONES"));
    }

    @Test @DisplayName("UT-COM-11 compra en USD: el costo se pacta en USD y el costo base del promedio se guarda en COP")
    void compraEnDolares() {
        when(monedasRepo.findById("USD")).thenReturn(Optional.of(Moneda.builder().codigo("USD").activa(true).decimales((short) 2).build()));
        when(tasas.findFirstByCodigoMonedaAndFechaVigenciaLessThanEqualOrderByFechaVigenciaDescIdDesc(org.mockito.ArgumentMatchers.eq("USD"), any()))
                .thenReturn(Optional.of(TasaCambio.builder().codigoMoneda("USD").tasa(new BigDecimal("4000"))
                        .fechaVigencia(LocalDate.of(2026, 10, 15)).build()));
        CompraResponse r = servicio.crear(new CompraRequest(9L, null, null, List.of(
                new CompraRequest.Linea(2L, new BigDecimal("10"), null, new BigDecimal("2.5"), null, null)), "USD"), actor);
        LineaCompra l = compra.getLineas().get(0);
        assertThat(compra.getMoneda()).isEqualTo("USD");
        assertThat(compra.getTasaCambio()).isEqualByComparingTo("4000");
        assertThat(l.getCostoMoneda()).isEqualByComparingTo("2.5");
        assertThat(l.getCostoPactado()).isEqualByComparingTo("10000");   // 2,5 USD x 4.000
        assertThat(r.total()).isEqualByComparingTo("25.00");              // 10 x 2,5 USD
        assertThat(r.totalFuncional()).isEqualByComparingTo("100000");    // equivalente en COP
        assertThat(r.lineas().get(0).costoUnitarioMoneda()).isEqualByComparingTo("2.5");
    }
}
