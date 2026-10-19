/*
 * Nombre: ExportacionServiceTest.java
 * Descripción: Pruebas unitarias de ExportacionService: columnas con/sin costos, contenido de filas, neutralización,
 *              paginación por lotes, límite MAX_FILAS y registro del evento de exportación.
 * Fecha de creación: 2026-10-19
 * Fecha de actualización: 2026-10-19
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ferreteria.inventario.config.ParametrosNegocio;
import com.ferreteria.inventario.domain.enums.EstadoDocumento;
import com.ferreteria.inventario.domain.enums.EstadoInventario;
import com.ferreteria.inventario.domain.enums.TipoIdentificacion;
import com.ferreteria.inventario.domain.enums.TipoMovimiento;
import com.ferreteria.inventario.domain.enums.TipoPersona;
import com.ferreteria.inventario.domain.enums.TipoRastreo;
import com.ferreteria.inventario.domain.model.Producto;
import com.ferreteria.inventario.domain.model.Tercero;
import com.ferreteria.inventario.dto.ExistenciaResponse;
import com.ferreteria.inventario.dto.MovimientoResponse;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.dto.VentaResumenResponse;
import com.ferreteria.inventario.excel.Excel;
import com.ferreteria.inventario.repository.ProductoRepository;
import com.ferreteria.inventario.repository.TerceroRepository;
import com.ferreteria.inventario.service.Actor;
import com.ferreteria.inventario.service.Auditables;
import com.ferreteria.inventario.service.AuditoriaService;
import com.ferreteria.inventario.service.EventoAuditoriaService;
import com.ferreteria.inventario.service.ExportacionService;
import com.ferreteria.inventario.service.InventarioService;
import com.ferreteria.inventario.service.VentaConsultaService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExportacionServiceTest {

    private static final Actor ACTOR = new Actor(3, "admin@ferreteria.com", "10.0.0.1");
    // Locale fijo: en un Windows en español (es-CO) 30.5 se mostraría como "30,5"
    private static final DataFormatter FMT = new DataFormatter(java.util.Locale.US);

    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 10, 16, 15, 0);

    private static final List<String> COLS_PRODUCTOS = List.of("id", "codigo_barras", "nombre", "marca", "presentacion",
            "categoria", "unidad_base", "tipo_rastreo", "tiene_vencimiento", "tiene_garantia", "meses_garantia",
            "porcentaje_iva", "porcentaje_incremento", "stock_minimo", "activo");
    private static final List<String> COLS_EXISTENCIAS = List.of("producto_id", "producto", "codigo_barras", "unidad_base",
            "disponible", "reservado", "danado", "total", "vencido_sin_baja", "por_vencer", "stock_minimo", "precio_vigente",
            "alertas");

    @Mock private ProductoRepository productos;
    @Mock private TerceroRepository terceros;
    @Mock private InventarioService inventario;
    @Mock private VentaConsultaService ventas;
    @Mock private AuditoriaService auditoria;
    @Mock private EventoAuditoriaService eventos;

    private ExportacionService servicio;

    @BeforeEach
    void setUp() {
        servicio = new ExportacionService(productos, terceros, inventario, ventas, auditoria, eventos,
                ParametrosNegocio.porDefecto());
    }

    // ------------------------------------------------------------------ datos de prueba

    private static Producto martillo() {
        return Producto.builder().id(7L).codigoBarras("7701234567890").nombre("Martillo").marca("Stanley")
                .presentacion("16 oz").categoria("Herramientas").unidadBase("unidad").tipoRastreo(TipoRastreo.LOTE)
                .tieneVencimiento(false).tieneGarantia(true).mesesGarantia(12).porcentajeIva(new BigDecimal("19"))
                .porcentajeIncremento(new BigDecimal("30")).stockMinimo(new BigDecimal("5")).activo(true)
                .costoPromedio(new BigDecimal("25000")).build();
    }

    private static Producto taladro() {
        return Producto.builder().id(8L).codigoBarras("7709999999999").nombre("=Taladro").marca("Bosch")
                .presentacion("500W").categoria("Eléctricos").unidadBase("unidad").tipoRastreo(TipoRastreo.SERIE)
                .tieneVencimiento(true).tieneGarantia(false).porcentajeIva(new BigDecimal("19"))
                .porcentajeIncremento(new BigDecimal("25")).stockMinimo(new BigDecimal("2")).activo(false)
                .costoPromedio(new BigDecimal("180000")).build();
    }

    private static ExistenciaResponse existencia(long id, String nombre) {
        return new ExistenciaResponse(id, nombre, "77011" + id, "Herramientas", "unidad", TipoRastreo.NINGUNO, true,
                new BigDecimal("8"), new BigDecimal("2"), BigDecimal.ONE, new BigDecimal("11"), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("5"), new BigDecimal("1000"), new BigDecimal("1500"),
                new BigDecimal("11000"), List.of("STOCK_BAJO", "POR_VENCER"));
    }

    private static VentaResumenResponse venta(long id) {
        return new VentaResumenResponse(id, "V-0000" + id, "FE-123", FECHA, 4L, "=Ferretería Sur",
                "vendedor@ferreteria.com", EstadoDocumento.ACTIVO, new BigDecimal("119000"), true, "USD",
                new BigDecimal("30.5"));
    }

    private static MovimientoResponse movimiento(long id) {
        return new MovimientoResponse(id, TipoMovimiento.VENTA, 7L, new BigDecimal("3"), FECHA, 3, "10.0.0.1", "V-000010",
                new BigDecimal("20"), new BigDecimal("17"), "Venta mostrador", null, EstadoInventario.DISPONIBLE, 9L);
    }

    private static <T> PaginaResponse<T> pagina(List<T> contenido, int numero, int totalPaginas) {
        return new PaginaResponse<>(contenido, numero, 100, (long) totalPaginas * 100, totalPaginas);
    }

    // ------------------------------------------------------------------ utilidades

    private static Sheet hoja(byte[] xlsx, String nombre) throws IOException {
        assertThat(xlsx).isNotNull().isNotEmpty();
        XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx));
        Sheet s = wb.getSheet(nombre);
        assertThat(s).as("hoja " + nombre).isNotNull();
        return s;
    }

    private static List<String> fila(Sheet s, int indice) {
        Row r = s.getRow(indice);
        assertThat(r).as("fila " + indice).isNotNull();
        List<String> out = new ArrayList<>();
        for (int c = 0; c < r.getLastCellNum(); c++) {
            out.add(FMT.formatCellValue(r.getCell(c)));
        }
        return out;
    }

    /** Verifica el evento de auditoría y devuelve el número de filas registrado. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private int filasRegistradas(String que) {
        ArgumentCaptor<String> descripcion = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map> detalle = ArgumentCaptor.forClass(Map.class);
        verify(eventos).registrar(eq(ACTOR), eq(Auditables.IMPORTACION), eq(Auditables.EXPORTAR), eq("EXPORTACION"),
                eq(que), descripcion.capture(), detalle.capture());
        int filas = (Integer) detalle.getValue().get("filas");
        assertThat(detalle.getValue()).hasSize(1);
        assertThat(descripcion.getValue()).isEqualTo("Exportación de " + que + " a Excel (" + filas + " filas)");
        return filas;
    }

    /** Ejecuta una exportación con Excel simulado (sin escribir celdas) para recorrer el límite rápidamente. */
    private void conExcelSimulado(String nombreHoja, Supplier<byte[]> exportacion) {
        SXSSFWorkbook wb = Excel.libro(nombreHoja, List.of("c"));
        try (MockedStatic<Excel> excel = mockStatic(Excel.class)) {
            excel.when(() -> Excel.libro(anyString(), anyList())).thenReturn(wb);
            excel.when(() -> Excel.bytes(any())).thenReturn(new byte[] {1});
            assertThat(exportacion.get()).containsExactly((byte) 1);
        } finally {
            wb.dispose();
        }
    }

    // ------------------------------------------------------------------ productos

    @Test @DisplayName("UT-EXP-01 productos sin costos: 15 columnas, filas en orden, valores tipados y evento con 2 filas")
    void productosSinCostos() throws IOException {
        when(productos.findAll(any(Sort.class))).thenReturn(List.of(martillo(), taladro()));

        byte[] xlsx = servicio.productos(false, ACTOR);

        verify(productos).findAll(Sort.by("nombre", "marca", "presentacion"));
        Sheet s = hoja(xlsx, "productos");
        assertThat(fila(s, 0)).containsExactlyElementsOf(COLS_PRODUCTOS);
        assertThat(s.getLastRowNum()).isEqualTo(2);
        assertThat(fila(s, 1)).containsExactly("7", "7701234567890", "Martillo", "Stanley", "16 oz", "Herramientas",
                "unidad", "LOTE", "NO", "SI", "12", "19", "30", "5", "SI");
        assertThat(fila(s, 2)).containsExactly("8", "7709999999999", "'=Taladro", "Bosch", "500W", "Eléctricos",
                "unidad", "SERIE", "SI", "NO", "", "19", "25", "2", "NO");
        assertThat(filasRegistradas("productos")).isEqualTo(2);
    }

    @Test @DisplayName("UT-EXP-02 productos con costos: agrega costo_promedio como columna 16 y su valor")
    void productosConCostos() throws IOException {
        when(productos.findAll(any(Sort.class))).thenReturn(List.of(martillo(), taladro()));

        Sheet s = hoja(servicio.productos(true, ACTOR), "productos");

        List<String> esperadas = new ArrayList<>(COLS_PRODUCTOS);
        esperadas.add("costo_promedio");
        assertThat(fila(s, 0)).containsExactlyElementsOf(esperadas);
        assertThat(fila(s, 1)).hasSize(16).endsWith("SI", "25000");
        assertThat(fila(s, 2)).hasSize(16).endsWith("NO", "180000");
        assertThat(filasRegistradas("productos")).isEqualTo(2);
    }

    @Test @DisplayName("UT-EXP-03 productos sin registros: solo encabezado y evento con 0 filas")
    void productosVacio() throws IOException {
        when(productos.findAll(any(Sort.class))).thenReturn(List.of());

        Sheet s = hoja(servicio.productos(false, ACTOR), "productos");

        assertThat(s.getLastRowNum()).isZero();
        assertThat(fila(s, 0)).containsExactlyElementsOf(COLS_PRODUCTOS);
        assertThat(filasRegistradas("productos")).isZero();
    }

    // ------------------------------------------------------------------ existencias

    @Test @DisplayName("UT-EXP-04 existencias sin costos: 13 columnas, alertas unidas y una sola página consultada")
    void existenciasSinCostos() throws IOException {
        when(inventario.existencias(eq(""), isNull(), anyInt(), anyInt()))
                .thenReturn(pagina(List.of(existencia(1, "Martillo"), existencia(2, "Pala")), 0, 1));

        Sheet s = hoja(servicio.existencias(false, ACTOR), "existencias");

        verify(inventario, times(1)).existencias(eq(""), isNull(), anyInt(), anyInt());
        verify(inventario).existencias("", null, 0, 100);
        assertThat(fila(s, 0)).containsExactlyElementsOf(COLS_EXISTENCIAS);
        assertThat(s.getLastRowNum()).isEqualTo(2);
        assertThat(fila(s, 1)).containsExactly("1", "Martillo", "770111", "unidad", "8", "2", "1", "11", "0", "0", "5",
                "1500", "STOCK_BAJO, POR_VENCER");
        assertThat(fila(s, 2).get(1)).isEqualTo("Pala");
        assertThat(filasRegistradas("existencias")).isEqualTo(2);
    }

    @Test @DisplayName("UT-EXP-05 existencias con costos: agrega costo_promedio y valor_inventario")
    void existenciasConCostos() throws IOException {
        when(inventario.existencias(eq(""), isNull(), anyInt(), anyInt()))
                .thenReturn(pagina(List.of(existencia(1, "Martillo")), 0, 1));

        Sheet s = hoja(servicio.existencias(true, ACTOR), "existencias");

        List<String> esperadas = new ArrayList<>(COLS_EXISTENCIAS);
        esperadas.add("costo_promedio");
        esperadas.add("valor_inventario");
        assertThat(fila(s, 0)).containsExactlyElementsOf(esperadas);
        assertThat(fila(s, 1)).hasSize(15).endsWith("STOCK_BAJO, POR_VENCER", "1000", "11000");
        assertThat(filasRegistradas("existencias")).isEqualTo(1);
    }

    @Test @DisplayName("UT-EXP-06 existencias recorre exactamente totalPaginas (0 y 1) y se detiene")
    void existenciasPaginas() throws IOException {
        when(inventario.existencias(eq(""), isNull(), anyInt(), eq(100))).thenAnswer(a -> {
            int p = a.getArgument(2);
            return pagina(List.of(existencia(p + 1L, "P" + p)), p, 2);
        });

        Sheet s = hoja(servicio.existencias(false, ACTOR), "existencias");

        verify(inventario).existencias("", null, 0, 100);
        verify(inventario).existencias("", null, 1, 100);
        verify(inventario, times(2)).existencias(eq(""), isNull(), anyInt(), anyInt());
        verify(inventario, never()).existencias(eq(""), isNull(), eq(2), anyInt());
        assertThat(s.getLastRowNum()).isEqualTo(2);
        assertThat(fila(s, 1).get(1)).isEqualTo("P0");
        assertThat(fila(s, 2).get(1)).isEqualTo("P1");
        assertThat(filasRegistradas("existencias")).isEqualTo(2);
    }

    @Test @DisplayName("UT-EXP-07 existencias vacías (0 páginas): solo encabezado y 0 filas")
    void existenciasVacias() throws IOException {
        when(inventario.existencias(eq(""), isNull(), anyInt(), anyInt())).thenReturn(pagina(List.of(), 0, 0));

        Sheet s = hoja(servicio.existencias(false, ACTOR), "existencias");

        verify(inventario, times(1)).existencias(eq(""), isNull(), anyInt(), anyInt());
        assertThat(s.getLastRowNum()).isZero();
        assertThat(filasRegistradas("existencias")).isZero();
    }

    // ------------------------------------------------------------------ terceros

    @Test @DisplayName("UT-EXP-08 terceros: columnas, nombre por razón social o nombres, teléfono neutralizado")
    void tercerosContenido() throws IOException {
        Tercero juridica = Tercero.builder().id(4L).tipoPersona(TipoPersona.JURIDICA)
                .tipoIdentificacion(TipoIdentificacion.NIT).numeroIdentificacion("900123456")
                .razonSocial("Ferretería Sur SAS").direccion("Calle 1").ciudad("Neiva").telefono("3001234567")
                .correo("compras@sur.com").cliente(true).proveedor(false).build();
        Tercero natural = Tercero.builder().id(5L).tipoPersona(TipoPersona.NATURAL)
                .tipoIdentificacion(TipoIdentificacion.CC).numeroIdentificacion("1075000000").primerNombre("Ana")
                .primerApellido("Pérez").direccion("Carrera 2").ciudad("Pitalito").telefono("+573001112233")
                .correo("ana@correo.com").cliente(false).proveedor(true).build();
        when(terceros.findAll(any(Sort.class))).thenReturn(List.of(juridica, natural));

        Sheet s = hoja(servicio.terceros(ACTOR), "terceros");

        verify(terceros).findAll(Sort.by("id"));
        assertThat(fila(s, 0)).containsExactly("id", "tipo_persona", "tipo_identificacion", "numero_identificacion",
                "nombre", "direccion", "ciudad", "telefono", "correo", "es_cliente", "es_proveedor");
        assertThat(s.getLastRowNum()).isEqualTo(2);
        assertThat(fila(s, 1)).containsExactly("4", "JURIDICA", "NIT", "900123456", "Ferretería Sur SAS", "Calle 1",
                "Neiva", "3001234567", "compras@sur.com", "SI", "NO");
        assertThat(fila(s, 2)).containsExactly("5", "NATURAL", "CC", "1075000000", "Ana Pérez", "Carrera 2", "Pitalito",
                "'+573001112233", "ana@correo.com", "NO", "SI");
        assertThat(filasRegistradas("terceros")).isEqualTo(2);
    }

    @Test @DisplayName("UT-EXP-09 terceros vacío: solo encabezado y 0 filas")
    void tercerosVacio() throws IOException {
        when(terceros.findAll(any(Sort.class))).thenReturn(List.of());

        Sheet s = hoja(servicio.terceros(ACTOR), "terceros");

        assertThat(s.getLastRowNum()).isZero();
        assertThat(filasRegistradas("terceros")).isZero();
    }

    // ------------------------------------------------------------------ ventas

    // @Test @DisplayName("UT-EXP-10 ventas: filtros por rango, columnas, fecha ISO, cliente neutralizado y moneda")
    // void ventasContenido() throws IOException {
    //     LocalDate desde = LocalDate.of(2026, 10, 1);
    //     LocalDate hasta = LocalDate.of(2026, 10, 16);
    //     when(ventas.listar(any(), anyInt(), anyInt())).thenReturn(pagina(List.of(venta(10), venta(11)), 0, 1));

    //     Sheet s = hoja(servicio.ventas(desde, hasta, ACTOR), "ventas");

    //     verify(ventas).listar(new VentaConsultaService.Filtros(null, null, desde, hasta, null), 0, 100);
    //     verify(ventas, times(1)).listar(any(), anyInt(), anyInt());
    //     assertThat(fila(s, 0)).containsExactly("id", "numero_interno", "factura", "fecha_utc", "cliente", "usuario",
    //             "estado", "total_cop", "con_devoluciones", "moneda", "total_moneda");
    //     assertThat(s.getLastRowNum()).isEqualTo(2);
    //     assertThat(fila(s, 1)).containsExactly("10", "V-000010", "FE-123", "2026-10-16 15:00:00", "'=Ferretería Sur",
    //             "vendedor@ferreteria.com", "ACTIVO", "119000", "SI", "USD", "30.5");
    //     assertThat(fila(s, 2).get(0)).isEqualTo("11");
    //     assertThat(filasRegistradas("ventas")).isEqualTo(2);
    // }

    @Test @DisplayName("UT-EXP-11 ventas recorre las páginas 0 y 1 y no pide la 2")
    void ventasPaginas() throws IOException {
        when(ventas.listar(any(), anyInt(), eq(100))).thenAnswer(a -> {
            int p = a.getArgument(1);
            return pagina(List.of(venta(20L + p)), p, 2);
        });

        Sheet s = hoja(servicio.ventas(null, null, ACTOR), "ventas");

        verify(ventas).listar(any(), eq(0), eq(100));
        verify(ventas).listar(any(), eq(1), eq(100));
        verify(ventas, times(2)).listar(any(), anyInt(), anyInt());
        verify(ventas, never()).listar(any(), eq(2), anyInt());
        assertThat(s.getLastRowNum()).isEqualTo(2);
        assertThat(fila(s, 1).get(0)).isEqualTo("20");
        assertThat(fila(s, 2).get(0)).isEqualTo("21");
        assertThat(filasRegistradas("ventas")).isEqualTo(2);
    }

    // ------------------------------------------------------------------ movimientos

    @Test @DisplayName("UT-EXP-12 movimientos: filtros y zona de negocio, columnas y valores (original nulo en blanco)")
    void movimientosContenido() throws IOException {
        AuditoriaService.Filtros f = new AuditoriaService.Filtros(7L, TipoMovimiento.VENTA, 3, "V-000010",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 16));
        when(auditoria.consultarFiltrado(any(), any(), anyInt(), anyInt()))
                .thenReturn(pagina(List.of(movimiento(55), movimiento(56)), 0, 1));

        Sheet s = hoja(servicio.movimientos(f, ACTOR), "movimientos");

        verify(auditoria).consultarFiltrado(same(f), eq(ZoneId.of("America/Bogota")), eq(0), eq(100));
        verify(auditoria, times(1)).consultarFiltrado(any(), any(), anyInt(), anyInt());
        assertThat(fila(s, 0)).containsExactly("id", "fecha_utc", "tipo", "estado_inventario", "producto_id", "cantidad",
                "valor_anterior", "valor_nuevo", "documento", "motivo", "usuario_id", "ip", "lote_id",
                "movimiento_original");
        assertThat(s.getLastRowNum()).isEqualTo(2);
        assertThat(fila(s, 1)).containsExactly("55", "2026-10-16 15:00:00", "VENTA", "DISPONIBLE", "7", "3", "20", "17",
                "V-000010", "Venta mostrador", "3", "10.0.0.1", "9", "");
        assertThat(fila(s, 2).get(0)).isEqualTo("56");
        assertThat(filasRegistradas("movimientos")).isEqualTo(2);
    }

    @Test @DisplayName("UT-EXP-13 movimientos recorre las páginas 0 y 1 y no pide la 2")
    void movimientosPaginas() throws IOException {
        AuditoriaService.Filtros f = new AuditoriaService.Filtros(null, null, null, null, null, null);
        when(auditoria.consultarFiltrado(any(), any(), anyInt(), eq(100))).thenAnswer(a -> {
            int p = a.getArgument(2);
            return pagina(List.of(movimiento(70L + p)), p, 2);
        });

        Sheet s = hoja(servicio.movimientos(f, ACTOR), "movimientos");

        verify(auditoria).consultarFiltrado(any(), any(), eq(0), eq(100));
        verify(auditoria).consultarFiltrado(any(), any(), eq(1), eq(100));
        verify(auditoria, times(2)).consultarFiltrado(any(), any(), anyInt(), anyInt());
        verify(auditoria, never()).consultarFiltrado(any(), any(), eq(2), anyInt());
        assertThat(s.getLastRowNum()).isEqualTo(2);
        assertThat(fila(s, 1).get(0)).isEqualTo("70");
        assertThat(fila(s, 2).get(0)).isEqualTo("71");
        assertThat(filasRegistradas("movimientos")).isEqualTo(2);
    }

    // ------------------------------------------------------------------ límite MAX_FILAS

    @Test @DisplayName("UT-EXP-14 productos: con MAX_FILAS + 1 registros exporta exactamente MAX_FILAS")
    void productosLimite() {
        when(productos.findAll(any(Sort.class)))
                .thenReturn(Collections.nCopies(ExportacionService.MAX_FILAS + 1, martillo()));

        conExcelSimulado("productos", () -> servicio.productos(false, ACTOR));

        assertThat(filasRegistradas("productos")).isEqualTo(ExportacionService.MAX_FILAS);
    }

    @Test @DisplayName("UT-EXP-15 terceros: con MAX_FILAS + 1 registros exporta exactamente MAX_FILAS")
    void tercerosLimite() {
        Tercero t = Tercero.builder().id(1L).tipoPersona(TipoPersona.NATURAL).tipoIdentificacion(TipoIdentificacion.CC)
                .numeroIdentificacion("1").primerNombre("Ana").direccion("d").ciudad("c").telefono("1").correo("a@b.co")
                .build();
        when(terceros.findAll(any(Sort.class))).thenReturn(Collections.nCopies(ExportacionService.MAX_FILAS + 1, t));

        conExcelSimulado("terceros", () -> servicio.terceros(ACTOR));

        assertThat(filasRegistradas("terceros")).isEqualTo(ExportacionService.MAX_FILAS);
    }

    /*
     * Límite de los listados paginados: la página 0 trae 99 filas y las demás 100. Tras 500 páginas van
     * 49.999 filas (r = 50.000), y como la condición es r <= MAX_FILAS se pide una página más: 501 páginas y
     * 50.099 filas. Si la condición fuera r < MAX_FILAS se detendría en 49.999.
     */

    @Test @DisplayName("UT-EXP-16 existencias: con r == MAX_FILAS todavía consulta una página más (r <= MAX_FILAS)")
    void existenciasLimite() {
        ExistenciaResponse e = existencia(1, "Martillo");
        when(inventario.existencias(eq(""), isNull(), anyInt(), eq(100))).thenAnswer(a -> {
            int p = a.getArgument(2);
            return pagina(Collections.nCopies(p == 0 ? 99 : 100, e), p, 10_000);
        });

        conExcelSimulado("existencias", () -> servicio.existencias(false, ACTOR));

        verify(inventario, times(501)).existencias(eq(""), isNull(), anyInt(), anyInt());
        assertThat(filasRegistradas("existencias")).isEqualTo(50_099);
    }

    @Test @DisplayName("UT-EXP-17 ventas: con r == MAX_FILAS todavía consulta una página más (r <= MAX_FILAS)")
    void ventasLimite() {
        VentaResumenResponse v = venta(1);
        when(ventas.listar(any(), anyInt(), eq(100))).thenAnswer(a -> {
            int p = a.getArgument(1);
            return pagina(Collections.nCopies(p == 0 ? 99 : 100, v), p, 10_000);
        });

        conExcelSimulado("ventas", () -> servicio.ventas(null, null, ACTOR));

        verify(ventas, times(501)).listar(any(), anyInt(), anyInt());
        assertThat(filasRegistradas("ventas")).isEqualTo(50_099);
    }

    @Test @DisplayName("UT-EXP-18 movimientos: con r == MAX_FILAS todavía consulta una página más (r <= MAX_FILAS)")
    void movimientosLimite() {
        MovimientoResponse m = movimiento(1);
        AuditoriaService.Filtros f = new AuditoriaService.Filtros(null, null, null, null, null, null);
        when(auditoria.consultarFiltrado(any(), any(), anyInt(), eq(100))).thenAnswer(a -> {
            int p = a.getArgument(2);
            return pagina(Collections.nCopies(p == 0 ? 99 : 100, m), p, 10_000);
        });

        conExcelSimulado("movimientos", () -> servicio.movimientos(f, ACTOR));

        verify(auditoria, times(501)).consultarFiltrado(any(), any(), anyInt(), anyInt());
        assertThat(filasRegistradas("movimientos")).isEqualTo(50_099);
    }
}