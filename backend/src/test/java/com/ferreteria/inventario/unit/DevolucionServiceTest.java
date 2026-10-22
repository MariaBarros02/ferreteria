/*
 * Nombre: DevolucionServiceTest.java
 * Descripción: Pruebas unitarias de devoluciones (RF-13): parciales y múltiples, rechazo por exceso, destino según estado del producto, importe proporcional, lotes, devolución a proveedor, consultas y filtros.
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ferreteria.inventario.config.ParametrosNegocio;
import com.ferreteria.inventario.domain.enums.*;
import com.ferreteria.inventario.domain.model.*;
import com.ferreteria.inventario.dto.DevolucionClienteRequest;
import com.ferreteria.inventario.dto.DevolucionProveedorRequest;
import com.ferreteria.inventario.dto.DevolucionResponse;
import com.ferreteria.inventario.dto.PaginaResponse;
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
import java.time.ZoneId;
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

/** Casos UT-DEV-01 … UT-DEV-22 (UT-DEV-08 en adelante: casos para matar mutantes de PIT). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DevolucionServiceTest {

    @Mock DocumentoDevolucionRepository documentos;
    @Mock DevolucionRepository lineasDevolucion;
    @Mock VentaRepository ventas;
    @Mock CompraRepository compras;
    @Mock LineaCompraRepository lineasCompra;
    @Mock LineaVentaLoteRepository lineaVentaLotes;
    @Mock LoteRepository lotes;
    @Mock ProductoRepository productos;
    @Mock TerceroRepository terceros;
    @Mock UsuarioRepository usuarios;
    @Mock InventarioRepository inventarios;
    @Mock CierrePeriodoRepository cierres;
    @Mock AuditoriaService auditoria;
    @Mock EventoAuditoriaService eventos;

    DevolucionService servicio;
    Inventario inv;
    Venta venta;
    LineaVenta linea;
    final List<Devolucion> guardadas = new ArrayList<>();
    final AtomicLong ids = new AtomicLong(500);
    final Actor actor = new Actor(1, "laura.ventas@ferreteria.com", "10.0.0.1");

    @BeforeEach
    void preparar() {
        Clock reloj = Clock.fixed(Instant.parse("2026-10-16T15:00:00Z"), ZoneOffset.UTC);
        ParametrosNegocio parametros = ParametrosNegocio.porDefecto();
        KardexService kardex = new KardexService(inventarios, productos, auditoria, cierres, parametros, reloj);
        servicio = new DevolucionService(documentos, lineasDevolucion, ventas, compras, lineasCompra, lineaVentaLotes, lotes,
                productos, terceros, usuarios, kardex, new LoteService(lotes), eventos, parametros);
        Producto taladro = Producto.builder().id(10L).nombre("Taladro").marca("Bosch").presentacion("500W")
                .unidadBase("unidad").tipoRastreo(TipoRastreo.NINGUNO).build();
        inv = Inventario.builder().idProducto(10L).disponible(new BigDecimal("16")).reservado(BigDecimal.ZERO)
                .danado(BigDecimal.ZERO).build();
        venta = Venta.builder().id(1L).numeroInterno("VTA-001000").idCliente(4L).estado(EstadoDocumento.ACTIVO).build();
        // 2 unidades a 650.000 con IVA 19 %: total de la línea 1.547.000
        linea = LineaVenta.builder().id(20L).venta(venta).idProducto(10L).cantidad(new BigDecimal("2"))
                .precioAplicado(new BigDecimal("650000")).subtotal(new BigDecimal("1300000")).impuesto(new BigDecimal("247000"))
                .total(new BigDecimal("1547000")).build();
        venta.getLineas().add(linea);
        when(productos.findById(10L)).thenReturn(Optional.of(taladro));
        when(inventarios.findForUpdate(10L)).thenReturn(Optional.of(inv));
        when(ventas.findForUpdate(1L)).thenReturn(Optional.of(venta));
        when(ventas.findById(1L)).thenReturn(Optional.of(venta));
        when(documentos.siguienteNumero()).thenReturn(1L);
        when(documentos.save(any(DocumentoDevolucion.class))).thenAnswer(a -> {
            DocumentoDevolucion d = a.getArgument(0);
            if (d.getId() == null) {
                d.setId(ids.incrementAndGet());
            }
            return d;
        });
        when(lineasDevolucion.save(any(Devolucion.class))).thenAnswer(a -> { Devolucion d = a.getArgument(0); guardadas.add(d); return d; });
        when(lineasDevolucion.devueltoDeLineaVenta(eq(20L), any())).thenAnswer(a -> guardadas.stream()
                .map(Devolucion::getCantidad).reduce(BigDecimal.ZERO, BigDecimal::add));
        when(lineasDevolucion.findByIdDocumentoDevolucionOrderByIdAsc(anyLong())).thenAnswer(a -> guardadas);
        when(auditoria.registrar(any(), anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(a -> MovimientoInventario.builder().id(ids.incrementAndGet()).build());
        when(usuarios.findById(any())).thenReturn(Optional.empty());
    }

    private DevolucionResponse devolver(String cantidad, EstadoProductoDevuelto estado) {
        return servicio.registrarCliente(new DevolucionClienteRequest(1L, "Cliente insatisfecho", TratamientoDevolucion.REEMBOLSO,
                null, null, List.of(new DevolucionClienteRequest.Linea(20L, new BigDecimal(cantidad), estado))), actor);
    }

    @Test @DisplayName("UT-DEV-01 devolución parcial apta vuelve a disponible con importe proporcional (incluye IVA)")
    void parcialApta() {
        DevolucionResponse r = devolver("1", EstadoProductoDevuelto.APTO);
        assertThat(inv.getDisponible()).isEqualByComparingTo("17");
        assertThat(r.total()).isEqualByComparingTo("773500.00");
        assertThat(r.numero()).isEqualTo("DEV-000001");
        assertThat(guardadas.get(0).getIdLineaVenta()).isEqualTo(20L);
    }

    @Test @DisplayName("UT-DEV-02 producto dañado no se reintegra al disponible: va al saldo no apto")
    void danadoNoDisponible() {
        devolver("1", EstadoProductoDevuelto.DANADO);
        assertThat(inv.getDisponible()).isEqualByComparingTo("16");
        assertThat(inv.getDanado()).isEqualByComparingTo("1");
    }

    @Test @DisplayName("UT-DEV-03 devoluciones múltiples: la segunda se valida contra lo ya devuelto")
    void multiples() {
        devolver("1", EstadoProductoDevuelto.APTO);
        devolver("1", EstadoProductoDevuelto.VENCIDO);
        assertThat(inv.getDisponible()).isEqualByComparingTo("17");
        assertThat(inv.getDanado()).isEqualByComparingTo("1");
        assertThatThrownBy(() -> devolver("0.5", EstadoProductoDevuelto.APTO))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("DEVOLUCION_EXCEDE"));
    }

    @Test @DisplayName("UT-DEV-04 una devolución que excede lo vendido se rechaza y no altera saldos")
    void excede() {
        assertThatThrownBy(() -> devolver("3", EstadoProductoDevuelto.APTO))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("DEVOLUCION_EXCEDE"));
        assertThat(inv.getDisponible()).isEqualByComparingTo("16");
    }

    @Test @DisplayName("UT-DEV-05 la línea debe pertenecer a la venta indicada")
    void lineaAjena() {
        assertThatThrownBy(() -> servicio.registrarCliente(new DevolucionClienteRequest(1L, "x", TratamientoDevolucion.NINGUNO,
                null, null, List.of(new DevolucionClienteRequest.Linea(999L, BigDecimal.ONE, EstadoProductoDevuelto.APTO))), actor))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("LINEA_AJENA"));
    }

    @Test @DisplayName("UT-DEV-06 la factura original no se modifica (la venta conserva su total y líneas)")
    void noModificaVenta() {
        devolver("2", EstadoProductoDevuelto.APTO);
        assertThat(linea.getCantidad()).isEqualByComparingTo("2");
        assertThat(linea.getTotal()).isEqualByComparingTo("1547000");
        assertThat(venta.getEstado()).isEqualTo(EstadoDocumento.ACTIVO);
    }

    @Test @DisplayName("UT-DEV-07 devolución a proveedor desde no apto descuenta el saldo dañado")
    void proveedorDesdeDanado() {
        inv.setDanado(new BigDecimal("3"));
        Compra c = Compra.builder().id(5L).numero("OC-000005").idProveedor(9L).estado(EstadoDocumento.ACTIVO).build();
        LineaCompra lc = LineaCompra.builder().id(30L).compra(c).idProducto(10L).cantidadPedida(new BigDecimal("10"))
                .cantidadRecibida(new BigDecimal("10")).costoPactado(new BigDecimal("500000")).build();
        when(compras.findForUpdate(5L)).thenReturn(Optional.of(c));
        when(lineasCompra.findForUpdate(30L)).thenReturn(Optional.of(lc));
        when(lineasDevolucion.devueltoDeLineaCompra(eq(30L), any())).thenReturn(BigDecimal.ZERO);
        DevolucionResponse r = servicio.registrarProveedor(new DevolucionProveedorRequest(5L, "Defecto de fábrica",
                TratamientoDevolucion.NOTA_CREDITO_PROVEEDOR, null, null,
                List.of(new DevolucionProveedorRequest.Linea(30L, new BigDecimal("2"), EstadoInventario.DANADO, null))), actor);
        assertThat(inv.getDanado()).isEqualByComparingTo("1");
        assertThat(inv.getDisponible()).isEqualByComparingTo("16");
        assertThat(r.total()).isEqualByComparingTo("1000000");
    }

    // ---------------------------------------------------------------------------------------------
    // Casos para matar mutantes de PIT en DevolucionService (UT-DEV-08 … UT-DEV-22)
    // ---------------------------------------------------------------------------------------------

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 16, 15, 0);
    Inventario invMartillo;

    private static String codigo(Throwable e) {
        return ((ApiException) e).getCodigo();
    }

    /** El taladro pasa a rastrearse por lote. */
    private void taladroPorLote() {
        when(productos.findById(10L)).thenReturn(Optional.of(Producto.builder().id(10L).nombre("Taladro").marca("Bosch")
                .presentacion("500W").unidadBase("unidad").tipoRastreo(TipoRastreo.LOTE).build()));
    }

    /** Segundo producto (id 11) con su inventario y una línea en la venta (id 21). */
    private void martilloEnLaVenta() {
        when(productos.findById(11L)).thenReturn(Optional.of(Producto.builder().id(11L).nombre("Martillo").marca("Stanley")
                .presentacion("16oz").unidadBase("unidad").tipoRastreo(TipoRastreo.NINGUNO).build()));
        invMartillo = Inventario.builder().idProducto(11L).disponible(new BigDecimal("5")).reservado(BigDecimal.ZERO)
                .danado(BigDecimal.ZERO).build();
        when(inventarios.findForUpdate(11L)).thenReturn(Optional.of(invMartillo));
        venta.getLineas().add(LineaVenta.builder().id(21L).venta(venta).idProducto(11L).cantidad(new BigDecimal("3"))
                .precioAplicado(new BigDecimal("20000")).total(new BigDecimal("60000")).build());
        when(lineasDevolucion.devueltoDeLineaVenta(eq(21L), any())).thenReturn(BigDecimal.ZERO);
    }

    /** Compra 5 con la línea 30 (taladro, 10 recibidos) y la línea 31 (martillo, 4 recibidos). */
    private Compra compraConLineas() {
        Compra c = Compra.builder().id(5L).numero("OC-000005").idProveedor(9L).estado(EstadoDocumento.ACTIVO).build();
        LineaCompra lc30 = LineaCompra.builder().id(30L).compra(c).idProducto(10L).cantidadPedida(new BigDecimal("10"))
                .cantidadRecibida(new BigDecimal("10")).costoPactado(new BigDecimal("500000")).build();
        LineaCompra lc31 = LineaCompra.builder().id(31L).compra(c).idProducto(11L).cantidadPedida(new BigDecimal("4"))
                .cantidadRecibida(new BigDecimal("4")).costoPactado(new BigDecimal("20000")).build();
        when(compras.findForUpdate(5L)).thenReturn(Optional.of(c));
        when(compras.findById(5L)).thenReturn(Optional.of(c));
        when(lineasCompra.findForUpdate(30L)).thenReturn(Optional.of(lc30));
        when(lineasCompra.findForUpdate(31L)).thenReturn(Optional.of(lc31));
        when(lineasDevolucion.devueltoDeLineaCompra(anyLong(), any())).thenAnswer(a -> guardadas.stream()
                .filter(d -> a.getArgument(0).equals(d.getIdLineaCompra()))
                .map(Devolucion::getCantidad).reduce(BigDecimal.ZERO, BigDecimal::add));
        return c;
    }

    private DevolucionResponse devolverAlProveedor(Long idLinea, String cantidad, EstadoInventario origen, Long idLote) {
        return servicio.registrarProveedor(new DevolucionProveedorRequest(5L, "Defecto de fábrica",
                TratamientoDevolucion.NOTA_CREDITO_PROVEEDOR, null, null,
                List.of(new DevolucionProveedorRequest.Linea(idLinea, new BigDecimal(cantidad), origen, idLote))), actor);
    }

    private Lote lote(Long id, String codigo, LocalDate vence) {
        Lote l = Lote.builder().id(id).idProducto(10L).codigo(codigo).fechaVencimiento(vence)
                .disponible(new BigDecimal("5")).danado(new BigDecimal("2")).build();
        when(lotes.findForUpdate(id)).thenReturn(Optional.of(l));
        when(lotes.findById(id)).thenReturn(Optional.of(l));
        when(lotes.save(any(Lote.class))).thenAnswer(a -> a.getArgument(0));
        return l;
    }

    /** Lotes de los que salió la venta (línea 20). */
    private void vendidoDeLotes(LineaVentaLote... asignaciones) {
        when(lineaVentaLotes.findById_IdLineaVenta(20L)).thenReturn(List.of(asignaciones));
        when(lineasDevolucion.devueltoDeLineaVentaYLote(anyLong(), anyLong(), any())).thenReturn(BigDecimal.ZERO);
    }

    private static LineaVentaLote vendido(Long idLote, String cantidad) {
        return LineaVentaLote.builder().id(new LineaVentaLoteId(20L, idLote)).cantidad(new BigDecimal(cantidad)).build();
    }

    private List<Long> lotesDevueltos() {
        return guardadas.stream().map(Devolucion::getIdLote).toList();
    }

    @Test @DisplayName("UT-DEV-08 la misma clave de operación devuelve la devolución existente (cliente y proveedor) sin mover saldos")
    void idempotente() {
        DocumentoDevolucion previa = DocumentoDevolucion.builder().id(77L).numero("DEV-000077").tipo(TipoDevolucion.CLIENTE)
                .idVenta(1L).idUsuario(1).motivo("x").tratamiento(TratamientoDevolucion.REEMBOLSO)
                .estado(EstadoDocumento.ACTIVO).build();
        when(documentos.findByIdOperacion("dev-12345678")).thenReturn(Optional.of(previa));

        DevolucionResponse r = servicio.registrarCliente(new DevolucionClienteRequest(1L, "x", TratamientoDevolucion.REEMBOLSO,
                null, "dev-12345678", List.of(new DevolucionClienteRequest.Linea(20L, BigDecimal.ONE,
                EstadoProductoDevuelto.APTO))), actor);
        assertThat(r).isNotNull();
        assertThat(r.numero()).isEqualTo("DEV-000077");

        DevolucionResponse p = servicio.registrarProveedor(new DevolucionProveedorRequest(5L, "x",
                TratamientoDevolucion.NOTA_CREDITO_PROVEEDOR, null, "dev-12345678",
                List.of(new DevolucionProveedorRequest.Linea(30L, BigDecimal.ONE, EstadoInventario.DISPONIBLE, null))), actor);
        assertThat(p).isNotNull();
        assertThat(p.numero()).isEqualTo("DEV-000077");

        assertThat(inv.getDisponible()).isEqualByComparingTo("16");
        verify(documentos, never()).save(any());
        verify(compras, never()).findForUpdate(anyLong());
    }

    @Test @DisplayName("UT-DEV-09 sin clave de operación no se consulta la idempotencia")
    void sinClaveNoConsulta() {
        devolver("1", EstadoProductoDevuelto.APTO);
        verify(documentos, never()).findByIdOperacion(any());
    }

    @Test @DisplayName("UT-DEV-10 venta, compra, línea de compra, producto o devolución inexistentes responden 404")
    void inexistentes() {
        assertThatThrownBy(() -> servicio.registrarCliente(new DevolucionClienteRequest(99L, "x", TratamientoDevolucion.NINGUNO,
                null, null, List.of(new DevolucionClienteRequest.Linea(20L, BigDecimal.ONE, EstadoProductoDevuelto.APTO))), actor))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
        assertThatThrownBy(() -> servicio.registrarProveedor(new DevolucionProveedorRequest(99L, "x",
                TratamientoDevolucion.NINGUNO, null, null,
                List.of(new DevolucionProveedorRequest.Linea(30L, BigDecimal.ONE, EstadoInventario.DISPONIBLE, null))), actor))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
        assertThatThrownBy(() -> servicio.obtener(99L)).satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));

        compraConLineas();
        assertThatThrownBy(() -> devolverAlProveedor(999L, "1", EstadoInventario.DISPONIBLE, null))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));

        when(productos.findById(10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> devolver("1", EstadoProductoDevuelto.APTO))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
        assertThatThrownBy(() -> devolverAlProveedor(30L, "1", EstadoInventario.DISPONIBLE, null))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
        assertThat(inv.getDisponible()).isEqualByComparingTo("16");
    }

    @Test @DisplayName("UT-DEV-11 devolución de cliente: las líneas se procesan ordenadas por producto (mismo orden de bloqueo)")
    void ordenCliente() {
        martilloEnLaVenta();
        DevolucionResponse r = servicio.registrarCliente(new DevolucionClienteRequest(1L, "x", TratamientoDevolucion.REEMBOLSO,
                null, null, List.of(
                new DevolucionClienteRequest.Linea(21L, BigDecimal.ONE, EstadoProductoDevuelto.APTO),
                new DevolucionClienteRequest.Linea(20L, BigDecimal.ONE, EstadoProductoDevuelto.APTO))), actor);
        InOrder orden = inOrder(inventarios);
        orden.verify(inventarios).findForUpdate(10L);
        orden.verify(inventarios).findForUpdate(11L);
        assertThat(invMartillo.getDisponible()).isEqualByComparingTo("6");
        assertThat(r.total()).isEqualByComparingTo("793500.00");   // 773.500 + 20.000
    }

    @Test @DisplayName("UT-DEV-12 devolución a proveedor: las líneas se procesan ordenadas por producto (mismo orden de bloqueo)")
    void ordenProveedor() {
        martilloEnLaVenta();
        compraConLineas();
        servicio.registrarProveedor(new DevolucionProveedorRequest(5L, "x", TratamientoDevolucion.NOTA_CREDITO_PROVEEDOR,
                null, null, List.of(
                new DevolucionProveedorRequest.Linea(31L, BigDecimal.ONE, EstadoInventario.DISPONIBLE, null),
                new DevolucionProveedorRequest.Linea(30L, BigDecimal.ONE, EstadoInventario.DISPONIBLE, null))), actor);
        InOrder orden = inOrder(inventarios);
        orden.verify(inventarios).findForUpdate(10L);
        orden.verify(inventarios).findForUpdate(11L);
        assertThat(inv.getDisponible()).isEqualByComparingTo("15");
        assertThat(invMartillo.getDisponible()).isEqualByComparingTo("4");
    }

    @Test @DisplayName("UT-DEV-13 a proveedor se puede devolver exactamente lo recibido, pero no más")
    void proveedorAlLimite() {
        compraConLineas();
        assertThatThrownBy(() -> devolverAlProveedor(30L, "11", EstadoInventario.DISPONIBLE, null))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("DEVOLUCION_EXCEDE"));
        DevolucionResponse r = devolverAlProveedor(30L, "10", EstadoInventario.DISPONIBLE, null);
        assertThat(r.total()).isEqualByComparingTo("5000000");
        assertThat(inv.getDisponible()).isEqualByComparingTo("6");
        assertThatThrownBy(() -> devolverAlProveedor(30L, "1", EstadoInventario.DISPONIBLE, null))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("DEVOLUCION_EXCEDE"));
    }

    @Test @DisplayName("UT-DEV-14 a proveedor con lote indicado: descuenta del lote según el origen (disponible o no apto)")
    void proveedorConLote() {
        compraConLineas();
        Lote l7 = lote(7L, "L-7", LocalDate.of(2026, 12, 31));

        DevolucionResponse r = devolverAlProveedor(30L, "2", EstadoInventario.DISPONIBLE, 7L);
        assertThat(l7.getDisponible()).isEqualByComparingTo("3");
        assertThat(l7.getDanado()).isEqualByComparingTo("2");
        assertThat(inv.getDisponible()).isEqualByComparingTo("14");
        assertThat(guardadas.get(0).getIdLote()).isEqualTo(7L);
        assertThat(r.lineas().get(0).lote()).isEqualTo("L-7");

        inv.setDanado(new BigDecimal("3"));
        devolverAlProveedor(30L, "1", EstadoInventario.DANADO, 7L);
        assertThat(l7.getDisponible()).isEqualByComparingTo("3");
        assertThat(l7.getDanado()).isEqualByComparingTo("1");
        assertThat(inv.getDanado()).isEqualByComparingTo("2");
    }

    @Test @DisplayName("UT-DEV-15 a proveedor, producto por lote sin lote indicado: solo se devuelve la existencia sin lote del origen")
    void proveedorPorLoteSinLote() {
        taladroPorLote();
        compraConLineas();
        inv.setDanado(new BigDecimal("3"));
        when(lotes.sumaDisponible(10L)).thenReturn(new BigDecimal("15"));   // disponible sin lote: 16 − 15 = 1
        when(lotes.sumaDanado(10L)).thenReturn(new BigDecimal("3"));        // no apto sin lote: 3 − 3 = 0

        assertThatThrownBy(() -> devolverAlProveedor(30L, "1", EstadoInventario.DANADO, null))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("LOTE_OBLIGATORIO"));

        when(lotes.sumaDanado(10L)).thenReturn(new BigDecimal("2"));        // no apto sin lote: 1
        devolverAlProveedor(30L, "1", EstadoInventario.DANADO, null);
        assertThat(inv.getDanado()).isEqualByComparingTo("2");

        assertThatThrownBy(() -> devolverAlProveedor(30L, "2", EstadoInventario.DISPONIBLE, null))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("LOTE_OBLIGATORIO"));
        devolverAlProveedor(30L, "1", EstadoInventario.DISPONIBLE, null);   // exactamente lo que hay sin lote
        assertThat(inv.getDisponible()).isEqualByComparingTo("15");
    }

    @Test @DisplayName("UT-DEV-16 cliente, producto por lote: el lote vigente vuelve a disponible y el lote vencido va a no apto")
    void clienteConLotes() {
        taladroPorLote();
        Lote vigente = lote(7L, "L-7", LocalDate.of(2026, 12, 31));
        Lote vencido = lote(8L, "L-8", LocalDate.of(2026, 1, 1));
        vendidoDeLotes(vendido(7L, "1"), vendido(8L, "1"));

        DevolucionResponse r = devolver("2", EstadoProductoDevuelto.APTO);

        assertThat(lotesDevueltos()).containsExactly(7L, 8L);
        assertThat(vigente.getDisponible()).isEqualByComparingTo("6");
        assertThat(vigente.getDanado()).isEqualByComparingTo("2");
        assertThat(vencido.getDisponible()).isEqualByComparingTo("5");
        assertThat(vencido.getDanado()).isEqualByComparingTo("3");
        assertThat(inv.getDisponible()).isEqualByComparingTo("17");
        assertThat(inv.getDanado()).isEqualByComparingTo("1");
        assertThat(r.lineas()).extracting(DevolucionResponse.Linea::lote).containsExactly("L-7", "L-8");
        assertThat(r.total()).isEqualByComparingTo("1547000");
    }

    @Test @DisplayName("UT-DEV-17 cliente, producto por lote: una devolución parcial usa solo el primer lote necesario")
    void clienteParcialUnLote() {
        taladroPorLote();
        Lote vigente = lote(7L, "L-7", LocalDate.of(2026, 12, 31));
        Lote otro = lote(8L, "L-8", LocalDate.of(2026, 12, 31));
        vendidoDeLotes(vendido(7L, "1"), vendido(8L, "1"));

        devolver("1", EstadoProductoDevuelto.APTO);

        assertThat(guardadas).hasSize(1);
        assertThat(lotesDevueltos()).containsExactly(7L);
        assertThat(vigente.getDisponible()).isEqualByComparingTo("6");
        assertThat(otro.getDisponible()).isEqualByComparingTo("5");
    }

    @Test @DisplayName("UT-DEV-18 cliente, producto por lote: un lote ya devuelto por completo se salta")
    void clienteLoteYaDevuelto() {
        taladroPorLote();
        lote(7L, "L-7", LocalDate.of(2026, 12, 31));
        Lote vencido = lote(8L, "L-8", LocalDate.of(2026, 1, 1));
        vendidoDeLotes(vendido(7L, "1"), vendido(8L, "1"));
        when(lineasDevolucion.devueltoDeLineaVentaYLote(20L, 7L, EstadoDocumento.ACTIVO)).thenReturn(BigDecimal.ONE);

        devolver("1", EstadoProductoDevuelto.APTO);

        assertThat(guardadas).hasSize(1);
        assertThat(lotesDevueltos()).containsExactly(8L);
        assertThat(vencido.getDanado()).isEqualByComparingTo("3");
        assertThat(inv.getDanado()).isEqualByComparingTo("1");
        assertThat(inv.getDisponible()).isEqualByComparingTo("16");
    }

    @Test @DisplayName("UT-DEV-19 cliente, producto por lote: lo que no salió de un lote se devuelve como existencia sin lote")
    void clienteRestoSinLote() {
        taladroPorLote();
        Lote vigente = lote(7L, "L-7", LocalDate.of(2026, 12, 31));
        vendidoDeLotes(vendido(7L, "1"));

        devolver("2", EstadoProductoDevuelto.APTO);

        assertThat(lotesDevueltos()).containsExactly(7L, null);
        assertThat(vigente.getDisponible()).isEqualByComparingTo("6");
        assertThat(inv.getDisponible()).isEqualByComparingTo("18");
    }

    @Test @DisplayName("UT-DEV-20 la respuesta de cliente muestra venta, cliente, usuario, fecha, producto y observaciones limpias")
    void respuestaCliente() {
        when(terceros.findById(4L)).thenReturn(Optional.of(Tercero.builder().id(4L).razonSocial("Constructora XYZ").build()));
        when(usuarios.findById(1)).thenReturn(Optional.of(Usuario.builder().id(1).correo("laura.ventas@ferreteria.com").build()));

        DevolucionResponse r = servicio.registrarCliente(new DevolucionClienteRequest(1L, "  Cliente insatisfecho  ",
                TratamientoDevolucion.REEMBOLSO, "  Caja abierta  ", null,
                List.of(new DevolucionClienteRequest.Linea(20L, BigDecimal.ONE, EstadoProductoDevuelto.APTO))), actor);

        assertThat(r.tipo()).isEqualTo(TipoDevolucion.CLIENTE);
        assertThat(r.ventaNumero()).isEqualTo("VTA-001000");
        assertThat(r.compraNumero()).isNull();
        assertThat(r.tercero()).isEqualTo("Constructora XYZ");
        assertThat(r.usuario()).isEqualTo("laura.ventas@ferreteria.com");
        assertThat(r.fecha()).isEqualTo(AHORA);
        assertThat(r.motivo()).isEqualTo("Cliente insatisfecho");
        assertThat(r.observaciones()).isEqualTo("Caja abierta");
        DevolucionResponse.Linea l = r.lineas().get(0);
        assertThat(l.producto()).isEqualTo("Taladro Bosch 500W");
        assertThat(l.lote()).isNull();
        assertThat(l.estadoProducto()).isEqualTo(EstadoProductoDevuelto.APTO);
        assertThat(l.valor()).isEqualByComparingTo("773500");

        DevolucionResponse sinObs = servicio.registrarCliente(new DevolucionClienteRequest(1L, "x",
                TratamientoDevolucion.NINGUNO, "   ", null,
                List.of(new DevolucionClienteRequest.Linea(20L, BigDecimal.ONE, EstadoProductoDevuelto.APTO))), actor);
        assertThat(sinObs.observaciones()).isNull();
    }

    @Test @DisplayName("UT-DEV-21 la respuesta de proveedor muestra compra y proveedor; documentos de origen inexistentes no fallan")
    void respuestaProveedor() {
        compraConLineas();
        when(terceros.findById(9L)).thenReturn(Optional.of(Tercero.builder().id(9L).razonSocial("Distribuidora").build()));

        DevolucionResponse r = devolverAlProveedor(30L, "1", EstadoInventario.DISPONIBLE, null);
        assertThat(r.tipo()).isEqualTo(TipoDevolucion.PROVEEDOR);
        assertThat(r.compraNumero()).isEqualTo("OC-000005");
        assertThat(r.ventaNumero()).isNull();
        assertThat(r.tercero()).isEqualTo("Distribuidora");
        assertThat(r.lineas().get(0).origen()).isEqualTo(EstadoInventario.DISPONIBLE);
        assertThat(r.lineas().get(0).idLineaCompra()).isEqualTo(30L);

        DevolucionResponse huerfana = servicio.aDto(DocumentoDevolucion.builder().id(900L).numero("DEV-000900")
                .idVenta(999L).idCompra(999L).idUsuario(1).build());
        assertThat(huerfana.ventaNumero()).isNull();
        assertThat(huerfana.compraNumero()).isNull();
        assertThat(huerfana.tercero()).isNull();
    }

    @Test @DisplayName("UT-DEV-22 obtener, devoluciones de una venta, devuelto por línea y listado con filtros")
    @SuppressWarnings("unchecked")
    void consultasYFiltros() {
        devolver("1", EstadoProductoDevuelto.APTO);
        ArgumentCaptor<DocumentoDevolucion> cap = ArgumentCaptor.forClass(DocumentoDevolucion.class);
        verify(documentos, atLeastOnce()).save(cap.capture());
        DocumentoDevolucion doc = cap.getValue();
        when(documentos.findById(doc.getId())).thenReturn(Optional.of(doc));
        when(documentos.findByIdVentaOrderByIdAsc(1L)).thenReturn(List.of(doc));

        DevolucionResponse o = servicio.obtener(doc.getId());
        assertThat(o).isNotNull();
        assertThat(o.numero()).isEqualTo("DEV-000001");
        assertThat(o.total()).isEqualByComparingTo("773500");

        assertThat(servicio.deVenta(1L)).extracting(DevolucionResponse::numero).containsExactly("DEV-000001");

        Map<Long, BigDecimal> devuelto = servicio.devueltoPorLinea(venta);
        assertThat(devuelto).containsKey(20L);
        assertThat(devuelto.get(20L)).isEqualByComparingTo("1");

        when(documentos.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(a -> new PageImpl<>(List.of(doc), a.getArgument(1), 1));
        LocalDate desde = LocalDate.of(2026, 10, 1);
        LocalDate hasta = LocalDate.of(2026, 10, 31);
        PaginaResponse<DevolucionResponse> p = servicio.listar(
                new DevolucionService.Filtros(TipoDevolucion.CLIENTE, "  DEV-1%_ ", 1L, 5L, desde, hasta), -1, 500);
        assertThat(p).isNotNull();
        assertThat(p.pagina()).isZero();
        assertThat(p.tamano()).isEqualTo(100);
        assertThat(p.contenido()).extracting(DevolucionResponse::numero).containsExactly("DEV-000001");

        servicio.listar(new DevolucionService.Filtros(null, "   ", null, null, null, null), 0, 10);
        ArgumentCaptor<Specification<DocumentoDevolucion>> spec = ArgumentCaptor.forClass(Specification.class);
        verify(documentos, org.mockito.Mockito.times(2)).findAll(spec.capture(), any(Pageable.class));

        ZoneId zona = ParametrosNegocio.porDefecto().getZonaHoraria();
        Root<DocumentoDevolucion> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Object> pTipo = mock(Path.class);
        Path<String> pNumero = mock(Path.class);
        Path<Object> pVenta = mock(Path.class);
        Path<Object> pCompra = mock(Path.class);
        Path<LocalDateTime> pFecha = mock(Path.class);
        Expression<String> minusculas = mock(Expression.class);
        Predicate porTipo = mock(Predicate.class);
        Predicate porNumero = mock(Predicate.class);
        Predicate porVenta = mock(Predicate.class);
        Predicate porCompra = mock(Predicate.class);
        Predicate porDesde = mock(Predicate.class);
        Predicate porHasta = mock(Predicate.class);
        Predicate todos = mock(Predicate.class);
        Predicate ninguno = mock(Predicate.class);
        doReturn(pTipo).when(root).get("tipo");
        doReturn(pNumero).when(root).get("numero");
        doReturn(pVenta).when(root).get("idVenta");
        doReturn(pCompra).when(root).get("idCompra");
        doReturn(pFecha).when(root).get("fecha");
        when(cb.equal(pTipo, TipoDevolucion.CLIENTE)).thenReturn(porTipo);
        when(cb.lower(pNumero)).thenReturn(minusculas);
        when(cb.like(minusculas, "%dev-1%")).thenReturn(porNumero);   // sin espacios, en minúsculas y sin comodines
        when(cb.equal(pVenta, 1L)).thenReturn(porVenta);
        when(cb.equal(pCompra, 5L)).thenReturn(porCompra);
        when(cb.greaterThanOrEqualTo(pFecha, Fechas.inicioUtc(desde, zona))).thenReturn(porDesde);
        when(cb.lessThan(pFecha, Fechas.finExclusivoUtc(hasta, zona))).thenReturn(porHasta);
        when(cb.and(porTipo, porNumero, porVenta, porCompra, porDesde, porHasta)).thenReturn(todos);
        when(cb.and()).thenReturn(ninguno);

        assertThat(spec.getAllValues().get(0).toPredicate(root, query, cb)).isSameAs(todos);
        assertThat(spec.getAllValues().get(1).toPredicate(root, query, cb)).isSameAs(ninguno);
    }
}