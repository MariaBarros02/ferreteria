/*
 * Nombre: Excel.java
 * Descripción: Utilidades de Apache POI: lectura segura de celdas como texto, creación de libros con encabezado y neutralización de fórmulas (inyección CSV/Excel).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

/**
 * Utilidades de Excel. Toda cadena exportada pasa por {@link #neutralizar(String)}: si empieza por
 * {@code = + - @}, tabulador o retorno de carro se antepone un apóstrofo para que ninguna hoja de cálculo la
 * interprete como fórmula (OWASP "CSV injection").
 */
public final class Excel {

    /** Tipo MIME de los libros .xlsx. */
    public static final String MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Excel() { }

    /**
     * Neutraliza fórmulas en un texto exportado.
     *
     * @param s texto
     * @return texto seguro
     */
    public static String neutralizar(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        char c = s.charAt(0);
        return (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') ? "'" + s : s;
    }

    /**
     * Lee una celda como texto recortado; los números se devuelven sin notación científica ni ceros sobrantes.
     *
     * @param cell celda (puede ser nula)
     * @param fmt formateador
     * @return texto o cadena vacía
     */
    public static String texto(Cell cell, DataFormatter fmt) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        if (cell.getCellType() == CellType.FORMULA) {
            return "";   // nunca se evalúan fórmulas de archivos externos
        }
        return fmt.formatCellValue(cell).trim();
    }

    /**
     * Crea un libro en modo streaming con una hoja y encabezado en negrita.
     *
     * @param hoja nombre de la hoja
     * @param encabezados encabezados
     * @return libro listo para agregar filas a partir de la fila 1
     */
    public static SXSSFWorkbook libro(String hoja, List<String> encabezados) {
        SXSSFWorkbook wb = new SXSSFWorkbook(200);
        Sheet s = wb.createSheet(hoja);
        CellStyle negrita = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        negrita.setFont(f);
        Row r = s.createRow(0);
        for (int i = 0; i < encabezados.size(); i++) {
            Cell c = r.createCell(i);
            c.setCellValue(encabezados.get(i));
            c.setCellStyle(negrita);
            s.setColumnWidth(i, Math.min(60, Math.max(12, encabezados.get(i).length() + 4)) * 256);
        }
        s.createFreezePane(0, 1);
        return wb;
    }

    /**
     * Escribe una fila de valores tipados (texto neutralizado, números y fechas como texto ISO).
     *
     * @param s hoja
     * @param indice número de fila
     * @param valores valores
     */
    public static void fila(Sheet s, int indice, List<?> valores) {
        Row r = s.createRow(indice);
        for (int i = 0; i < valores.size(); i++) {
            Object v = valores.get(i);
            Cell c = r.createCell(i);
            if (v == null) {
                c.setCellValue((String) null);   // celda en blanco
            } else if (v instanceof BigDecimal b) {
                c.setCellValue(b.doubleValue());
            } else if (v instanceof Number n) {
                c.setCellValue(n.doubleValue());
            } else if (v instanceof Boolean b) {
                c.setCellValue(b ? "SI" : "NO");
            } else if (v instanceof LocalDateTime t) {
                c.setCellValue(t.format(FECHA_HORA));
            } else if (v instanceof LocalDate d) {
                c.setCellValue(d.format(FECHA));
            } else {
                c.setCellValue(neutralizar(v.toString()));
            }
        }
    }

    /**
     * Serializa y libera el libro.
     *
     * @param wb libro
     * @return bytes .xlsx
     */
    public static byte[] bytes(Workbook wb) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            if (wb instanceof SXSSFWorkbook sx) {
                sx.dispose();
            }
            wb.close();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
