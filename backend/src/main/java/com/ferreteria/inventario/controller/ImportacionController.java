/*
 * Nombre: ImportacionController.java
 * Descripción: API REST de migración desde Excel (RF-10): plantillas, validación, revisión, confirmación e informe de errores.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.controller;

import com.ferreteria.inventario.dto.*;
import com.ferreteria.inventario.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Productos: Inventario/Administrador. Terceros: Compras/Ventas/Administrador.
 */
@RestController
@RequestMapping("/api/importaciones")
@RequiredArgsConstructor
@Validated
@Tag(name = "Importación", description = "Migración validada desde Excel (.xlsx)")
public class ImportacionController {

    private static final String POR_TIPO = "(#tipo == 'productos' and hasAnyRole('INVENTARIO','ADMINISTRADOR'))"
            + " or (#tipo == 'terceros' and hasAnyRole('COMPRAS','VENTAS','ADMINISTRADOR'))";
    private static final String ALGUNO = "hasAnyRole('INVENTARIO','COMPRAS','VENTAS','ADMINISTRADOR')";

    private final ImportacionService servicio;
    private final ContextoSolicitud contexto;

    /** Plantilla del tipo indicado. */
    @GetMapping("/plantillas/{tipo}")
    @PreAuthorize(POR_TIPO)
    @Operation(summary = "Descargar plantilla .xlsx")
    public ResponseEntity<byte[]> plantilla(@PathVariable @jakarta.validation.constraints.Pattern(regexp = "productos|terceros") String tipo) {
        return Descargas.xlsx("plantilla-" + tipo + ".xlsx", servicio.plantilla(tipo));
    }

    /** Valida un archivo (no importa). */
    @PostMapping(value = "/{tipo}/validar", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(POR_TIPO)
    @Operation(summary = "Validar archivo", description = "Valida formato, columnas, campos, duplicados y posibles duplicados. "
            + "No importa nada hasta confirmar.")
    public ImportacionResponse validar(@PathVariable @jakarta.validation.constraints.Pattern(regexp = "productos|terceros") String tipo,
                                       @RequestPart("archivo") org.springframework.web.multipart.MultipartFile archivo)
            throws java.io.IOException {
        return servicio.validar(tipo, archivo.getOriginalFilename(), archivo.getBytes(), contexto.actor());
    }

    /** Historial. */
    @GetMapping
    @PreAuthorize(ALGUNO)
    @Operation(summary = "Historial de importaciones")
    public PaginaResponse<ImportacionResponse> historial(@RequestParam(defaultValue = "0") @Min(0) int pagina,
                                                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return servicio.historial(pagina, tamano);
    }

    /** Detalle por fila. */
    @GetMapping("/{id}")
    @PreAuthorize(ALGUNO)
    @Operation(summary = "Consultar importación")
    public ImportacionResponse obtener(@PathVariable Long id) {
        autorizar(id);
        return servicio.obtener(id);
    }

    /** Confirma e importa. */
    @PostMapping("/{id}/confirmar")
    @PreAuthorize(ALGUNO)
    @Operation(summary = "Confirmar importación", description = "Importa en una sola transacción las filas válidas y los "
            + "posibles duplicados aprobados; nunca filas con errores ni duplicados exactos.")
    public ImportacionResponse confirmar(@PathVariable Long id, @Valid @RequestBody ConfirmacionImportacionRequest req) {
        autorizar(id);
        return servicio.confirmar(id, req.importarPosiblesDuplicados(), contexto.actor());
    }

    /** Cancela. */
    @PostMapping("/{id}/cancelar")
    @PreAuthorize(ALGUNO)
    @Operation(summary = "Cancelar importación")
    public ImportacionResponse cancelar(@PathVariable Long id) {
        autorizar(id);
        return servicio.cancelar(id, contexto.actor());
    }

    /** Informe de errores en Excel. */
    @GetMapping("/{id}/errores")
    @PreAuthorize(ALGUNO)
    @Operation(summary = "Descargar informe de errores (.xlsx)")
    public ResponseEntity<byte[]> errores(@PathVariable Long id) {
        autorizar(id);
        return Descargas.xlsx("importacion-" + id + "-errores.xlsx", servicio.informeErrores(id));
    }

    private void autorizar(Long id) {
        String tipo = servicio.tipo(id);
        boolean ok = ImportacionService.PRODUCTOS.equals(tipo)
                ? contexto.tieneRol("INVENTARIO", "ADMINISTRADOR")
                : contexto.tieneRol("COMPRAS", "VENTAS", "ADMINISTRADOR");
        if (!ok) {
            throw new org.springframework.security.access.AccessDeniedException("importacion");
        }
    }
}
