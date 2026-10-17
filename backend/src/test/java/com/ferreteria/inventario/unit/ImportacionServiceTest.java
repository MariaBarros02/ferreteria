/*
 * Nombre: ImportacionServiceTest.java
 * Descripción: Pruebas unitarias de la migración desde Excel (RF-10, RN-15): columnas, errores por fila, duplicados exactos y posibles, confirmación selectiva, reproceso del mismo archivo y neutralización de fórmulas.
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferreteria.inventario.config.ParametrosNegocio;
import com.ferreteria.inventario.domain.enums.EstadoImportacion;
import com.ferreteria.inventario.domain.model.Importacion;
import com.ferreteria.inventario.domain.model.Producto;
import com.ferreteria.inventario.dto.ImportacionResponse;
import com.ferreteria.inventario.excel.Excel;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.*;
import com.ferreteria.inventario.service.*;
import jakarta.validation.Validator;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Casos UT-IMP-01 … UT-IMP-08. */
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
}
