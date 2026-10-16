/*
 * Nombre: CompraController.java
 * Descripción: API REST de compras y recepción de mercancía (RF-03, RF-17).
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
 * Compras: Compras/Inventario/Administrador registran; la recepción la hacen Compras/Inventario/Administrador; consultan además Ventas y Gerencia.
 */
@RestController
@RequestMapping("/api/compras")
@RequiredArgsConstructor
@Validated
@Tag(name = "Compras", description = "Compras a proveedores, recepciones parciales y documentos de ingreso")
public class CompraController {

    private static final String LECTURA = "hasAnyRole('VENTAS','COMPRAS','INVENTARIO','ADMINISTRADOR','GERENCIA')";
    private static final String ESCRITURA = "hasAnyRole('COMPRAS','INVENTARIO','ADMINISTRADOR')";

    private final CompraService servicio;
    private final ContextoSolicitud contexto;

    /** Registra una compra (lo pedido). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(ESCRITURA)
    @Operation(summary = "Registrar compra", description = "No modifica el inventario hasta la recepción (RF-03).")
    public CompraResponse crear(@Valid @RequestBody CompraRequest req) {
        return servicio.crear(req, contexto.actor());
    }

    /** Listado con filtros. */
    @GetMapping
    @PreAuthorize(LECTURA)
    @Operation(summary = "Listar compras")
    public PaginaResponse<CompraResumenResponse> listar(
            @RequestParam(required = false) @Size(max = 30) String q,
            @RequestParam(required = false) Long proveedorId,
            @RequestParam(required = false) com.ferreteria.inventario.domain.enums.EstadoDocumento estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return servicio.listar(new CompraService.Filtros(q, proveedorId, estado, desde, hasta), pagina, tamano);
    }

    /** Detalle con avance de recepción y documentos de ingreso. */
    @GetMapping("/{id}")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Consultar compra")
    public CompraResponse obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    /** Registra una recepción parcial o total. */
    @PostMapping("/{id}/recepciones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(ESCRITURA)
    @Operation(summary = "Recibir mercancía", description = "Genera un documento de ingreso; aumenta el inventario solo "
            + "por lo recibido, registra lote y vencimiento y actualiza el costo promedio (RF-17, RN-07).")
    public CompraResponse recibir(@PathVariable Long id, @Valid @RequestBody RecepcionRequest req) {
        return servicio.recibir(id, req, contexto.actor());
    }

    /** Anula una compra sin recepciones. */
    @PostMapping("/{id}/anular")
    @PreAuthorize("hasAnyRole('COMPRAS','ADMINISTRADOR')")
    @Operation(summary = "Anular compra sin recepciones")
    public CompraResponse anular(@PathVariable Long id, @Valid @RequestBody MotivoRequest req) {
        return servicio.anular(id, req.motivo(), contexto.actor());
    }

    /** Anula un documento de ingreso (revierte la recepción con nota de ajuste). */
    @PostMapping("/ingresos/{idDocumento}/anular")
    @PreAuthorize("hasAnyRole('INVENTARIO','ADMINISTRADOR')")
    @Operation(summary = "Anular documento de ingreso", description = "Conserva el consecutivo (RN-09) y registra "
            + "movimientos de reversión vinculados a los originales (RN-16). Falla si la mercancía ya salió.")
    public CompraResponse anularIngreso(@PathVariable Long idDocumento, @Valid @RequestBody MotivoRequest req) {
        return servicio.anularIngreso(idDocumento, req.motivo(), contexto.actor());
    }
}
