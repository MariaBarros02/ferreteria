/*
 * Nombre: VentaServiceTest.java
 * Descripción: Pruebas unitarias (JUnit 5 + Mockito) del caso de uso de venta: saldo, precio, conversiones, cliente, auditoría, totales con IVA y descuento, lotes FEFO, reservas e idempotencia.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
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
import com.ferreteria.inventario.domain.enums.EstadoInventario;
import com.ferreteria.inventario.domain.enums.EstadoReserva;
import com.ferreteria.inventario.domain.enums.TipoMovimiento;
import com.ferreteria.inventario.domain.enums.TipoRastreo;
import com.ferreteria.inventario.domain.model.*;
import com.ferreteria.inventario.dto.VentaRequest;
import com.ferreteria.inventario.dto.VentaResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.*;
import com.ferreteria.inventario.service.AuditoriaService;
import com.ferreteria.inventario.service.EventoAuditoriaService;
import com.ferreteria.inventario.service.FacturacionPort;
import com.ferreteria.inventario.service.KardexService;
import com.ferreteria.inventario.service.LoteService;
import com.ferreteria.inventario.service.UnidadService;
import com.ferreteria.inventario.service.VentaService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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

/** Casos: UT-VEN-01 … UT-VEN-18 (ver docs/CASOS_DE_PRUEBA.md). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VentaServiceTest {

    @Mock VentaRepository ventas;
    @Mock TerceroRepository terceros;
    @Mock ProductoRepository productos;
    @Mock InventarioRepository inventarios;
    @Mock ConversionUnidadRepository conversiones;
    @Mock PrecioRepository precios;
    @Mock FacturaRepository facturas;
    @Mock UsuarioRepository usuarios;
    @Mock ReservaRepository reservas;
    @Mock LineaVentaLoteRepository lineaVentaLotes;
    @Mock LoteRepository lotes;
    @Mock CierrePeriodoRepository cierres;
    @Mock AuditoriaService auditoria;
    @Mock EventoAuditoriaService eventos;
    @Mock FacturacionPort facturacion;

    VentaService servicio;
    Producto taladro;
    Inventario inv;
    Tercero cliente;

    @BeforeEach
    void preparar() {
        Clock reloj = Clock.fixed(Instant.parse("2026-10-07T15:00:00Z"), ZoneOffset.UTC);
        ParametrosNegocio parametros = ParametrosNegocio.porDefecto();
        KardexService kardex = new KardexService(inventarios, productos, auditoria, cierres, parametros, reloj);
        servicio = new VentaService(ventas, terceros, productos, precios, facturas, usuarios, reservas, lineaVentaLotes,
                facturacion, new UnidadService(conversiones), kardex, new LoteService(lotes), eventos, parametros);
        cliente = Tercero.builder().id(1L).cliente(true).numeroIdentificacion("1002003000").primerNombre("Juan")
                .primerApellido("Pérez").build();
        taladro = Producto.builder().id(10L).nombre("Taladro").marca("Bosch").presentacion("500W").unidadBase("unidad")
                .tipoRastreo(TipoRastreo.NINGUNO).build();
        inv = Inventario.builder().idProducto(10L).disponible(new BigDecimal("18")).reservado(BigDecimal.ZERO).danado(BigDecimal.ZERO).build();
        Usuario u = Usuario.builder().id(7).correo("laura.ventas@ferreteria.com").build();

        when(terceros.findById(1L)).thenReturn(Optional.of(cliente));
        when(usuarios.findByCorreoIgnoreCase("laura.ventas@ferreteria.com")).thenReturn(Optional.of(u));
        when(productos.findById(10L)).thenReturn(Optional.of(taladro));
        when(precios.vigentes(eq(10L), any())).thenReturn(List.of(Precio.builder().precioVigente(new BigDecimal("650000")).build()));
        when(inventarios.findForUpdate(10L)).thenReturn(Optional.of(inv));
        when(ventas.siguienteNumeroVenta()).thenReturn(1000L);
        when(ventas.findByIdOperacion(any())).thenReturn(Optional.empty());
        when(ventas.save(any(Venta.class))).thenAnswer(a -> { Venta v = a.getArgument(0); v.setId(1L); return v; });
        when(facturas.save(any(Factura.class))).thenAnswer(a -> { Factura f = a.getArgument(0); f.setId(5L); return f; });
        when(facturacion.generarNumeracionFiscal(any())).thenReturn("DIAN-SETP-001000");
        when(lotes.sumaDisponible(anyLong())).thenReturn(BigDecimal.ZERO);
    }

    private VentaRequest peticion(String cantidad, String unidad) {
        return new VentaRequest(1L, List.of(new VentaRequest.Linea(10L, new BigDecimal(cantidad), unidad)), null, null);
    }

    private VentaRequest peticionConDescuento(String cantidad, String descuento) {
        return new VentaRequest(1L, List.of(new VentaRequest.Linea(10L, new BigDecimal(cantidad), null,
                new BigDecimal(descuento), null)), null, null, null, null);
    }

    @Test @DisplayName("UT-VEN-01 saldo suficiente descuenta una sola vez y emite factura")
    void ventaDescuentaInventario() {
        VentaResponse r = servicio.registrar(peticion("2", null), "laura.ventas@ferreteria.com", "10.0.0.5");
        assertThat(inv.getDisponible()).isEqualByComparingTo("16");
        assertThat(r.total()).isEqualByComparingTo("1300000");
        assertThat(r.factura().numeracionDian()).isEqualTo("DIAN-SETP-001000");
        verify(inventarios).save(inv);
    }

    @Test @DisplayName("UT-VEN-02 número interno y numeración fiscal son distintos (RN-14)")
    void numeracionDistinta() {
        VentaResponse r = servicio.registrar(peticion("1", null), "laura.ventas@ferreteria.com", "10.0.0.5");
        assertThat(r.numeroInterno()).isEqualTo("VTA-001000").isNotEqualTo(r.factura().numeracionDian());
    }

    @Test @DisplayName("UT-VEN-03 saldo insuficiente rechaza con 409 y no modifica inventario ni factura")
    void saldoInsuficiente() {
        assertThatThrownBy(() -> servicio.registrar(peticion("19", null), "laura.ventas@ferreteria.com", "x"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("SALDO_INSUFICIENTE"));
        assertThat(inv.getDisponible()).isEqualByComparingTo("18");
        verify(facturas, never()).save(any());
        verify(auditoria, never()).registrar(any(), anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test @DisplayName("UT-VEN-04 vender exactamente el saldo es válido y deja 0 (nunca negativo)")
    void ventaTotalDejaCero() {
        servicio.registrar(peticion("18", null), "laura.ventas@ferreteria.com", "x");
        assertThat(inv.getDisponible()).isEqualByComparingTo("0");
    }

    @Test @DisplayName("UT-VEN-05 auditoría registra valor anterior/nuevo, cantidad negativa y documento")
    void auditoriaCompleta() {
        servicio.registrar(peticion("2", null), "laura.ventas@ferreteria.com", "10.0.0.5");
        verify(auditoria).registrar(eq(TipoMovimiento.VENTA), eq(10L), eq(new BigDecimal("-2")), eq(7), eq("10.0.0.5"),
                eq("VTA-001000"), eq(new BigDecimal("18")), eq(new BigDecimal("16")), any(),
                eq(EstadoInventario.DISPONIBLE), any(), any());
    }

    @Test @DisplayName("UT-VEN-06 sin precio vigente aprobado rechaza con 422 (RN-07)")
    void sinPrecio() {
        when(precios.vigentes(eq(10L), any())).thenReturn(List.of());
        assertThatThrownBy(() -> servicio.registrar(peticion("1", null), "laura.ventas@ferreteria.com", "x"))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("PRECIO_NO_VIGENTE"));
    }

    @Test @DisplayName("UT-VEN-07 conversión de 3 cajas x 100 = 300 unidades (RF-02)")
    void conversionCajas() {
        inv.setDisponible(new BigDecimal("300"));
        when(conversiones.findByIdProductoAndUnidadPresentacionIgnoreCase(10L, "caja"))
                .thenReturn(Optional.of(ConversionUnidad.builder().unidadPresentacion("caja")
                        .factorAUnidadBase(new BigDecimal("100")).precisionDecimales(0).build()));
        VentaResponse r = servicio.registrar(peticion("3", "caja"), "laura.ventas@ferreteria.com", "x");
        assertThat(inv.getDisponible()).isEqualByComparingTo("0");
        assertThat(r.lineas().get(0).unidad()).isEqualTo("caja");
        assertThat(r.lineas().get(0).cantidadPresentacion()).isEqualByComparingTo("3");
    }

    @Test @DisplayName("UT-VEN-08 conversión no configurada se rechaza sin tocar saldos (RF-02)")
    void conversionNoConfigurada() {
        when(conversiones.findByIdProductoAndUnidadPresentacionIgnoreCase(10L, "bulto")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.registrar(peticion("1", "bulto"), "laura.ventas@ferreteria.com", "x"))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("CONVERSION_NO_CONFIGURADA"));
        assertThat(inv.getDisponible()).isEqualByComparingTo("18");
    }

    @Test @DisplayName("UT-VEN-09 un tercero que no es cliente no puede comprar")
    void clienteInvalido() {
        when(terceros.findById(1L)).thenReturn(Optional.of(Tercero.builder().id(1L).cliente(false).proveedor(true)
                .numeroIdentificacion("900123456").build()));
        assertThatThrownBy(() -> servicio.registrar(peticion("1", null), "laura.ventas@ferreteria.com", "x"))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("CLIENTE_INVALIDO"));
    }

    @Test @DisplayName("UT-VEN-10 totales: descuento por línea y luego IVA del producto sobre la base")
    void totalesConIvaYDescuento() {
        taladro.setPorcentajeIva(new BigDecimal("19"));
        VentaResponse r = servicio.registrar(peticionConDescuento("2", "10"), "laura.ventas@ferreteria.com", "x");
        // 2 x 650.000 = 1.300.000; descuento 10 % = 130.000; base 1.170.000; IVA 19 % = 222.300; total 1.392.300
        assertThat(r.subtotal()).isEqualByComparingTo("1300000");
        assertThat(r.totalDescuentos()).isEqualByComparingTo("130000");
        assertThat(r.totalImpuestos()).isEqualByComparingTo("222300");
        assertThat(r.total()).isEqualByComparingTo("1392300");
        assertThat(r.factura().impuestos()).isEqualByComparingTo("222300");
        assertThat(r.factura().descuentos()).isEqualByComparingTo("130000");
    }

    @Test @DisplayName("UT-VEN-11 descuento mayor al máximo configurado se rechaza (422)")
    void descuentoExcedido() {
        assertThatThrownBy(() -> servicio.registrar(peticionConDescuento("1", "10.01"), "laura.ventas@ferreteria.com", "x"))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("DESCUENTO_NO_PERMITIDO"));
        assertThat(inv.getDisponible()).isEqualByComparingTo("18");
    }

    @Test @DisplayName("UT-VEN-12 el cliente no puede fijar impuestos/descuentos globales (importes calculados)")
    void importesManualesRechazados() {
        var req = new VentaRequest(1L, List.of(new VentaRequest.Linea(10L, BigDecimal.ONE, null)), new BigDecimal("5"), null);
        assertThatThrownBy(() -> servicio.registrar(req, "laura.ventas@ferreteria.com", "x"))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("IMPORTES_CALCULADOS"));
    }

    @Test @DisplayName("UT-VEN-13 un producto inactivo no se puede vender")
    void productoInactivo() {
        taladro.setActivo(false);
        assertThatThrownBy(() -> servicio.registrar(peticion("1", null), "laura.ventas@ferreteria.com", "x"))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("PRODUCTO_INACTIVO"));
    }

    @Test @DisplayName("UT-VEN-14 reintento con la misma clave de operación devuelve la venta existente sin descontar de nuevo")
    void idempotencia() {
        Venta previa = Venta.builder().id(99L).numeroInterno("VTA-000099").idCliente(1L)
                .estado(com.ferreteria.inventario.domain.enums.EstadoDocumento.ACTIVO).total(new BigDecimal("650000")).build();
        when(ventas.findByIdOperacion("op-123456789")).thenReturn(Optional.of(previa));
        var req = new VentaRequest(1L, List.of(new VentaRequest.Linea(10L, BigDecimal.ONE, null)), null, null,
                "op-123456789", null);
        VentaResponse r = servicio.registrar(req, "laura.ventas@ferreteria.com", "x");
        assertThat(r.id()).isEqualTo(99L);
        assertThat(inv.getDisponible()).isEqualByComparingTo("18");
        verify(facturas, never()).save(any());
    }

    @Test @DisplayName("UT-VEN-15 producto por lote: asigna FEFO (primero el que vence antes) y traza un movimiento por lote")
    void asignacionFefo() {
        taladro.setTipoRastreo(TipoRastreo.LOTE);
        Lote pronto = Lote.builder().id(1L).idProducto(10L).codigo("A").fechaVencimiento(LocalDate.of(2026, 11, 1))
                .disponible(new BigDecimal("3")).danado(BigDecimal.ZERO).build();
        Lote tarde = Lote.builder().id(2L).idProducto(10L).codigo("B").fechaVencimiento(LocalDate.of(2027, 1, 1))
                .disponible(new BigDecimal("10")).danado(BigDecimal.ZERO).build();
        when(lotes.sumaDisponible(10L)).thenReturn(new BigDecimal("13"));
        when(lotes.vendiblesParaActualizar(eq(10L), any())).thenReturn(List.of(pronto, tarde));
        when(lotes.save(any(Lote.class))).thenAnswer(a -> a.getArgument(0));
        servicio.registrar(peticion("5", null), "laura.ventas@ferreteria.com", "x");
        assertThat(pronto.getDisponible()).isEqualByComparingTo("0");
        assertThat(tarde.getDisponible()).isEqualByComparingTo("8");
        assertThat(inv.getDisponible()).isEqualByComparingTo("13");
        verify(auditoria).registrar(eq(TipoMovimiento.VENTA), eq(10L), eq(new BigDecimal("-3")), any(), any(), any(),
                any(), any(), any(), eq(EstadoInventario.DISPONIBLE), eq(1L), any());
        verify(auditoria).registrar(eq(TipoMovimiento.VENTA), eq(10L), eq(new BigDecimal("-2")), any(), any(), any(),
                any(), any(), any(), eq(EstadoInventario.DISPONIBLE), eq(2L), any());
    }

    @Test @DisplayName("UT-VEN-16 producto por lote: las unidades de lotes vencidos no se venden (409 STOCK_VENCIDO)")
    void loteVencidoNoSeVende() {
        taladro.setTipoRastreo(TipoRastreo.LOTE);
        inv.setDisponible(new BigDecimal("4"));
        when(lotes.sumaDisponible(10L)).thenReturn(new BigDecimal("4"));        // todo el saldo está en lotes
        when(lotes.vendiblesParaActualizar(eq(10L), any())).thenReturn(List.of()); // y todos vencieron
        assertThatThrownBy(() -> servicio.registrar(peticion("1", null), "laura.ventas@ferreteria.com", "x"))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("STOCK_VENCIDO"));
        assertThat(inv.getDisponible()).isEqualByComparingTo("4");
    }

    @Test @DisplayName("UT-VEN-17 garantía: la línea registra el fin de garantía según los meses del producto")
    void garantia() {
        taladro.setTieneGarantia(true);
        taladro.setMesesGarantia(12);
        VentaResponse r = servicio.registrar(peticion("1", null), "laura.ventas@ferreteria.com", "x");
        assertThat(r.lineas().get(0).garantiaHasta()).isEqualTo(LocalDate.of(2027, 10, 7));
    }

    @Test @DisplayName("UT-VEN-18 venta con reserva: descuenta del reservado y libera el sobrante a disponible (RN-03)")
    void ventaConReserva() {
        inv.setDisponible(new BigDecimal("10"));
        inv.setReservado(new BigDecimal("5"));
        Reserva r = Reserva.builder().id(3L).idProducto(10L).idCliente(1L).cantidad(new BigDecimal("5"))
                .fechaLimite(LocalDate.of(2026, 10, 30)).estado(EstadoReserva.ACTIVA).build();
        when(reservas.findForUpdate(3L)).thenReturn(Optional.of(r));
        var req = new VentaRequest(1L, List.of(new VentaRequest.Linea(10L, new BigDecimal("3"), null, null, 3L)),
                null, null, null, null);
        servicio.registrar(req, "laura.ventas@ferreteria.com", "x");
        assertThat(inv.getReservado()).isEqualByComparingTo("0");
        assertThat(inv.getDisponible()).isEqualByComparingTo("12");
        assertThat(r.getEstado()).isEqualTo(EstadoReserva.LIBERADA_VENTA);
        ArgumentCaptor<Venta> captor = ArgumentCaptor.forClass(Venta.class);
        verify(ventas, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(r.getIdVenta()).isEqualTo(captor.getValue().getId());
    }
}
