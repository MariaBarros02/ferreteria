/*
 * Nombre: CompraServiceTest.java
 * Descripción: Pruebas unitarias de compras y recepción (RF-03, RF-17): conversión de unidades, recepción parcial, lotes y vencimientos, costo promedio, idempotencia y anulación.
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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ferreteria.inventario.config.ParametrosNegocio;
import com.ferreteria.inventario.domain.enums.EstadoDocumento;
import com.ferreteria.inventario.domain.enums.TipoRastreo;
import com.ferreteria.inventario.domain.model.*;
import com.ferreteria.inventario.dto.CompraRequest;
import com.ferreteria.inventario.dto.CompraResponse;
import com.ferreteria.inventario.dto.CompraResumenResponse;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.dto.RecepcionRequest;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.*;
import com.ferreteria.inventario.service.*;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/** Casos UT-COM-01 … UT-COM-24 (UT-COM-12 en adelante: casos para matar mutantes de PIT). */
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

    // ---------------------------------------------------------------------------------------------
    // Casos para matar mutantes de PIT en CompraService (UT-COM-12 … UT-COM-24)
    // ---------------------------------------------------------------------------------------------

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 16, 15, 0);
    private final List<Recepcion> recepcionesGuardadas = new ArrayList<>();
    private final List<DocumentoIngreso> documentosGuardados = new ArrayList<>();
    private final List<Lote> lotesGuardados = new ArrayList<>();

    private static String codigo(Throwable e) {
        return ((ApiException) e).getCodigo();
    }

    /** Guarda en listas lo que el servicio persiste, para poder consultarlo y anularlo después. */
    private void capturarPersistencia() {
        when(recepciones.save(any(Recepcion.class))).thenAnswer(a -> {
            Recepcion r = a.getArgument(0);
            r.setId(ids.incrementAndGet());
            recepcionesGuardadas.add(r);
            return r;
        });
        when(documentos.save(any(DocumentoIngreso.class))).thenAnswer(a -> {
            DocumentoIngreso d = a.getArgument(0);
            if (d.getId() == null) {
                d.setId(ids.incrementAndGet());
                documentosGuardados.add(d);
            }
            return d;
        });
        when(lotes.save(any(Lote.class))).thenAnswer(a -> {
            Lote l = a.getArgument(0);
            if (l.getId() == null) {
                l.setId(ids.incrementAndGet());
                lotesGuardados.add(l);
            }
            return l;
        });
    }

    /** Crea la compra y recibe pegante (con lote) y tornillo en un mismo documento, en ese orden. */
    private CompraResponse recibirAmbas() {
        crearCompra();
        capturarPersistencia();
        return servicio.recibir(50L, new RecepcionRequest("FAC-9", null, null, List.of(
                new RecepcionRequest.Linea(linea(3L), new BigDecimal("12"), "L-9", LocalDate.of(2026, 12, 31)),
                new RecepcionRequest.Linea(linea(2L), new BigDecimal("100"), null, null))), actor);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> detalleCreacion() {
        ArgumentCaptor<Map<String, Object>> c = ArgumentCaptor.forClass(Map.class);
        verify(eventos).registrar(any(), eq(Auditables.COMPRAS), eq(Auditables.CREAR), eq("COMPRA"), any(), anyString(),
                c.capture());
        return c.getValue();
    }

    @Test @DisplayName("UT-COM-12 crear con la misma clave de operación devuelve la compra existente; sin clave no se consulta")
    void crearIdempotente() {
        CompraResponse primera = crearCompra();
        verify(compras, never()).findByIdOperacion(any());

        when(compras.findByIdOperacion("oc-12345678")).thenReturn(Optional.of(compra));
        CompraResponse r = servicio.crear(new CompraRequest(9L, null, "oc-12345678", List.of(
                new CompraRequest.Linea(2L, BigDecimal.ONE, null, BigDecimal.TEN, null, null))), actor);
        assertThat(r).isNotNull();
        assertThat(r.numero()).isEqualTo(primera.numero());
        verify(compras).save(any(Compra.class));   // solo la primera vez
    }

    @Test @DisplayName("UT-COM-13 proveedor, producto, compra, línea, documento inexistentes responden 404")
    void inexistentes() {
        assertThatThrownBy(() -> servicio.crear(new CompraRequest(404L, null, null, List.of(
                new CompraRequest.Linea(2L, BigDecimal.ONE, null, BigDecimal.TEN, null, null))), actor))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
        assertThatThrownBy(() -> servicio.crear(new CompraRequest(9L, null, null, List.of(
                new CompraRequest.Linea(404L, BigDecimal.ONE, null, BigDecimal.TEN, null, null))), actor))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
        assertThatThrownBy(() -> servicio.recibir(404L, new RecepcionRequest("F", null, null,
                List.of(new RecepcionRequest.Linea(1L, BigDecimal.ONE, null, null))), actor))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
        assertThatThrownBy(() -> servicio.obtener(404L)).satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
        assertThatThrownBy(() -> servicio.anular(404L, "x", actor)).satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
        assertThatThrownBy(() -> servicio.anularIngreso(404L, "x", actor))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));

        crearCompra();
        assertThatThrownBy(() -> servicio.recibir(50L, new RecepcionRequest("F", null, null,
                List.of(new RecepcionRequest.Linea(9999L, BigDecimal.ONE, null, null))), actor))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
        when(productos.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.recibir(50L, new RecepcionRequest("F", null, null,
                List.of(new RecepcionRequest.Linea(linea(2L), BigDecimal.ONE, null, null))), actor))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
    }

    @Test @DisplayName("UT-COM-14 presentación: cantidad y costo de la caja se conservan solo si hubo conversión; observaciones se limpian")
    void presentacionYObservaciones() {
        when(conversiones.findByIdProductoAndUnidadPresentacionIgnoreCase(2L, "caja")).thenReturn(Optional.of(
                ConversionUnidad.builder().unidadPresentacion("caja").factorAUnidadBase(new BigDecimal("100"))
                        .precisionDecimales(0).build()));
        CompraResponse r = servicio.crear(new CompraRequest(9L, "  Urgente  ", null, List.of(
                new CompraRequest.Linea(2L, new BigDecimal("3"), "caja", new BigDecimal("15000"), null, null),
                new CompraRequest.Linea(3L, new BigDecimal("20"), null, new BigDecimal("8000"), null, null))), actor);
        assertThat(r.observaciones()).isEqualTo("Urgente");
        LineaCompra caja = compra.getLineas().get(0);
        assertThat(caja.getCantidadPresentacion()).isEqualByComparingTo("3");
        assertThat(caja.getCostoPresentacion()).isEqualByComparingTo("15000");
        LineaCompra suelta = compra.getLineas().get(1);
        assertThat(suelta.getCantidadPresentacion()).isNull();
        assertThat(suelta.getCostoPresentacion()).isNull();

        servicio.crear(new CompraRequest(9L, "   ", null, List.of(
                new CompraRequest.Linea(2L, BigDecimal.ONE, null, BigDecimal.TEN, null, null))), actor);
        assertThat(compra.getObservaciones()).isNull();
    }

    @Test @DisplayName("UT-COM-15 la auditoría de una compra en COP no lleva tasa; en USD lleva tasa, fecha y total en COP")
    void auditoriaSegunMoneda() {
        crearCompra();
        assertThat(detalleCreacion()).containsEntry("moneda", "COP").doesNotContainKeys("tasaCambio", "totalCop")
                .containsEntry("proveedor", "Distribuidora").containsEntry("lineas", 2);

        org.mockito.Mockito.clearInvocations(eventos);
        compraEnDolares();
        Map<String, Object> d = detalleCreacion();
        assertThat(d).containsEntry("moneda", "USD").containsEntry("fechaTasa", "2026-10-15");
        assertThat((BigDecimal) d.get("tasaCambio")).isEqualByComparingTo("4000");
        assertThat((BigDecimal) d.get("totalCop")).isEqualByComparingTo("100000");
        assertThat(servicio.obtener(50L).tasaCambio()).isEqualByComparingTo("4000");
        assertThat(servicio.obtener(50L).moneda()).isEqualTo("USD");
    }

    @Test @DisplayName("UT-COM-16 el reintento de una recepción devuelve el detalle de la compra")
    void reintentoDevuelveDetalle() {
        crearCompra();
        when(documentos.findByIdOperacion("rec-123456789")).thenReturn(Optional.of(DocumentoIngreso.builder().id(1L)
                .idCompra(50L).build()));
        CompraResponse r = servicio.recibir(50L, new RecepcionRequest("FAC-1", null, "rec-123456789",
                List.of(new RecepcionRequest.Linea(linea(2L), BigDecimal.ONE, null, null))), actor);
        assertThat(r).isNotNull();
        assertThat(r.id()).isEqualTo(50L);
        assertThat(r.numero()).isEqualTo("OC-000007");
    }

    @Test @DisplayName("UT-COM-17 recibir exactamente lo pendiente es válido y deja la compra RECIBIDA")
    void recepcionExacta() {
        crearCompra();
        servicio.recibir(50L, new RecepcionRequest("FAC-1", null, null,
                List.of(new RecepcionRequest.Linea(linea(2L), new BigDecimal("300"), null, null))), actor);
        CompraResponse r = servicio.recibir(50L, new RecepcionRequest("FAC-2", null, null,
                List.of(new RecepcionRequest.Linea(linea(3L), new BigDecimal("20"), "L-1", LocalDate.of(2026, 12, 1)))), actor);
        assertThat(r.estadoRecepcion()).isEqualTo("RECIBIDA");
        assertThat(invTornillo.getDisponible()).isEqualByComparingTo("310");
    }

    @Test @DisplayName("UT-COM-18 las líneas se procesan ordenadas por producto (mismo orden de bloqueo, evita interbloqueos)")
    void ordenDeBloqueo() {
        recibirAmbas();
        InOrder orden = inOrder(inventarios);
        orden.verify(inventarios).findForUpdate(2L);
        orden.verify(inventarios).findForUpdate(3L);
    }

    @Test @DisplayName("UT-COM-19 anular un ingreso revierte cantidades, lote y línea, y registra quién, cuándo y por qué")
    void anularIngreso() {
        recibirAmbas();
        DocumentoIngreso doc = documentosGuardados.get(0);
        Lote lote = lotesGuardados.get(0);
        when(documentos.findForUpdate(doc.getId())).thenReturn(Optional.of(doc));
        when(lotes.findForUpdate(lote.getId())).thenReturn(Optional.of(lote));
        // el repositorio devuelve las recepciones con el pegante primero: el servicio debe reordenarlas
        when(recepciones.findByIdDocumentoIngresoOrderByIdAsc(doc.getId())).thenReturn(recepcionesGuardadas.stream()
                .sorted((a, b) -> Long.compare(b.getIdLineaCompra(), a.getIdLineaCompra())).toList());
        org.mockito.Mockito.clearInvocations(inventarios);

        CompraResponse r = servicio.anularIngreso(doc.getId(), "  Factura errada  ", actor);

        assertThat(r).isNotNull();
        assertThat(r.estadoRecepcion()).isEqualTo("PENDIENTE");
        assertThat(invTornillo.getDisponible()).isEqualByComparingTo("10");
        assertThat(invPegante.getDisponible()).isEqualByComparingTo("0");
        assertThat(lote.getDisponible()).isEqualByComparingTo("0");
        assertThat(compra.getLineas()).allSatisfy(l -> assertThat(l.getCantidadRecibida()).isEqualByComparingTo("0"));
        assertThat(doc.getEstado()).isEqualTo(EstadoDocumento.ANULADO);
        assertThat(doc.getMotivoAnulacion()).isEqualTo("Factura errada");
        assertThat(doc.getFechaAnulacion()).isEqualTo(AHORA);
        assertThat(doc.getIdUsuarioAnulacion()).isEqualTo(3);
        verify(compras, times(2)).findForUpdate(50L);   // recepción + anulación (mismo orden de bloqueo)
        InOrder orden = inOrder(inventarios);
        orden.verify(inventarios).findForUpdate(2L);
        orden.verify(inventarios).findForUpdate(3L);

        assertThatThrownBy(() -> servicio.anularIngreso(doc.getId(), "otra vez", actor))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("DOCUMENTO_ANULADO"));
    }

    @Test @DisplayName("UT-COM-20 anular una compra sin recepciones la deja ANULADA con motivo y fecha")
    void anularSinRecepciones() {
        crearCompra();
        CompraResponse r = servicio.anular(50L, "  Proveedor canceló  ", actor);
        assertThat(r).isNotNull();
        assertThat(r.estado()).isEqualTo(EstadoDocumento.ANULADO);
        assertThat(r.estadoRecepcion()).isEqualTo("ANULADA");
        assertThat(r.motivoAnulacion()).isEqualTo("Proveedor canceló");
        assertThat(compra.getFechaAnulacion()).isEqualTo(AHORA);
    }

    @Test @DisplayName("UT-COM-21 el detalle muestra producto, proveedor, usuario y las recepciones con su lote")
    void detalleCompleto() {
        tornillo.setCodigoBarras("7701234567890");
        when(usuarios.findById(3)).thenReturn(Optional.of(Usuario.builder().id(3).correo("marta.inventario@ferreteria.com").build()));
        recibirAmbas();
        DocumentoIngreso doc = documentosGuardados.get(0);
        Lote lote = lotesGuardados.get(0);
        when(documentos.findByIdCompraOrderByIdDesc(50L)).thenReturn(List.of(doc));
        when(recepciones.findByIdDocumentoIngresoOrderByIdAsc(doc.getId())).thenReturn(recepcionesGuardadas);
        when(lotes.findById(lote.getId())).thenReturn(Optional.of(lote));

        CompraResponse r = servicio.obtener(50L);

        assertThat(r).isNotNull();
        assertThat(r.proveedor()).isEqualTo("Distribuidora");
        assertThat(r.proveedorIdentificacion()).isEqualTo("900123456");
        assertThat(r.usuario()).isEqualTo("marta.inventario@ferreteria.com");
        assertThat(r.moneda()).isEqualTo("COP");
        CompraResponse.Linea t = r.lineas().get(0);
        assertThat(t.producto()).isEqualTo("Tornillo Genérico 1 pulgada");
        assertThat(t.codigoBarras()).isEqualTo("7701234567890");
        assertThat(t.unidadBase()).isEqualTo("unidad");
        assertThat(t.tipoRastreo()).isEqualTo(TipoRastreo.NINGUNO);
        assertThat(t.tieneVencimiento()).isFalse();
        assertThat(r.lineas().get(1).tieneVencimiento()).isTrue();

        CompraResponse.Documento d = r.documentos().get(0);
        assertThat(d.usuario()).isEqualTo("marta.inventario@ferreteria.com");
        CompraResponse.Recepcion conLote = d.recepciones().stream().filter(x -> x.lote() != null).findFirst().orElseThrow();
        assertThat(conLote.productoId()).isEqualTo(3L);
        assertThat(conLote.producto()).isEqualTo("Pegante Sika 250ml");
        assertThat(conLote.lote()).isEqualTo("L-9");
        assertThat(conLote.fechaVencimiento()).isEqualTo(LocalDate.of(2026, 12, 31));
        CompraResponse.Recepcion sinLote = d.recepciones().stream().filter(x -> x.lote() == null).findFirst().orElseThrow();
        assertThat(sinLote.productoId()).isEqualTo(2L);
        assertThat(sinLote.fechaVencimiento()).isNull();
    }

    @Test @DisplayName("UT-COM-22 compras anteriores a V5 (sin moneda ni tasa) se muestran en COP con tasa 1")
    void compraHistoricaSinMoneda() {
        crearCompra();
        compra.setMoneda(null);
        compra.setTasaCambio(null);
        CompraResponse r = servicio.obtener(50L);
        assertThat(r.moneda()).isEqualTo("COP");
        assertThat(r.tasaCambio()).isEqualByComparingTo("1");
    }

    @Test @DisplayName("UT-COM-23 estado de recepción: una compra sin líneas está PENDIENTE")
    void estadoSinLineas() {
        Compra vacia = Compra.builder().estado(EstadoDocumento.ACTIVO).build();
        assertThat(CompraService.estadoRecepcion(vacia)).isEqualTo("PENDIENTE");
    }

    @Test @DisplayName("UT-COM-24 listar: resumen con proveedor y totales, paginación acotada y filtros de la especificación")
    @SuppressWarnings("unchecked")
    void listarYFiltros() {
        crearCompra();
        when(compras.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(a -> new PageImpl<>(List.of(compra), a.getArgument(1), 1));
        LocalDate desde = LocalDate.of(2026, 10, 1);
        LocalDate hasta = LocalDate.of(2026, 10, 31);
        PaginaResponse<CompraResumenResponse> p = servicio.listar(
                new CompraService.Filtros("  OC-7%_ ", 9L, EstadoDocumento.ACTIVO, desde, hasta), -1, 500);
        assertThat(p).isNotNull();
        assertThat(p.pagina()).isZero();
        assertThat(p.tamano()).isEqualTo(100);
        CompraResumenResponse c = p.contenido().get(0);
        assertThat(c.proveedor()).isEqualTo("Distribuidora");
        assertThat(c.numero()).isEqualTo("OC-000007");
        assertThat(c.lineas()).isEqualTo(2);
        assertThat(c.total()).isEqualByComparingTo("197550");
        assertThat(c.moneda()).isEqualTo("COP");
        assertThat(c.estadoRecepcion()).isEqualTo("PENDIENTE");

        servicio.listar(new CompraService.Filtros("   ", null, null, null, null), 0, 10);
        ArgumentCaptor<Specification<Compra>> spec = ArgumentCaptor.forClass(Specification.class);
        verify(compras, times(2)).findAll(spec.capture(), any(Pageable.class));

        java.time.ZoneId zona = ParametrosNegocio.porDefecto().getZonaHoraria();
        Root<Compra> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<String> pNumero = mock(Path.class);
        Path<Object> pProveedor = mock(Path.class);
        Path<Object> pEstado = mock(Path.class);
        Path<LocalDateTime> pFecha = mock(Path.class);
        Expression<String> minusculas = mock(Expression.class);
        Predicate porNumero = mock(Predicate.class);
        Predicate porProveedor = mock(Predicate.class);
        Predicate porEstado = mock(Predicate.class);
        Predicate porDesde = mock(Predicate.class);
        Predicate porHasta = mock(Predicate.class);
        Predicate todos = mock(Predicate.class);
        Predicate ninguno = mock(Predicate.class);
        doReturn(pNumero).when(root).get("numero");
        doReturn(pProveedor).when(root).get("idProveedor");
        doReturn(pEstado).when(root).get("estado");
        doReturn(pFecha).when(root).get("fecha");
        when(cb.lower(pNumero)).thenReturn(minusculas);
        when(cb.like(minusculas, "%oc-7%")).thenReturn(porNumero);   // sin espacios, en minúsculas y sin comodines
        when(cb.equal(pProveedor, 9L)).thenReturn(porProveedor);
        when(cb.equal(pEstado, EstadoDocumento.ACTIVO)).thenReturn(porEstado);
        when(cb.greaterThanOrEqualTo(pFecha, Fechas.inicioUtc(desde, zona))).thenReturn(porDesde);
        when(cb.lessThan(pFecha, Fechas.finExclusivoUtc(hasta, zona))).thenReturn(porHasta);
        when(cb.and(porNumero, porProveedor, porEstado, porDesde, porHasta)).thenReturn(todos);
        when(cb.and()).thenReturn(ninguno);

        assertThat(spec.getAllValues().get(0).toPredicate(root, query, cb)).isSameAs(todos);
        assertThat(spec.getAllValues().get(1).toPredicate(root, query, cb)).isSameAs(ninguno);
    }
}