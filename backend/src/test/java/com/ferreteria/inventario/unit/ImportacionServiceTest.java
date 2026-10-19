/*
 * Nombre: ImportacionServiceTest.java
 * Descripción: Pruebas unitarias de la migración desde Excel (RF-10, RN-15): columnas, errores por fila, duplicados exactos y posibles, confirmación selectiva, reproceso del mismo archivo y neutralización de fórmulas.
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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferreteria.inventario.config.ParametrosNegocio;
import com.ferreteria.inventario.domain.enums.EstadoImportacion;
import com.ferreteria.inventario.domain.enums.TipoIdentificacion;
import com.ferreteria.inventario.domain.enums.TipoPersona;
import com.ferreteria.inventario.domain.enums.TipoRastreo;
import com.ferreteria.inventario.domain.model.Importacion;
import com.ferreteria.inventario.domain.model.Producto;
import com.ferreteria.inventario.domain.model.Tercero;
import com.ferreteria.inventario.dto.ImportacionResponse;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.dto.ProductoRequest;
import com.ferreteria.inventario.dto.TerceroRequest;
import com.ferreteria.inventario.excel.Excel;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.*;
import com.ferreteria.inventario.service.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

/** Casos UT-IMP-01 … UT-IMP-21 (UT-IMP-09 en adelante: casos para matar mutantes de PIT). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImportacionServiceTest {

    @Mock ImportacionRepository importaciones;
    @Mock ProductoRepository productos;
    @Mock TerceroRepository terceros;
    @Mock UsuarioRepository usuarios;
    @Mock ProductoService productoService;
    @Mock TerceroService terceroService;
    @Mock EventoAuditoriaService eventos;
    @Mock InventarioRepository inventarios;
    @Mock CierrePeriodoRepository cierres;
    @Mock AuditoriaService auditoria;
    @Mock Validator validador;

    ImportacionService servicio;
    Importacion guardada;
    final Actor actor = new Actor(3, "marta.inventario@ferreteria.com", "x");

    @BeforeEach
    void preparar() {
        KardexService kardex = new KardexService(inventarios, productos, auditoria, cierres, ParametrosNegocio.porDefecto(),
                Clock.fixed(Instant.parse("2026-10-16T15:00:00Z"), ZoneOffset.UTC));
        servicio = new ImportacionService(importaciones, productos, terceros, usuarios, productoService, terceroService,
                eventos, kardex, validador, new ObjectMapper().findAndRegisterModules());
        when(validador.validate(any())).thenReturn(Set.of());
        when(productos.findAll()).thenReturn(List.of(Producto.builder().id(1L).nombre("Taladro").marca("Bosch")
                .presentacion("500W").codigoBarras("7701234500015").build()));
        when(productos.findByCodigoBarras(any())).thenReturn(Optional.empty());
        when(productos.findByNombreIgnoreCaseAndMarcaIgnoreCaseAndPresentacionIgnoreCase(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(importaciones.save(any(Importacion.class))).thenAnswer(a -> {
            Importacion i = a.getArgument(0);
            if (i.getId() == null) {
                i.setId(1L);
            }
            guardada = i;
            return i;
        });
        when(importaciones.findForUpdate(1L)).thenAnswer(a -> Optional.ofNullable(guardada));
        when(usuarios.findById(any())).thenReturn(Optional.empty());
    }

    private static byte[] libro(List<String> encabezado, List<List<String>> filas) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("datos");
            Row r = s.createRow(0);
            for (int i = 0; i < encabezado.size(); i++) {
                r.createCell(i).setCellValue(encabezado.get(i));
            }
            for (int f = 0; f < filas.size(); f++) {
                Row x = s.createRow(f + 1);
                for (int i = 0; i < filas.get(f).size(); i++) {
                    x.createCell(i).setCellValue(filas.get(f).get(i));
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static final List<String> ENC = List.of("codigo_barras", "nombre", "marca", "presentacion", "unidad_base",
            "tipo_rastreo", "tiene_vencimiento");

    private ImportacionResponse validarEjemplo() throws Exception {
        return servicio.validar("productos", "productos.xlsx", libro(ENC, List.of(
                List.of("7701234599991", "Martillo", "Stanley", "16 oz", "unidad", "NINGUNO", "NO"),     // válida
                List.of("7701234500015", "Taladro", "Bosch", "500W", "unidad", "", ""),                // duplicado exacto
                List.of("7701234599992", "Taladro", "Bosch", "500 W", "unidad", "", ""),               // posible duplicado
                List.of("7701234599993", "Sierra", "Truper", "24\"", "unidad", "RASTRO", ""),          // valor inválido
                List.of("7701234599991", "Martillo2", "Stanley", "20 oz", "unidad", "", ""))),         // código repetido en el archivo
                actor);
    }

    @Test @DisplayName("UT-IMP-01 la validación clasifica cada fila sin importar nada")
    void clasifica() throws Exception {
        ImportacionResponse r = validarEjemplo();
        assertThat(r.filas()).extracting(ImportacionResponse.Fila::estado)
                .containsExactly("VALIDA", "DUPLICADO", "POSIBLE_DUPLICADO", "ERROR", "ERROR");
        assertThat(r.filasValidas()).isEqualTo(1);
        assertThat(r.filasError()).isEqualTo(2);
        assertThat(r.filasDuplicadas()).isEqualTo(2);
        assertThat(r.filas().get(3).errores()).extracting(ImportacionResponse.ErrorCelda::columna).contains("tipo_rastreo");
        assertThat(r.filas().get(2).coincidencia()).contains("Taladro");
        verify(productoService, never()).crear(any());
    }

    @Test @DisplayName("UT-IMP-02 faltan columnas obligatorias → 422 con la lista")
    void columnasFaltantes() {
        assertThatThrownBy(() -> servicio.validar("productos", "p.xlsx", libro(List.of("nombre", "marca"), List.of(
                List.of("A", "B"))), actor))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("COLUMNAS_FALTANTES"));
    }

    @Test @DisplayName("UT-IMP-03 un archivo que no es .xlsx válido se rechaza")
    void archivoInvalido() {
        assertThatThrownBy(() -> servicio.validar("productos", "p.xlsx", "no soy excel".getBytes(), actor))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("ARCHIVO_INVALIDO"));
        assertThatThrownBy(() -> servicio.validar("productos", "p.csv", "a,b".getBytes(), actor))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("ARCHIVO_INVALIDO"));
    }

    @Test @DisplayName("UT-IMP-04 confirmar importa solo válidas y los posibles duplicados aprobados")
    void confirmaSeleccion() throws Exception {
        validarEjemplo();
        ImportacionResponse r = servicio.confirmar(1L, List.of(4), actor);
        verify(productoService, times(2)).crear(any());
        assertThat(r.estado()).isEqualTo(EstadoImportacion.IMPORTADA);
        assertThat(r.filasImportadas()).isEqualTo(2);
        assertThat(r.filas()).extracting(ImportacionResponse.Fila::estado)
                .containsExactly("IMPORTADA", "DUPLICADO", "IMPORTADA", "ERROR", "ERROR");
    }

    @Test @DisplayName("UT-IMP-05 sin aprobar el posible duplicado, se omite")
    void omiteNoAprobados() throws Exception {
        validarEjemplo();
        ImportacionResponse r = servicio.confirmar(1L, List.of(), actor);
        verify(productoService, times(1)).crear(any());
        assertThat(r.filas().get(2).estado()).isEqualTo("OMITIDA");
    }

    @Test @DisplayName("UT-IMP-06 un archivo idéntico ya importado no se vuelve a procesar")
    void reproceso() {
        when(importaciones.existsByTipoAndHashArchivoAndEstado(eq("productos"), any(), eq(EstadoImportacion.IMPORTADA)))
                .thenReturn(true);
        assertThatThrownBy(this::validarEjemplo)
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("ARCHIVO_YA_IMPORTADO"));
    }

    @Test @DisplayName("UT-IMP-07 una importación procesada no se confirma de nuevo")
    void yaProcesada() throws Exception {
        validarEjemplo();
        servicio.confirmar(1L, List.of(), actor);
        assertThatThrownBy(() -> servicio.confirmar(1L, List.of(), actor))
                .satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo("IMPORTACION_PROCESADA"));
    }

    @Test @DisplayName("UT-IMP-08 exportación: textos que empiezan por = + - @ se neutralizan (inyección de fórmulas)")
    void neutralizaFormulas() {
        assertThat(Excel.neutralizar("=HYPERLINK(\"x\")")).isEqualTo("'=HYPERLINK(\"x\")");
        assertThat(Excel.neutralizar("+57 300")).isEqualTo("'+57 300");
        assertThat(Excel.neutralizar("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(Excel.neutralizar("Taladro")).isEqualTo("Taladro");
        assertThat(Excel.neutralizar(null)).isNull();
    }

    // ---------------------------------------------------------------------------------------------
    // Casos para matar mutantes de PIT en ImportacionService (UT-IMP-09 … UT-IMP-21)
    // ---------------------------------------------------------------------------------------------

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 16, 15, 0);
    private static final DataFormatter FMT = new DataFormatter(Locale.ROOT);

    private static final List<String> ENC_PRODUCTOS = List.of("codigo_barras", "nombre", "marca", "presentacion",
            "unidad_base", "tipo_rastreo", "tiene_vencimiento", "tiene_garantia", "meses_garantia", "categoria",
            "descripcion", "porcentaje_iva", "porcentaje_incremento", "stock_minimo");

    private static final List<String> ENC_TERCEROS = List.of("tipo_persona", "tipo_identificacion",
            "numero_identificacion", "primer_nombre", "segundo_nombre", "primer_apellido", "segundo_apellido",
            "razon_social", "direccion", "ciudad", "telefono", "correo", "es_cliente", "es_proveedor");

    private static String codigo(Throwable e) {
        return ((ApiException) e).getCodigo();
    }

    private static String celda(Sheet s, int fila, int col) {
        return FMT.formatCellValue(s.getRow(fila).getCell(col));
    }

    private static XSSFWorkbook leer(byte[] b) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(b));
    }

    private static List<String> natural(String tipoId, String id, String nombre, String apellido) {
        return List.of("NATURAL", tipoId, id, nombre, "", apellido, "", "", "Cra 1 # 2-3", "Neiva", "3001112233",
                "x@correo.com", "SI", "NO");
    }

    private static List<String> juridica(String id, String razonSocial) {
        return List.of("JURIDICA", "NIT", id, "", "", "", "", razonSocial, "Calle 5", "Neiva", "3001112233",
                "y@correo.com", "NO", "SI");
    }

    /** Valida un archivo de terceros con todos los casos de clasificación. */
    private ImportacionResponse validarTerceros() throws Exception {
        when(terceros.findAll()).thenReturn(List.of(Tercero.builder().id(1L).tipoPersona(TipoPersona.JURIDICA)
                .numeroIdentificacion("900123456").razonSocial("Distribuidora SA").build()));
        return servicio.validar("terceros", "terceros.xlsx", libro(ENC_TERCEROS, List.of(
                natural("CC", "111", "Ana", "Gómez"),              // fila 2: válida
                juridica("900123456", "Otra SAS"),                 // fila 3: duplicado exacto (identificación)
                juridica("800", "Distribuidora S.A."),             // fila 4: posible duplicado contra la base
                natural("CC", "111", "Pedro", "Pérez"),            // fila 5: identificación repetida en el archivo
                natural("CE", "222", "Ana", "Gomez"),              // fila 6: posible duplicado dentro del archivo
                natural("CC", "333", "Luis", "Paz").stream().map(v -> v.equals("NATURAL") ? "OTRA" : v).toList(),  // fila 7
                natural("XX", "444", "Eva", "Ruiz"),               // fila 8: tipo de identificación inválido
                juridica("555", ""),                                // fila 9: sin razón social (nombre vacío)
                juridica("556", ""))),                              // fila 10: igual a la 9, no es posible duplicado
                actor);
    }

    @Test @DisplayName("UT-IMP-09 plantillas: encabezados, fila de ejemplo e instrucciones según el tipo; tipo inválido se rechaza")
    void plantillas() throws Exception {
        try (XSSFWorkbook wb = leer(servicio.plantilla("productos"))) {
            Sheet s = wb.getSheet("productos");
            assertThat(celda(s, 0, 0)).isEqualTo("codigo_barras");
            assertThat(celda(s, 1, 0)).isEqualTo("7701234599999");
            assertThat(celda(s, 1, 1)).isEqualTo("Martillo");
            Sheet ayuda = wb.getSheet("instrucciones");
            assertThat(ayuda.getLastRowNum()).isEqualTo(5);
            assertThat(celda(ayuda, 0, 0)).startsWith("Obligatorias: codigo_barras");
            assertThat(celda(ayuda, 5, 0)).contains("Texto");
        }
        try (XSSFWorkbook wb = leer(servicio.plantilla("terceros"))) {
            Sheet s = wb.getSheet("terceros");
            assertThat(celda(s, 0, 0)).isEqualTo("tipo_persona");
            assertThat(celda(s, 1, 0)).isEqualTo("NATURAL");
            Sheet ayuda = wb.getSheet("instrucciones");
            assertThat(ayuda.getLastRowNum()).isEqualTo(3);
            assertThat(celda(ayuda, 0, 0)).startsWith("Obligatorias: tipo_persona");
        }
        assertThatThrownBy(() -> servicio.plantilla("clientes"))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("TIPO_IMPORTACION_INVALIDO"));
    }

    @Test @DisplayName("UT-IMP-10 nombre del archivo: sin nombre usa archivo.xlsx, quita rutas y se recorta a 255; se guarda el SHA-256")
    void nombreYHash() throws Exception {
        byte[] b = libro(ENC, List.of(List.of("7701234599991", "Martillo", "Stanley", "16 oz", "unidad", "", "")));
        String esperado = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));

        ImportacionResponse r = servicio.validar("productos", null, b, actor);
        assertThat(r.nombreArchivo()).isEqualTo("archivo.xlsx");
        assertThat(guardada.getHashArchivo()).isEqualTo(esperado);
        verify(importaciones).existsByTipoAndHashArchivoAndEstado("productos", esperado, EstadoImportacion.IMPORTADA);
        org.mockito.Mockito.clearInvocations(importaciones);

        assertThat(servicio.validar("productos", "c:\\datos/productos.xlsx", b, actor).nombreArchivo())
                .isEqualTo("c:_datos_productos.xlsx");

        String largo = "a".repeat(300) + ".xlsx";
        servicio.validar("productos", largo, b, actor);
        assertThat(guardada.getNombreArchivo()).isEqualTo("a".repeat(255));

        assertThatThrownBy(() -> servicio.validar("productos", "p.xlsx", new byte[0], actor))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("ARCHIVO_VACIO"));
    }

    @Test @DisplayName("UT-IMP-11 límite de filas: 5000 filas de datos se aceptan y 5001 se rechazan")
    void limiteDeFilas() throws Exception {
        List<List<String>> filas = new ArrayList<>();
        for (int i = 0; i < ImportacionService.MAX_FILAS; i++) {
            filas.add(List.of(String.format("77%011d", i), "Producto " + i, "Marca", "1 u", "unidad", "", ""));
        }
        ImportacionResponse r = servicio.validar("productos", "grande.xlsx", libro(ENC, filas), actor);
        assertThat(r.totalFilas()).isEqualTo(ImportacionService.MAX_FILAS);

        filas.add(List.of("7799999999999", "Uno más", "Marca", "1 u", "unidad", "", ""));
        assertThatThrownBy(() -> servicio.validar("productos", "grande2.xlsx", libro(ENC, filas), actor))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("ARCHIVO_DEMASIADO_GRANDE"));
    }

    @Test @DisplayName("UT-IMP-12 los valores del archivo se convierten al ProductoRequest (rastreo, SI/NO, números, vacíos a null)")
    void conversionDeProductos() throws Exception {
        servicio.validar("productos", "completo.xlsx", libro(ENC_PRODUCTOS, List.of(
                List.of("7701234511111", "Lijadora", "Dewalt", "300W", "unidad", "lote", "sí", "X", "6", "Herramientas",
                        "  ", "19", "0,30", "5"),
                List.of("7701234522222", "Brocha", "Goya", "2in", "unidad", "", "no", "", "", "", "Brocha fina",
                        "", "", ""))), actor);
        servicio.confirmar(1L, List.of(), actor);

        ArgumentCaptor<ProductoRequest> c = ArgumentCaptor.forClass(ProductoRequest.class);
        verify(productoService, times(2)).crear(c.capture());
        ProductoRequest a = c.getAllValues().get(0);
        assertThat(a.codigoBarras()).isEqualTo("7701234511111");
        assertThat(a.nombre()).isEqualTo("Lijadora");
        assertThat(a.tipoRastreo()).isEqualTo(TipoRastreo.LOTE);
        assertThat(a.tieneVencimiento()).isTrue();
        assertThat(a.tieneGarantia()).isTrue();
        assertThat(a.mesesGarantia()).isEqualTo(6);
        assertThat(a.categoria()).isEqualTo("Herramientas");
        assertThat(a.descripcion()).isNull();
        assertThat(a.porcentajeIva()).isEqualByComparingTo("19");
        assertThat(a.porcentajeIncremento()).isEqualByComparingTo("0.30");
        assertThat(a.stockMinimo()).isEqualByComparingTo("5");

        ProductoRequest b = c.getAllValues().get(1);
        assertThat(b.tipoRastreo()).isEqualTo(TipoRastreo.NINGUNO);
        assertThat(b.tieneVencimiento()).isFalse();
        assertThat(b.tieneGarantia()).isFalse();
        assertThat(b.mesesGarantia()).isNull();
        assertThat(b.categoria()).isNull();
        assertThat(b.descripcion()).isEqualTo("Brocha fina");
        assertThat(b.porcentajeIva()).isNull();
    }

    @Test @DisplayName("UT-IMP-13 valores inválidos: SI/NO, entero y número se reportan en su columna con un mensaje claro")
    void valoresInvalidos() throws Exception {
        ImportacionResponse r = servicio.validar("productos", "malos.xlsx", libro(ENC_PRODUCTOS, List.of(
                List.of("7701234533331", "A", "M", "1", "unidad", "", "TALVEZ", "", "", "", "", "", "", ""),
                List.of("7701234533332", "B", "M", "1", "unidad", "", "", "SI", "6.5", "", "", "", "", ""),
                List.of("7701234533333", "C", "M", "1", "unidad", "", "", "", "", "", "", "abc", "", ""),
                List.of("7701234533334", "D", "M", "1", "unidad", "RASTRO", "", "", "", "", "", "", "", ""))), actor);
        assertThat(r.filas()).extracting(ImportacionResponse.Fila::estado).containsOnly("ERROR");
        assertThat(r.filas().get(0).errores()).containsExactly(
                new ImportacionResponse.ErrorCelda("tiene_vencimiento", "Use SI o NO"));
        assertThat(r.filas().get(1).errores()).containsExactly(
                new ImportacionResponse.ErrorCelda("meses_garantia", "Debe ser un número entero"));
        assertThat(r.filas().get(2).errores()).containsExactly(
                new ImportacionResponse.ErrorCelda("porcentaje_iva", "Debe ser un número"));
        assertThat(r.filas().get(3).errores()).containsExactly(
                new ImportacionResponse.ErrorCelda("tipo_rastreo", "Use NINGUNO, LOTE o SERIE"));
    }

    @Test @DisplayName("UT-IMP-14 los errores de Bean Validation se traducen a la columna del archivo y se ordenan por columna")
    @SuppressWarnings("unchecked")
    void violacionesOrdenadas() throws Exception {
        ConstraintViolation<Object> porNombre = mock(ConstraintViolation.class);
        ConstraintViolation<Object> porCodigo = mock(ConstraintViolation.class);
        jakarta.validation.Path rutaNombre = mock(jakarta.validation.Path.class);
        jakarta.validation.Path rutaCodigo = mock(jakarta.validation.Path.class);
        when(rutaNombre.toString()).thenReturn("nombre");
        when(rutaCodigo.toString()).thenReturn("codigoBarras");
        when(porNombre.getPropertyPath()).thenReturn(rutaNombre);
        when(porNombre.getMessage()).thenReturn("Es obligatorio");
        when(porCodigo.getPropertyPath()).thenReturn(rutaCodigo);
        when(porCodigo.getMessage()).thenReturn("Formato inválido");
        Set<ConstraintViolation<Object>> vs = new LinkedHashSet<>(List.of(porNombre, porCodigo));
        doReturn(vs).when(validador).validate(any());

        ImportacionResponse r = servicio.validar("productos", "v.xlsx", libro(ENC, List.of(
                List.of("7701234599991", "Martillo", "Stanley", "16 oz", "unidad", "", ""))), actor);

        assertThat(r.filas().get(0).estado()).isEqualTo("ERROR");
        assertThat(r.filas().get(0).errores()).containsExactly(
                new ImportacionResponse.ErrorCelda("codigo_barras", "Formato inválido"),
                new ImportacionResponse.ErrorCelda("nombre", "Es obligatorio"));
    }

    @Test @DisplayName("UT-IMP-15 confirmar registra la fecha de proceso, guarda el resultado y audita importadas y omitidas")
    @SuppressWarnings("unchecked")
    void confirmarAuditaYGuarda() throws Exception {
        validarEjemplo();
        when(importaciones.findById(1L)).thenAnswer(a -> Optional.ofNullable(guardada));
        ImportacionResponse r = servicio.confirmar(1L, List.of(4), actor);

        assertThat(r.fechaProceso()).isEqualTo(AHORA);
        assertThat(servicio.obtener(1L).filas()).extracting(ImportacionResponse.Fila::estado)
                .containsExactly("IMPORTADA", "DUPLICADO", "IMPORTADA", "ERROR", "ERROR");
        ArgumentCaptor<Map<String, Object>> d = ArgumentCaptor.forClass(Map.class);
        verify(eventos).registrar(any(), eq(Auditables.IMPORTACION), eq(Auditables.IMPORTAR), eq("IMPORTACION"), any(),
                any(), d.capture());
        assertThat(d.getValue()).containsEntry("importadas", 2).containsEntry("omitidas", 3)
                .containsEntry("archivo", "productos.xlsx");
    }

    @Test @DisplayName("UT-IMP-16 al confirmar se revalida la unicidad: código o referencia ya existentes se omiten con su motivo")
    void confirmarRevalidaProductos() throws Exception {
        validarEjemplo();
        when(productos.findByCodigoBarras("7701234599991")).thenReturn(Optional.of(Producto.builder().id(9L).build()));
        when(productos.findByNombreIgnoreCaseAndMarcaIgnoreCaseAndPresentacionIgnoreCase("Taladro", "Bosch", "500 W"))
                .thenReturn(Optional.of(Producto.builder().id(8L).build()));

        ImportacionResponse r = servicio.confirmar(1L, List.of(4), actor);

        verify(productoService, never()).crear(any());
        assertThat(r.filasImportadas()).isZero();
        assertThat(r.filas().get(0).estado()).isEqualTo("OMITIDA");
        assertThat(r.filas().get(0).errores()).contains(
                new ImportacionResponse.ErrorCelda("codigo_barras", "El código ya existe en el catálogo"));
        assertThat(r.filas().get(2).estado()).isEqualTo("OMITIDA");
        assertThat(r.filas().get(2).errores()).contains(
                new ImportacionResponse.ErrorCelda("codigo_barras", "La referencia producto + marca + presentación ya existe"));
    }

    @Test @DisplayName("UT-IMP-17 terceros: válida, duplicado exacto, posibles duplicados (base y archivo) y errores de valor")
    void validacionDeTerceros() throws Exception {
        ImportacionResponse r = validarTerceros();

        assertThat(r.filas()).extracting(ImportacionResponse.Fila::estado).containsExactly(
                "VALIDA", "DUPLICADO", "POSIBLE_DUPLICADO", "ERROR", "POSIBLE_DUPLICADO", "ERROR", "ERROR", "VALIDA", "VALIDA");
        assertThat(r.filas().get(1).coincidencia()).isEqualTo("Distribuidora SA");
        assertThat(r.filas().get(2).coincidencia()).isEqualTo("Distribuidora SA [900123456]");
        assertThat(r.filas().get(3).errores()).containsExactly(new ImportacionResponse.ErrorCelda("numero_identificacion",
                "Identificación repetida en el archivo (fila 2)"));
        assertThat(r.filas().get(4).coincidencia()).isEqualTo("Fila 2 del archivo");
        assertThat(r.filas().get(5).errores()).containsExactly(
                new ImportacionResponse.ErrorCelda("tipo_persona", "Use NATURAL o JURIDICA"));
        assertThat(r.filas().get(6).errores()).containsExactly(
                new ImportacionResponse.ErrorCelda("tipo_identificacion", "Use CC, NIT, CE o PASAPORTE"));
        assertThat(r.filas().get(8).coincidencia()).isNull();
        assertThat(r.filasValidas()).isEqualTo(3);
        assertThat(r.filasError()).isEqualTo(3);
        assertThat(r.filasDuplicadas()).isEqualTo(3);
    }

    @Test @DisplayName("UT-IMP-18 confirmar terceros convierte los valores y omite la identificación que ya existe")
    void confirmarTerceros() throws Exception {
        validarTerceros();
        when(terceros.findByNumeroIdentificacion("111")).thenReturn(Optional.of(Tercero.builder().id(5L).build()));

        ImportacionResponse r = servicio.confirmar(1L, List.of(4, 6), actor);

        assertThat(r.filas()).extracting(ImportacionResponse.Fila::estado).containsExactly(
                "OMITIDA", "DUPLICADO", "IMPORTADA", "ERROR", "IMPORTADA", "ERROR", "ERROR", "IMPORTADA", "IMPORTADA");
        assertThat(r.filas().get(0).errores()).containsExactly(
                new ImportacionResponse.ErrorCelda("numero_identificacion", "La identificación ya existe"));
        assertThat(r.filasImportadas()).isEqualTo(4);

        ArgumentCaptor<TerceroRequest> c = ArgumentCaptor.forClass(TerceroRequest.class);
        verify(terceroService, times(4)).crear(c.capture());
        TerceroRequest empresa = c.getAllValues().get(0);
        assertThat(empresa.tipoPersona()).isEqualTo(TipoPersona.JURIDICA);
        assertThat(empresa.tipoIdentificacion()).isEqualTo(TipoIdentificacion.NIT);
        assertThat(empresa.numeroIdentificacion()).isEqualTo("800");
        assertThat(empresa.razonSocial()).isEqualTo("Distribuidora S.A.");
        assertThat(empresa.primerNombre()).isNull();
        assertThat(empresa.cliente()).isFalse();
        assertThat(empresa.proveedor()).isTrue();
        TerceroRequest persona = c.getAllValues().get(1);
        assertThat(persona.tipoPersona()).isEqualTo(TipoPersona.NATURAL);
        assertThat(persona.tipoIdentificacion()).isEqualTo(TipoIdentificacion.CE);
        assertThat(persona.primerNombre()).isEqualTo("Ana");
        assertThat(persona.segundoNombre()).isNull();
        assertThat(persona.primerApellido()).isEqualTo("Gomez");
        assertThat(persona.cliente()).isTrue();
        assertThat(persona.proveedor()).isFalse();
    }

    @Test @DisplayName("UT-IMP-19 cancelar deja la importación CANCELADA con fecha y no se puede cancelar dos veces")
    void cancelar() throws Exception {
        validarEjemplo();
        ImportacionResponse r = servicio.cancelar(1L, actor);
        assertThat(r).isNotNull();
        assertThat(r.estado()).isEqualTo(EstadoImportacion.CANCELADA);
        assertThat(r.fechaProceso()).isEqualTo(AHORA);
        assertThat(r.filas()).hasSize(5);
        assertThat(guardada.getEstado()).isEqualTo(EstadoImportacion.CANCELADA);
        verify(productoService, never()).crear(any());
        assertThatThrownBy(() -> servicio.cancelar(1L, actor))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("IMPORTACION_PROCESADA"));
        assertThatThrownBy(() -> servicio.confirmar(1L, List.of(), actor))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("IMPORTACION_PROCESADA"));
    }

    @Test @DisplayName("UT-IMP-20 obtener, tipo e historial; una importación inexistente responde 404")
    void consultas() throws Exception {
        validarEjemplo();
        when(importaciones.findById(1L)).thenAnswer(a -> Optional.ofNullable(guardada));
        when(importaciones.findAllByOrderByIdDesc(any(Pageable.class)))
                .thenAnswer(a -> new PageImpl<>(List.of(guardada), a.getArgument(0), 1));

        ImportacionResponse o = servicio.obtener(1L);
        assertThat(o).isNotNull();
        assertThat(o.filas()).hasSize(5);
        assertThat(servicio.tipo(1L)).isEqualTo("productos");

        PaginaResponse<ImportacionResponse> h = servicio.historial(-1, 500);
        assertThat(h).isNotNull();
        assertThat(h.pagina()).isZero();
        assertThat(h.tamano()).isEqualTo(100);
        assertThat(h.contenido()).hasSize(1);
        assertThat(h.contenido().get(0).id()).isEqualTo(1L);
        assertThat(h.contenido().get(0).filas()).isEmpty();

        assertThatThrownBy(() -> servicio.obtener(404L)).satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
        assertThatThrownBy(() -> servicio.tipo(404L)).satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
        assertThatThrownBy(() -> servicio.confirmar(404L, List.of(), actor))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
        assertThatThrownBy(() -> servicio.cancelar(404L, actor))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
        assertThatThrownBy(() -> servicio.informeErrores(404L))
                .satisfies(e -> assertThat(codigo(e)).isEqualTo("NO_ENCONTRADO"));
    }

    @Test @DisplayName("UT-IMP-21 el informe de errores lista solo filas con errores o coincidencias, una fila por error")
    void informeDeErrores() throws Exception {
        validarEjemplo();
        when(importaciones.findById(1L)).thenAnswer(a -> Optional.ofNullable(guardada));

        try (XSSFWorkbook wb = leer(servicio.informeErrores(1L))) {
            Sheet s = wb.getSheet("errores");
            assertThat(celda(s, 0, 0)).isEqualTo("fila");
            assertThat(celda(s, 0, 5)).isEqualTo("codigo_barras");
            assertThat(s.getLastRowNum()).isEqualTo(4);   // filas 3, 4, 5 y 6 (la 2 es válida y no aparece)

            assertThat(celda(s, 1, 0)).isEqualTo("3");
            assertThat(celda(s, 1, 1)).isEqualTo("DUPLICADO");
            assertThat(celda(s, 1, 2)).isEmpty();
            assertThat(celda(s, 1, 4)).isEqualTo("Taladro Bosch 500W");
            assertThat(celda(s, 1, 5)).isEqualTo("7701234500015");

            assertThat(celda(s, 3, 0)).isEqualTo("5");
            assertThat(celda(s, 3, 1)).isEqualTo("ERROR");
            assertThat(celda(s, 3, 2)).isEqualTo("tipo_rastreo");
            assertThat(celda(s, 3, 4)).isEmpty();
            assertThat(celda(s, 4, 3)).contains("Código repetido en el archivo (fila 2)");
        }
    }
}