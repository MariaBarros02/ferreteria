/*
 * Nombre: Descargas.java
 * Descripción: Construcción de respuestas de descarga de archivos (Content-Disposition, tipo MIME y sin caché).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.controller;

import com.ferreteria.inventario.excel.Excel;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Respuestas de archivo.
 */
final class Descargas {

    private Descargas() { }

    /**
     * Archivo .xlsx como adjunto.
     *
     * @param nombre nombre del archivo
     * @param contenido bytes
     * @return respuesta HTTP
     */
    static ResponseEntity<byte[]> xlsx(String nombre, byte[] contenido) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nombre, StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(Excel.MIME))
                .contentLength(contenido.length)
                .body(contenido);
    }
}
