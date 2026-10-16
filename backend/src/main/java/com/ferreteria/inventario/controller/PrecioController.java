/*
 * Nombre: PrecioController.java
 * Descripción: API REST de precios (RF-07, RF-16): propuesta, aprobación, rechazo e historial.
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
 * Precios: Inventario y Administrador proponen; solo Gerencia aprueba o rechaza (matriz RF-09).
 */
@RestController
@RequestMapping("/api/precios")
@RequiredArgsConstructor
@Validated
@Tag(name = "Precios", description = "Cálculo, propuesta y aprobación de precios")
public class PrecioController {

    private static final String LECTURA = "hasAnyRole('INVENTARIO','ADMINISTRADOR','GERENCIA')";

    private final PrecioService servicio;
    private final ContextoSolicitud contexto;

    /** Propone un precio. */
    @PostMapping("/propuestas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('INVENTARIO','ADMINISTRADOR')")
    @Operation(summary = "Proponer precio", description = "Recargo: P = C × (1 + r) (RN-07). Margen: P = C / (1 − m). "
            + "Manual: precio indicado con justificación. La propuesta no cambia el precio de venta.")
    public PrecioResponse proponer(@Valid @RequestBody PrecioPropuestaRequest req) {
        return servicio.proponer(req, contexto.actor());
    }

    /** Propuestas por estado (por defecto, pendientes). */
    @GetMapping("/propuestas")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Listar propuestas por estado")
    public PaginaResponse<PrecioResponse> propuestas(
            @RequestParam(defaultValue = "propuesto") @jakarta.validation.constraints.Pattern(regexp = "propuesto|aprobado|rechazado") String estado,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return servicio.porEstado(estado, pagina, tamano);
    }

    /** Aprueba una propuesta. */
    @PostMapping("/propuestas/{id}/aprobar")
    @PreAuthorize("hasRole('GERENCIA')")
    @Operation(summary = "Aprobar precio", description = "El precio pasa a ser vigente. Quien propone no puede aprobar.")
    public PrecioResponse aprobar(@PathVariable Long id) {
        return servicio.aprobar(id, contexto.actor());
    }

    /** Rechaza una propuesta. */
    @PostMapping("/propuestas/{id}/rechazar")
    @PreAuthorize("hasRole('GERENCIA')")
    @Operation(summary = "Rechazar precio")
    public PrecioResponse rechazar(@PathVariable Long id, @Valid @RequestBody MotivoRequest req) {
        return servicio.rechazar(id, req.motivo(), contexto.actor());
    }

    /** Historial de precios de un producto. */
    @GetMapping("/productos/{idProducto}")
    @PreAuthorize(LECTURA)
    @Operation(summary = "Historial de precios de un producto")
    public PaginaResponse<PrecioResponse> historial(@PathVariable Long idProducto,
                                                    @RequestParam(defaultValue = "0") @Min(0) int pagina,
                                                    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return servicio.historial(idProducto, pagina, tamano);
    }
}
