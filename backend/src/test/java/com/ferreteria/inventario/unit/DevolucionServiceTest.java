/*
 * Nombre: DevolucionServiceTest.java
 * Descripción: Pruebas unitarias de devoluciones (RF-13): parciales y múltiples, rechazo por exceso, destino según estado del producto, importe proporcional y devolución a proveedor.
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
import static org.mockito.Mockito.when;

import com.ferreteria.inventario.config.ParametrosNegocio;
import com.ferreteria.inventario.domain.enums.*;
import com.ferreteria.inventario.domain.model.*;
import com.ferreteria.inventario.dto.DevolucionClienteRequest;
import com.ferreteria.inventario.dto.DevolucionProveedorRequest;
import com.ferreteria.inventario.dto.DevolucionResponse;
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

/** Casos UT-DEV-01 … UT-DEV-07. */
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
}
