/*
 * Nombre: VentaConsultaServiceTest.java
 * Descripción: Pruebas unitarias de la consulta de ventas (RF-18, RF-19): listado con cliente, usuario, factura y devoluciones; detalle completo con datos persistidos, movimientos del documento, motivos por los que el comprobante no es exportable, leyenda interna o fiscal, datos de la empresa y filtros.
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ferreteria.inventario.config.ParametrosNegocio;
import com.ferreteria.inventario.domain.enums.EstadoDocumento;
import com.ferreteria.inventario.domain.enums.TipoMovimiento;
import com.ferreteria.inventario.domain.model.Factura;
import com.ferreteria.inventario.domain.model.LineaVenta;
import com.ferreteria.inventario.domain.model.Producto;
import com.ferreteria.inventario.domain.model.Tercero;
import com.ferreteria.inventario.domain.model.Usuario;
import com.ferreteria.inventario.domain.model.Venta;
import com.ferreteria.inventario.domain.model.DocumentoDevolucion;
import com.ferreteria.inventario.dto.EmpresaResponse;
import com.ferreteria.inventario.dto.MovimientoResponse;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.dto.VentaDetalleResponse;
import com.ferreteria.inventario.dto.VentaResumenResponse;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import org.springframework.data.jpa.domain.Specification;

/** Casos UT-VCS-01 … UT-VCS-08. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VentaConsultaServiceTest {

    @Mock VentaRepository ventas;
    @Mock FacturaRepository facturas;
    @Mock TerceroRepository terceros;
    @Mock UsuarioRepository usuarios;
    @Mock ProductoRepository productos;
    @Mock DocumentoDevolucionRepository documentosDevolucion;
    @Mock DevolucionService devoluciones;
    @Mock EventoAuditoriaService eventos;
    @Mock AuditoriaService auditoria;
    @Mock FacturacionPort facturacion;

    static final LocalDateTime FECHA = LocalDateTime.of(2026, 10, 16, 15, 0);

    /** Empresa con NIT y ciudad; dirección en blanco, teléfono vacío y correo sin definir. */
    final ParametrosNegocio parametros = new ParametrosNegocio(new BigDecimal("10"), 30, BigDecimal.ONE, "recargo",
            "Ferretería El Tornillo", "900123456-7", "   ", "Neiva", "", null, "America/Bogota");

    VentaConsultaService servicio;
    Venta venta;

    @BeforeEach
    void preparar() {
        servicio = new VentaConsultaService(ventas, facturas, terceros, usuarios, productos, documentosDevolucion,
                devoluciones, eventos, auditoria, facturacion, parametros);

        venta = Venta.builder().id(1L).numeroInterno("VTA-000001").idCliente(4L).idUsuario(3).fecha(FECHA)
                .estado(EstadoDocumento.ACTIVO).subtotal(new BigDecimal("1300000")).totalDescuentos(BigDecimal.ZERO)
                .totalImpuestos(new BigDecimal("247000")).total(new BigDecimal("1547000")).build();
        venta.getLineas().add(LineaVenta.builder().id(20L).venta(venta).idProducto(10L).cantidad(new BigDecimal("2"))
                .precioAplicado(new BigDecimal("650000")).subtotal(new BigDecimal("1300000")).descuento(BigDecimal.ZERO)
                .impuesto(new BigDecimal("247000")).total(new BigDecimal("1547000")).build());
        when(ventas.findById(1L)).thenReturn(Optional.of(venta));

        when(facturas.findByIdVenta(1L)).thenReturn(Optional.of(Factura.builder().id(30L).idVenta(1L)
                .numeracionDian("SETP-990001").fechaEmision(FECHA).build()));
        when(terceros.findById(4L)).thenReturn(Optional.of(Tercero.builder().id(4L).cliente(true)
                .razonSocial("Constructora XYZ").numeroIdentificacion("800").build()));
        when(usuarios.findById(3)).thenReturn(Optional.of(Usuario.builder().id(3).nombre("Laura Ventas")
                .correo("laura.ventas@ferreteria.com").build()));
        when(productos.findById(10L)).thenReturn(Optional.of(Producto.builder().id(10L).nombre("Taladro").marca("Bosch")
                .presentacion("500W").codigoBarras("7701234500015").unidadBase("unidad").build()));
        when(devoluciones.devueltoPorLinea(any())).thenReturn(Map.of(20L, BigDecimal.ONE));
        when(devoluciones.deVenta(anyLong())).thenReturn(List.of());
        when(eventos.deEntidad(any(), any())).thenReturn(List.of());
        when(auditoria.consultarFiltrado(any(), any(), eq(0), eq(100))).thenReturn(new PaginaResponse<>(List.of(
                movimiento(100L, "VTA-000001"), movimiento(101L, "VTA-0000010")), 0, 100, 2, 1));
        when(facturacion.esValidaFiscalmente()).thenReturn(true);
    }

    private static MovimientoResponse movimiento(Long id, String documento) {
        return new MovimientoResponse(id, TipoMovimiento.VENTA, 10L, new BigDecimal("-2"), FECHA, 3, "10.0.0.1", documento,
                new BigDecimal("10"), new BigDecimal("8"), "Venta", null, null, null);
    }

    private static String codigo(Throwable e) {
        return ((ApiException) e).getCodigo();
    }

    @Test @DisplayName("UT-VCS-01 detalle completo: cliente, usuario, productos, devuelto por línea, movimientos del documento y leyenda fiscal")
    void detalleCompleto() {
        VentaDetalleResponse d = servicio.detalle(1L);

        assertThat(d).isNotNull();
        assertThat(d.venta().numeroInterno()).isEqualTo("VTA-000001");
        assertThat(d.venta().cliente()).isEqualTo("Constructora XYZ");
        assertThat(d.venta().usuario()).isEqualTo("laura.ventas@ferreteria.com");
        assertThat(d.venta().factura().numeracionDian()).isEqualTo("SETP-990001");
        assertThat(d.venta().factura().validaFiscalmente()).isTrue();
        assertThat(d.venta().lineas().get(0).producto()).isEqualTo("Taladro Bosch 500W");
        assertThat(d.venta().lineas().get(0).codigoBarras()).isEqualTo("7701234500015");
        assertThat(d.venta().lineas().get(0).unidadBase()).isEqualTo("unidad");
        assertThat(d.venta().lineas().get(0).cantidadDevuelta()).isEqualByComparingTo("1");

        assertThat(d.cliente()).isNotNull();
        assertThat(d.cliente().id()).isEqualTo(4L);
        assertThat(d.usuarioNombre()).isEqualTo("Laura Ventas");
        assertThat(d.usuarioCorreo()).isEqualTo("laura.ventas@ferreteria.com");
        assertThat(d.empresa().nombre()).isEqualTo("Ferretería El Tornillo");

        // solo los movimientos cuyo documento es exactamente el número de la venta
        assertThat(d.movimientos()).extracting(MovimientoResponse::id).containsExactly(100L);
        ArgumentCaptor<AuditoriaService.Filtros> filtro = ArgumentCaptor.forClass(AuditoriaService.Filtros.class);
        verify(auditoria).consultarFiltrado(filtro.capture(), eq(parametros.getZonaHoraria()), eq(0), eq(100));
        assertThat(filtro.getValue().documento()).isEqualTo("VTA-000001");
        verify(eventos).deEntidad("VENTA", 1L);
        verify(devoluciones).deVenta(1L);

        assertThat(d.exportable()).isTrue();
        assertThat(d.motivosNoExportable()).isEmpty();
        assertThat(d.leyenda()).isEqualTo(VentaConsultaService.LEYENDA_FISCAL);
    }

    @Test @DisplayName("UT-VCS-02 detalle no exportable: sin líneas, sin factura, sin cliente y anulada; leyenda de comprobante interno")
    void detalleNoExportable() {
        Venta v = Venta.builder().id(2L).numeroInterno("VTA-000002").idCliente(404L).fecha(FECHA)
                .estado(EstadoDocumento.ANULADO).build();
        when(ventas.findById(2L)).thenReturn(Optional.of(v));
        when(facturas.findByIdVenta(2L)).thenReturn(Optional.empty());
        when(facturacion.esValidaFiscalmente()).thenReturn(false);

        VentaDetalleResponse d = servicio.detalle(2L);

        assertThat(d.exportable()).isFalse();
        assertThat(d.motivosNoExportable()).containsExactly(
                "La venta no tiene líneas.",
                "La venta no tiene factura emitida.",
                "No se encontraron los datos del cliente.",
                "La venta está anulada.");
        assertThat(d.leyenda()).isEqualTo(VentaConsultaService.LEYENDA_INTERNA);
        assertThat(d.cliente()).isNull();
        assertThat(d.usuarioNombre()).isNull();
        assertThat(d.usuarioCorreo()).isNull();
        assertThat(d.venta().cliente()).isNull();
        assertThat(d.venta().usuario()).isNull();
        assertThat(d.venta().factura()).isNull();
        assertThat(d.movimientos()).isEmpty();
        verify(usuarios, never()).findById(any());     // la venta no tiene usuario
    }

    @Test @DisplayName("UT-VCS-03 factura sin numeración no es exportable; producto o usuario inexistentes no rompen el detalle")
    void detalleDatosIncompletos() {
        when(facturas.findByIdVenta(1L)).thenReturn(Optional.of(Factura.builder().id(30L).idVenta(1L).build()));
        when(productos.findById(10L)).thenReturn(Optional.empty());
        when(usuarios.findById(3)).thenReturn(Optional.empty());

        VentaDetalleResponse d = servicio.detalle(1L);

        assertThat(d.exportable()).isFalse();
        assertThat(d.motivosNoExportable()).containsExactly("La venta no tiene factura emitida.");
        assertThat(d.venta().lineas().get(0).producto()).isNull();
        assertThat(d.usuarioNombre()).isNull();
        assertThat(d.usuarioCorreo()).isNull();
        assertThat(d.venta().usuario()).isNull();
    }

    @Test @DisplayName("UT-VCS-04 detalle de una venta inexistente → 404")
    void detalleInexistente() {
        assertThatThrownBy(() -> servicio.detalle(404L)).satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
    }

    @Test @DisplayName("UT-VCS-05 datos de la empresa: los valores vacíos o en blanco se devuelven como nulos")
    void empresa() {
        EmpresaResponse e = servicio.empresa();
        assertThat(e).isEqualTo(new EmpresaResponse("Ferretería El Tornillo", "900123456-7", null, "Neiva", null, null));
    }

    @Test @DisplayName("UT-VCS-06 listar: cliente, usuario, numeración, total, moneda y si tiene devoluciones; consultas en caché")
    @SuppressWarnings("unchecked")
    void listar() {
        Venta sinDatos = Venta.builder().id(2L).numeroInterno("VTA-000002").idCliente(404L).fecha(FECHA)
                .estado(EstadoDocumento.ACTIVO).total(new BigDecimal("1000")).build();
        Venta mismoCliente = Venta.builder().id(3L).numeroInterno("VTA-000003").idCliente(4L).idUsuario(3).fecha(FECHA)
                .estado(EstadoDocumento.ANULADO).total(new BigDecimal("500")).build();
        Venta usuarioBorrado = Venta.builder().id(4L).numeroInterno("VTA-000004").idCliente(4L).idUsuario(7).fecha(FECHA)
                .estado(EstadoDocumento.ACTIVO).total(BigDecimal.TEN).build();
        when(ventas.findAll(any(Specification.class), any(Pageable.class))).thenAnswer(a ->
                new PageImpl<>(List.of(venta, sinDatos, mismoCliente, usuarioBorrado), a.getArgument(1), 4));
        when(facturas.findByIdVenta(2L)).thenReturn(Optional.empty());
        when(documentosDevolucion.findByIdVentaOrderByIdAsc(anyLong())).thenReturn(List.of());
        when(documentosDevolucion.findByIdVentaOrderByIdAsc(1L)).thenReturn(List.of(DocumentoDevolucion.builder().id(9L).build()));

        PaginaResponse<VentaResumenResponse> p = servicio.listar(
                new VentaConsultaService.Filtros(null, null, null, null, null), -1, 500);

        assertThat(p).isNotNull();
        assertThat(p.pagina()).isZero();
        assertThat(p.tamano()).isEqualTo(100);
        VentaResumenResponse a = p.contenido().get(0);
        assertThat(a.numeroInterno()).isEqualTo("VTA-000001");
        assertThat(a.numeracionDian()).isEqualTo("SETP-990001");
        assertThat(a.cliente()).isEqualTo("Constructora XYZ");
        assertThat(a.usuario()).isEqualTo("laura.ventas@ferreteria.com");
        assertThat(a.total()).isEqualByComparingTo("1547000");
        assertThat(a.tieneDevoluciones()).isTrue();
        assertThat(a.moneda()).isEqualTo("COP");
        assertThat(a.totalMoneda()).isEqualByComparingTo("1547000");

        VentaResumenResponse b = p.contenido().get(1);
        assertThat(b.numeracionDian()).isNull();
        assertThat(b.cliente()).isEmpty();          // cliente inexistente
        assertThat(b.usuario()).isNull();           // venta sin usuario
        assertThat(b.tieneDevoluciones()).isFalse();

        assertThat(p.contenido().get(2).estado()).isEqualTo(EstadoDocumento.ANULADO);
        assertThat(p.contenido().get(2).cliente()).isEqualTo("Constructora XYZ");
        assertThat(p.contenido().get(3).usuario()).isEmpty();    // usuario inexistente

        verify(terceros, times(1)).findById(4L);     // cliente consultado una sola vez
        verify(usuarios, times(1)).findById(3);      // correo consultado una sola vez
    }

    @Test @DisplayName("UT-VCS-07 listar acota el tamaño mínimo a 1")
    @SuppressWarnings("unchecked")
    void listarTamanoMinimo() {
        when(ventas.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(a -> new PageImpl<>(List.of(), a.getArgument(1), 0));
        PaginaResponse<VentaResumenResponse> p = servicio.listar(
                new VentaConsultaService.Filtros(null, null, null, null, null), 2, 0);
        assertThat(p.pagina()).isEqualTo(2);
        assertThat(p.tamano()).isEqualTo(1);
        assertThat(p.contenido()).isEmpty();
    }

    @Test @DisplayName("UT-VCS-08 filtros: número (recortado, en minúsculas y sin comodines), cliente, estado y rango de fechas")
    @SuppressWarnings("unchecked")
    void filtros() {
        when(ventas.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(a -> new PageImpl<>(List.of(), a.getArgument(1), 0));
        LocalDate desde = LocalDate.of(2026, 10, 1);
        LocalDate hasta = LocalDate.of(2026, 10, 31);
        servicio.listar(new VentaConsultaService.Filtros("  VTA-1%_ ", 4L, desde, hasta, EstadoDocumento.ACTIVO), 0, 10);
        servicio.listar(new VentaConsultaService.Filtros("   ", null, null, null, null), 0, 10);
        ArgumentCaptor<Specification<Venta>> spec = ArgumentCaptor.forClass(Specification.class);
        verify(ventas, times(2)).findAll(spec.capture(), any(Pageable.class));

        ZoneId zona = parametros.getZonaHoraria();
        Root<Venta> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<String> pNumero = mock(Path.class);
        Path<Object> pCliente = mock(Path.class);
        Path<Object> pEstado = mock(Path.class);
        Path<LocalDateTime> pFecha = mock(Path.class);
        Expression<String> minusculas = mock(Expression.class);
        Predicate porNumero = mock(Predicate.class);
        Predicate porCliente = mock(Predicate.class);
        Predicate porEstado = mock(Predicate.class);
        Predicate porDesde = mock(Predicate.class);
        Predicate porHasta = mock(Predicate.class);
        Predicate todos = mock(Predicate.class);
        Predicate ninguno = mock(Predicate.class);
        doReturn(pNumero).when(root).get("numeroInterno");
        doReturn(pCliente).when(root).get("idCliente");
        doReturn(pEstado).when(root).get("estado");
        doReturn(pFecha).when(root).get("fecha");
        when(cb.lower(pNumero)).thenReturn(minusculas);
        when(cb.like(minusculas, "%vta-1%")).thenReturn(porNumero);
        when(cb.equal(pCliente, 4L)).thenReturn(porCliente);
        when(cb.equal(pEstado, EstadoDocumento.ACTIVO)).thenReturn(porEstado);
        when(cb.greaterThanOrEqualTo(pFecha, Fechas.inicioUtc(desde, zona))).thenReturn(porDesde);
        when(cb.lessThan(pFecha, Fechas.finExclusivoUtc(hasta, zona))).thenReturn(porHasta);
        when(cb.and(porNumero, porCliente, porEstado, porDesde, porHasta)).thenReturn(todos);
        when(cb.and()).thenReturn(ninguno);

        assertThat(spec.getAllValues().get(0).toPredicate(root, query, cb)).isSameAs(todos);
        assertThat(spec.getAllValues().get(1).toPredicate(root, query, cb)).isSameAs(ninguno);
    }
}