/*
 * Nombre: ProductoServiceTest.java
 * Descripción: Pruebas unitarias del servicio de productos: unicidad RN-01, normalización de campos, saldo inicial en cero, activación, consultas, selectores, catálogo sin conexión, eliminación, unidades y auditoría.
 * Fecha de creación: 2026-10-07
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ferreteria.inventario.config.ParametrosNegocio;
import com.ferreteria.inventario.domain.enums.TipoRastreo;
import com.ferreteria.inventario.domain.model.ConversionUnidad;
import com.ferreteria.inventario.domain.model.Inventario;
import com.ferreteria.inventario.domain.model.Precio;
import com.ferreteria.inventario.domain.model.Producto;
import com.ferreteria.inventario.dto.ConversionRequest;
import com.ferreteria.inventario.dto.ConversionResponse;
import com.ferreteria.inventario.dto.InventarioResponse;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.dto.ProductoRequest;
import com.ferreteria.inventario.dto.ProductoResponse;
import com.ferreteria.inventario.dto.ProductoSeleccionResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.ConversionUnidadRepository;
import com.ferreteria.inventario.repository.InventarioRepository;
import com.ferreteria.inventario.repository.LoteRepository;
import com.ferreteria.inventario.repository.PrecioRepository;
import com.ferreteria.inventario.repository.ProductoRepository;
import com.ferreteria.inventario.service.Actor;
import com.ferreteria.inventario.service.Auditables;
import com.ferreteria.inventario.service.ContextoSolicitud;
import com.ferreteria.inventario.service.EventoAuditoriaService;
import com.ferreteria.inventario.service.ProductoService;
import java.math.BigDecimal;
import java.time.Clock;
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
import org.springframework.data.domain.Sort;

/** Casos UT-PRO-01 … UT-PRO-20 (UT-PRO-08 en adelante: casos para matar mutantes de PIT). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductoServiceTest {

    @Mock ProductoRepository productos;
    @Mock InventarioRepository inventarios;
    @Mock ConversionUnidadRepository conversiones;
    @Mock PrecioRepository precios;
    @Mock LoteRepository lotes;
    @Mock EventoAuditoriaService eventos;
    @Mock ContextoSolicitud contexto;
    ProductoService servicio;

    final Actor actor = new Actor(1, "marta.inventario@ferreteria.com", "127.0.0.1");

    ProductoRequest req = new ProductoRequest(" Taladro ", "Bosch", "500W", "7701234500015", "unidad",
            TipoRastreo.SERIE, false, true);

    @BeforeEach
    void preparar() {
        servicio = new ProductoService(productos, inventarios, conversiones, precios, lotes, eventos, contexto,
                ParametrosNegocio.porDefecto(), Clock.systemUTC());
        when(contexto.actor()).thenReturn(actor);
        when(productos.save(any(Producto.class))).thenAnswer(a -> { Producto p = a.getArgument(0); p.setId(10L); return p; });
        when(productos.findByCodigoBarras(any())).thenReturn(Optional.empty());
        when(productos.findByNombreIgnoreCaseAndMarcaIgnoreCaseAndPresentacionIgnoreCase(any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test @DisplayName("UT-PRO-01 crear normaliza espacios y crea saldo inicial en cero")
    void crear() {
        ProductoResponse r = servicio.crear(req);
        assertThat(r.nombre()).isEqualTo("Taladro");
        ArgumentCaptor<Inventario> cap = ArgumentCaptor.forClass(Inventario.class);
        verify(inventarios).save(cap.capture());
        assertThat(cap.getValue().getDisponible()).isEqualByComparingTo("0");
        assertThat(cap.getValue().getIdProducto()).isEqualTo(10L);
    }

    @Test @DisplayName("UT-PRO-02 código de barras duplicado se rechaza (RN-01)")
    void codigoDuplicado() {
        when(productos.findByCodigoBarras("7701234500015")).thenReturn(Optional.of(Producto.builder().id(99L).build()));
        assertThatThrownBy(() -> servicio.crear(req))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("CODIGO_DUPLICADO"));
        verify(productos, never()).save(any());
    }

    @Test @DisplayName("UT-PRO-03 misma combinación producto+marca+presentación se rechaza (RN-01)")
    void referenciaDuplicada() {
        when(productos.findByNombreIgnoreCaseAndMarcaIgnoreCaseAndPresentacionIgnoreCase("Taladro", "Bosch", "500W"))
                .thenReturn(Optional.of(Producto.builder().id(99L).build()));
        assertThatThrownBy(() -> servicio.crear(req))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("REFERENCIA_DUPLICADA"));
    }

    @Test @DisplayName("UT-PRO-04 actualizar el mismo producto con su propio código es válido")
    void actualizarMismoCodigo() {
        when(productos.findById(10L)).thenReturn(Optional.of(Producto.builder().id(10L).build()));
        when(productos.findByCodigoBarras("7701234500015")).thenReturn(Optional.of(Producto.builder().id(10L).build()));
        assertThat(servicio.actualizar(10L, req).id()).isEqualTo(10L);
    }

    @Test @DisplayName("UT-PRO-05 consultar un producto inexistente lanza 404")
    void noEncontrado() {
        when(productos.findById(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.obtener(5L))
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(404));
    }

    @Test @DisplayName("UT-PRO-06 no se elimina un producto con existencias")
    void eliminarConExistencias() {
        when(productos.findById(10L)).thenReturn(Optional.of(Producto.builder().id(10L).build()));
        when(inventarios.findById(10L)).thenReturn(Optional.of(Inventario.builder().idProducto(10L)
                .disponible(new BigDecimal("3")).reservado(BigDecimal.ZERO).danado(BigDecimal.ZERO).build()));
        assertThatThrownBy(() -> servicio.eliminar(10L))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("PRODUCTO_CON_EXISTENCIAS"));
        verify(productos, never()).deleteById(any());
    }

    @Test @DisplayName("UT-PRO-07 conversiones repetidas o iguales a la unidad base se rechazan")
    void conversionesInvalidas() {
        when(productos.findById(10L)).thenReturn(Optional.of(Producto.builder().id(10L).unidadBase("unidad").nombre("T").build()));
        var caja = new ConversionRequest("caja", new BigDecimal("100"), 0);
        assertThatThrownBy(() -> servicio.configurarUnidades(10L, List.of(caja, caja))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> servicio.configurarUnidades(10L, List.of(new ConversionRequest("unidad", BigDecimal.ONE, 0))))
                .isInstanceOf(ApiException.class);
    }

    // ---------------------------------------------------------------------------------------------
    // Casos para matar mutantes de PIT en ProductoService (UT-PRO-08 … UT-PRO-20)
    // ---------------------------------------------------------------------------------------------

    /** Solicitud con todos los campos, con espacios alrededor para comprobar la normalización. */
    private final ProductoRequest completo = new ProductoRequest(" Lijadora ", " Dewalt ", " 300W ", " 7701234511111 ",
            " unidad ", TipoRastreo.LOTE, true, true, "  Lijadora orbital  ", "   ", new BigDecimal("19"),
            new BigDecimal("0.30"), new BigDecimal("5"), 6);

    private static String codigo(Throwable e) {
        return ((ApiException) e).getCodigo();
    }

    private Producto existente() {
        Producto p = Producto.builder().id(10L).nombre("Viejo").marca("Marca").presentacion("1").codigoBarras("7700000000001")
                .unidadBase("caja").tipoRastreo(TipoRastreo.NINGUNO).porcentajeIva(BigDecimal.ZERO).build();
        when(productos.findById(10L)).thenReturn(Optional.of(p));
        return p;
    }

    private static Inventario inventario(Long id, String disponible, String reservado, String danado) {
        return Inventario.builder().idProducto(id).disponible(new BigDecimal(disponible))
                .reservado(new BigDecimal(reservado)).danado(new BigDecimal(danado)).build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> detalleEvento(String accion) {
        ArgumentCaptor<Map<String, Object>> c = ArgumentCaptor.forClass(Map.class);
        verify(eventos).registrar(eq(actor), eq(Auditables.PRODUCTOS), eq(accion), eq("PRODUCTO"), any(), anyString(),
                c.capture());
        return c.getValue();
    }

    @Test @DisplayName("UT-PRO-08 crear copia y normaliza todos los campos y audita con la etiqueta y el resumen del producto")
    void crearCompleto() {
        ProductoResponse r = servicio.crear(completo);

        assertThat(r.nombre()).isEqualTo("Lijadora");
        assertThat(r.marca()).isEqualTo("Dewalt");
        assertThat(r.presentacion()).isEqualTo("300W");
        assertThat(r.codigoBarras()).isEqualTo("7701234511111");
        assertThat(r.unidadBase()).isEqualTo("unidad");
        assertThat(r.tipoRastreo()).isEqualTo(TipoRastreo.LOTE);
        assertThat(r.tieneVencimiento()).isTrue();
        assertThat(r.tieneGarantia()).isTrue();
        assertThat(r.descripcion()).isEqualTo("Lijadora orbital");
        assertThat(r.categoria()).isNull();
        assertThat(r.porcentajeIva()).isEqualByComparingTo("19");
        assertThat(r.porcentajeIncremento()).isEqualByComparingTo("0.30");
        assertThat(r.stockMinimo()).isEqualByComparingTo("5");
        assertThat(r.mesesGarantia()).isEqualTo(6);
        verify(productos).findByCodigoBarras("7701234511111");
        verify(productos).findByNombreIgnoreCaseAndMarcaIgnoreCaseAndPresentacionIgnoreCase("Lijadora", "Dewalt", "300W");

        verify(eventos).registrar(eq(actor), eq(Auditables.PRODUCTOS), eq(Auditables.CREAR), eq("PRODUCTO"), eq(10L),
                eq("Producto creado: Lijadora Dewalt 300W (7701234511111)"), any());
        assertThat(detalleEvento(Auditables.CREAR)).containsEntry("nombre", "Lijadora").containsEntry("marca", "Dewalt")
                .containsEntry("codigoBarras", "7701234511111").containsEntry("tipoRastreo", "LOTE")
                .containsEntry("tieneGarantia", true).containsEntry("activo", true);
    }

    @Test @DisplayName("UT-PRO-09 sin garantía no se guardan meses; IVA vacío queda en 0; descripción nula se conserva nula")
    void sinGarantiaNiIva() {
        ProductoResponse r = servicio.crear(new ProductoRequest("Brocha", "Goya", "2in", "7701234522222", "unidad",
                TipoRastreo.NINGUNO, false, false, null, "Pintura", null, null, null, 12));
        assertThat(r.tieneGarantia()).isFalse();
        assertThat(r.mesesGarantia()).isNull();
        assertThat(r.porcentajeIva()).isEqualByComparingTo("0");
        assertThat(r.descripcion()).isNull();
        assertThat(r.categoria()).isEqualTo("Pintura");
    }

    @Test @DisplayName("UT-PRO-10 actualizar valida la unicidad, reemplaza todos los campos y audita antes y después")
    @SuppressWarnings("unchecked")
    void actualizarCompleto() {
        Producto p = existente();
        when(productos.findByCodigoBarras("7701234511111")).thenReturn(Optional.of(Producto.builder().id(99L).build()));
        assertThatThrownBy(() -> servicio.actualizar(10L, completo))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("CODIGO_DUPLICADO"));
        assertThat(p.getNombre()).isEqualTo("Viejo");

        when(productos.findByCodigoBarras("7701234511111")).thenReturn(Optional.empty());
        ProductoResponse r = servicio.actualizar(10L, completo);

        assertThat(r.nombre()).isEqualTo("Lijadora");
        assertThat(p.getMarca()).isEqualTo("Dewalt");
        assertThat(p.getPresentacion()).isEqualTo("300W");
        assertThat(p.getCodigoBarras()).isEqualTo("7701234511111");
        assertThat(p.getUnidadBase()).isEqualTo("unidad");
        assertThat(p.getTipoRastreo()).isEqualTo(TipoRastreo.LOTE);
        assertThat(p.isTieneVencimiento()).isTrue();
        assertThat(p.isTieneGarantia()).isTrue();
        assertThat(p.getDescripcion()).isEqualTo("Lijadora orbital");
        assertThat(p.getPorcentajeIva()).isEqualByComparingTo("19");
        assertThat(p.getPorcentajeIncremento()).isEqualByComparingTo("0.30");
        assertThat(p.getStockMinimo()).isEqualByComparingTo("5");
        assertThat(p.getMesesGarantia()).isEqualTo(6);

        Map<String, Object> d = detalleEvento(Auditables.ACTUALIZAR);
        assertThat((Map<String, Object>) d.get("antes")).containsEntry("nombre", "Viejo").containsEntry("unidadBase", "caja");
        assertThat((Map<String, Object>) d.get("despues")).containsEntry("nombre", "Lijadora").containsEntry("unidadBase", "unidad");
    }

    @Test @DisplayName("UT-PRO-11 activar y desactivar cambian el estado y lo auditan; repetir el mismo estado no hace nada")
    void cambiarEstado() {
        Producto p = existente();

        ProductoResponse r = servicio.cambiarEstado(10L, false);
        assertThat(r).isNotNull();
        assertThat(r.activo()).isFalse();
        assertThat(p.isActivo()).isFalse();
        verify(eventos).registrar(eq(actor), eq(Auditables.PRODUCTOS), eq(Auditables.DESACTIVAR), eq("PRODUCTO"), eq(10L),
                eq("Producto desactivado: Viejo Marca 1 (7700000000001)"), isNull());

        assertThat(servicio.cambiarEstado(10L, true).activo()).isTrue();
        verify(eventos).registrar(eq(actor), eq(Auditables.PRODUCTOS), eq(Auditables.ACTIVAR), eq("PRODUCTO"), eq(10L),
                eq("Producto activado: Viejo Marca 1 (7700000000001)"), isNull());

        org.mockito.Mockito.clearInvocations(productos, eventos);
        assertThat(servicio.cambiarEstado(10L, true).activo()).isTrue();
        verify(productos, never()).save(any());
        verify(eventos, never()).registrar(any(), any(), any(), any(), any(), any(), any());
    }

    @Test @DisplayName("UT-PRO-12 obtener por id y por código de barras (con espacios); código inexistente → 404")
    void consultas() {
        Producto p = existente();
        assertThat(servicio.obtener(10L).nombre()).isEqualTo("Viejo");

        when(productos.findByCodigoBarras("7700000000001")).thenReturn(Optional.of(p));
        assertThat(servicio.obtenerPorCodigo("  7700000000001  ").id()).isEqualTo(10L);
        assertThatThrownBy(() -> servicio.obtenerPorCodigo("7709999999999"))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
    }

    @Test @DisplayName("UT-PRO-13 listar usa el filtro recortado y una cadena vacía cuando no hay filtro")
    void listar() {
        Producto p = existente();
        when(productos.buscar(anyString(), any(Pageable.class))).thenAnswer(a -> new PageImpl<>(List.of(p), a.getArgument(1), 1));
        Pageable pagina = org.springframework.data.domain.PageRequest.of(0, 20);

        PaginaResponse<ProductoResponse> sinFiltro = servicio.listar(null, pagina);
        assertThat(sinFiltro).isNotNull();
        assertThat(sinFiltro.contenido()).extracting(ProductoResponse::nombre).containsExactly("Viejo");
        verify(productos).buscar("", pagina);

        servicio.listar("  taladro  ", pagina);
        verify(productos).buscar("taladro", pagina);
    }

    @Test @DisplayName("UT-PRO-14 selector: existencias, precio vigente, unidades y vencidos (solo por lote); tamaño acotado a 1–50")
    void buscarParaSeleccion() {
        Producto conInventario = Producto.builder().id(10L).nombre("Taladro").marca("Bosch").presentacion("500W")
                .codigoBarras("7701234500015").unidadBase("unidad").tipoRastreo(TipoRastreo.NINGUNO)
                .porcentajeIva(new BigDecimal("19")).stockMinimo(new BigDecimal("2")).build();
        Producto porLote = Producto.builder().id(11L).nombre("Pegante").marca("Sika").presentacion("250ml")
                .codigoBarras("7701234500022").unidadBase("unidad").tipoRastreo(TipoRastreo.LOTE).tieneVencimiento(true).build();
        when(productos.buscarActivos(anyString(), any(Pageable.class)))
                .thenAnswer(a -> new PageImpl<>(List.of(conInventario, porLote), a.getArgument(1), 2));
        when(inventarios.findById(10L)).thenReturn(Optional.of(inventario(10L, "7", "2", "0")));
        when(precios.vigentes(eq(10L), any(Pageable.class)))
                .thenReturn(List.of(Precio.builder().precioVigente(new BigDecimal("15600")).build()));
        when(conversiones.findByIdProducto(10L)).thenReturn(List.of(ConversionUnidad.builder().idProducto(10L)
                .unidadPresentacion("caja").factorAUnidadBase(new BigDecimal("100")).precisionDecimales(0).build()));
        when(lotes.sumaVencida(eq(11L), any())).thenReturn(new BigDecimal("4"));

        List<ProductoSeleccionResponse> r = servicio.buscarParaSeleccion(null, 500);

        assertThat(r).hasSize(2);
        ProductoSeleccionResponse a = r.get(0);
        assertThat(a.id()).isEqualTo(10L);
        assertThat(a.nombre()).isEqualTo("Taladro");
        assertThat(a.codigoBarras()).isEqualTo("7701234500015");
        assertThat(a.disponible()).isEqualByComparingTo("7");
        assertThat(a.reservado()).isEqualByComparingTo("2");
        assertThat(a.precioVigente()).isEqualByComparingTo("15600");
        assertThat(a.vencidoSinBaja()).isEqualByComparingTo("0");
        assertThat(a.stockMinimo()).isEqualByComparingTo("2");
        assertThat(a.unidades()).containsExactly(new ConversionResponse("caja", new BigDecimal("100"), 0));
        verify(lotes, never()).sumaVencida(eq(10L), any());

        ProductoSeleccionResponse b = r.get(1);
        assertThat(b.disponible()).isEqualByComparingTo("0");       // sin registro de inventario
        assertThat(b.reservado()).isEqualByComparingTo("0");
        assertThat(b.precioVigente()).isNull();                     // sin precio aprobado
        assertThat(b.vencidoSinBaja()).isEqualByComparingTo("4");
        assertThat(b.unidades()).isEmpty();

        ArgumentCaptor<Pageable> pag = ArgumentCaptor.forClass(Pageable.class);
        verify(productos).buscarActivos(eq(""), pag.capture());
        assertThat(pag.getValue().getPageSize()).isEqualTo(50);
        assertThat(pag.getValue().getSort().getOrderFor("nombre")).isNotNull();

        servicio.buscarParaSeleccion("  tal  ", 0);
        verify(productos).buscarActivos(eq("tal"), pag.capture());
        assertThat(pag.getValue().getPageSize()).isEqualTo(1);
    }

    @Test @DisplayName("UT-PRO-15 catálogo sin conexión: página desde 0, tamaño acotado a 500 y orden por id")
    void catalogo() {
        Producto p = existente();
        when(productos.buscarActivos(anyString(), any(Pageable.class)))
                .thenAnswer(a -> new PageImpl<>(List.of(p), a.getArgument(1), 1));

        PaginaResponse<ProductoSeleccionResponse> r = servicio.catalogo(-1, 1000);

        assertThat(r).isNotNull();
        assertThat(r.pagina()).isZero();
        assertThat(r.tamano()).isEqualTo(500);
        assertThat(r.contenido()).extracting(ProductoSeleccionResponse::nombre).containsExactly("Viejo");
        ArgumentCaptor<Pageable> pag = ArgumentCaptor.forClass(Pageable.class);
        verify(productos).buscarActivos(eq(""), pag.capture());
        assertThat(pag.getValue().getSort().getOrderFor("id")).isNotNull();
        assertThat(pag.getValue().getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test @DisplayName("UT-PRO-16 selección por código: solo productos activos; inactivo o inexistente → 404")
    void seleccionPorCodigo() {
        Producto p = existente();
        when(productos.findByCodigoBarras("7700000000001")).thenReturn(Optional.of(p));

        ProductoSeleccionResponse r = servicio.seleccionPorCodigo(" 7700000000001 ");
        assertThat(r).isNotNull();
        assertThat(r.id()).isEqualTo(10L);

        p.setActivo(false);
        assertThatThrownBy(() -> servicio.seleccionPorCodigo("7700000000001"))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
        assertThatThrownBy(() -> servicio.seleccionPorCodigo("7709999999999"))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
    }

    @Test @DisplayName("UT-PRO-17 eliminar: con reservado o no apto se rechaza; sin existencias borra conversiones, saldo y producto")
    void eliminar() {
        existente();
        when(inventarios.findById(10L)).thenReturn(Optional.of(inventario(10L, "0", "1", "0")));
        assertThatThrownBy(() -> servicio.eliminar(10L))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("PRODUCTO_CON_EXISTENCIAS"));
        when(inventarios.findById(10L)).thenReturn(Optional.of(inventario(10L, "0", "0", "1")));
        assertThatThrownBy(() -> servicio.eliminar(10L))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("PRODUCTO_CON_EXISTENCIAS"));
        verify(productos, never()).deleteById(any());

        Inventario vacio = inventario(10L, "0", "0", "0");
        when(inventarios.findById(10L)).thenReturn(Optional.of(vacio));
        servicio.eliminar(10L);

        verify(conversiones).deleteByIdProducto(10L);
        verify(inventarios).delete(vacio);
        verify(productos).deleteById(10L);
        verify(productos).flush();
        assertThat(detalleEvento(Auditables.ELIMINAR)).containsEntry("nombre", "Viejo");
    }

    @Test @DisplayName("UT-PRO-18 eliminar un producto sin registro de inventario no intenta borrar el saldo")
    void eliminarSinInventario() {
        existente();
        when(inventarios.findById(10L)).thenReturn(Optional.empty());
        servicio.eliminar(10L);
        verify(inventarios, never()).delete(any());
        verify(productos).deleteById(10L);
    }

    @Test @DisplayName("UT-PRO-19 configurar unidades reemplaza las conversiones (presentación recortada) y las audita")
    void configurarUnidades() {
        existente();
        when(conversiones.save(any(ConversionUnidad.class))).thenAnswer(a -> a.getArgument(0));

        List<ConversionResponse> r = servicio.configurarUnidades(10L, List.of(
                new ConversionRequest(" Paquete ", new BigDecimal("10"), 0),
                new ConversionRequest("bulto", new BigDecimal("2.5"), 2)));

        assertThat(r).containsExactly(new ConversionResponse("Paquete", new BigDecimal("10"), 0),
                new ConversionResponse("bulto", new BigDecimal("2.5"), 2));
        verify(conversiones).deleteByIdProducto(10L);
        verify(conversiones).flush();
        assertThat(detalleEvento(Auditables.ACTUALIZAR)).containsEntry("conversiones", r);

        when(conversiones.findByIdProducto(10L)).thenReturn(List.of(ConversionUnidad.builder().idProducto(10L)
                .unidadPresentacion("Paquete").factorAUnidadBase(new BigDecimal("10")).precisionDecimales(0).build()));
        assertThat(servicio.unidades(10L)).containsExactly(new ConversionResponse("Paquete", new BigDecimal("10"), 0));
        assertThatThrownBy(() -> servicio.unidades(404L)).satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
    }

    @Test @DisplayName("UT-PRO-20 existencias por estado y precio vigente (nulo si no hay uno aprobado)")
    void existenciasYPrecio() {
        existente();
        when(inventarios.findById(10L)).thenReturn(Optional.of(inventario(10L, "7", "2", "1")));
        assertThat(servicio.existencias(10L)).isEqualTo(new InventarioResponse(10L, new BigDecimal("7"),
                new BigDecimal("2"), new BigDecimal("1")));
        when(inventarios.findById(10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.existencias(10L)).satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));

        when(precios.vigentes(anyLong(), any(Pageable.class))).thenReturn(List.of());
        assertThat(servicio.precioVigente(10L)).isNull();
        when(precios.vigentes(anyLong(), any(Pageable.class)))
                .thenReturn(List.of(Precio.builder().precioVigente(new BigDecimal("15600")).build()));
        assertThat(servicio.precioVigente(10L)).isEqualByComparingTo("15600");
    }
}