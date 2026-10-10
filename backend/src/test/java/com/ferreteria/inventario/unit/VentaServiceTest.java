/*
 * Nombre: VentaServiceTest.java
 * Descripción: Pruebas unitarias (JUnit 5 + Mockito) del caso de uso de venta: saldo, precio, conversiones, cliente y auditoría.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
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

import com.ferreteria.inventario.domain.enums.TipoMovimiento;
import com.ferreteria.inventario.domain.model.*;
import com.ferreteria.inventario.dto.VentaRequest;
import com.ferreteria.inventario.dto.VentaResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.*;
import com.ferreteria.inventario.service.AuditoriaService;
import com.ferreteria.inventario.service.FacturacionPort;
import com.ferreteria.inventario.service.VentaService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Casos: UT-VEN-01 … UT-VEN-09 (ver docs/CASOS_DE_PRUEBA.md). */
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
    @Mock AuditoriaService auditoria;
    @Mock FacturacionPort facturacion;

    VentaService servicio;
    Producto taladro;
    Inventario inv;

    @BeforeEach
    void preparar() {
        Clock reloj = Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneOffset.UTC);
        servicio = new VentaService(ventas, terceros, productos, inventarios, conversiones, precios, facturas,
                usuarios, auditoria, facturacion, reloj);
        Tercero cliente = Tercero.builder().id(1L).cliente(true).numeroIdentificacion("1002003000").build();
        taladro = Producto.builder().id(10L).nombre("Taladro").unidadBase("unidad").build();
        inv = Inventario.builder().idProducto(10L).disponible(new BigDecimal("18")).reservado(BigDecimal.ZERO).danado(BigDecimal.ZERO).build();
        Usuario u = Usuario.builder().id(7).correo("laura.ventas@ferreteria.com").build();

        when(terceros.findById(1L)).thenReturn(Optional.of(cliente));
        when(usuarios.findByCorreoIgnoreCase("laura.ventas@ferreteria.com")).thenReturn(Optional.of(u));
        when(productos.findById(10L)).thenReturn(Optional.of(taladro));
        when(precios.findFirstByIdProductoAndPrecioVigenteIsNotNullOrderByIdDesc(10L))
                .thenReturn(Optional.of(Precio.builder().precioVigente(new BigDecimal("650000")).build()));
        when(inventarios.findForUpdate(10L)).thenReturn(Optional.of(inv));
        when(ventas.siguienteNumeroVenta()).thenReturn(1000L);
        when(ventas.save(any(Venta.class))).thenAnswer(a -> { Venta v = a.getArgument(0); v.setId(1L); return v; });
        when(facturas.save(any(Factura.class))).thenAnswer(a -> { Factura f = a.getArgument(0); f.setId(5L); return f; });
        when(facturacion.generarNumeracionFiscal(any())).thenReturn("DIAN-SETP-001000");
    }

    private VentaRequest peticion(String cantidad, String unidad) {
        return new VentaRequest(1L, List.of(new VentaRequest.Linea(10L, new BigDecimal(cantidad), unidad)), null, null);
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
        verify(auditoria, never()).registrar(any(), anyLong(), any(), any(), any(), any(), any(), any(), any());
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
                eq("VTA-001000"), eq(new BigDecimal("18")), eq(new BigDecimal("16")), any());
    }

    @Test @DisplayName("UT-VEN-06 sin precio vigente aprobado rechaza con 422 (RN-07)")
    void sinPrecio() {
        when(precios.findFirstByIdProductoAndPrecioVigenteIsNotNullOrderByIdDesc(10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.registrar(peticion("1", null), "laura.ventas@ferreteria.com", "x"))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("PRECIO_NO_VIGENTE"));
    }

    @Test @DisplayName("UT-VEN-07 conversión de 3 cajas x 100 = 300 unidades (RF-02)")
    void conversionCajas() {
        taladro.setUnidadBase("unidad");
        inv.setDisponible(new BigDecimal("300"));
        when(conversiones.findByIdProductoAndUnidadPresentacionIgnoreCase(10L, "caja"))
                .thenReturn(Optional.of(ConversionUnidad.builder().unidadPresentacion("caja")
                        .factorAUnidadBase(new BigDecimal("100")).precisionDecimales(0).build()));
        servicio.registrar(peticion("3", "caja"), "laura.ventas@ferreteria.com", "x");
        assertThat(inv.getDisponible()).isEqualByComparingTo("0");
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
}
