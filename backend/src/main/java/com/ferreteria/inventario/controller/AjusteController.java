/*
 * Nombre: AjusteController.java
 * Descripción: API REST de ajustes de inventario (RF-14): solicitud, aprobación, rechazo y consulta.
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
 * Ajustes: Inventario/Administrador solicitan; Gerencia/Administrador (otro usuario) aprueban.
 */
@RestController
@RequestMapping("/api/ajustes")
@RequiredArgsConstructor
@Validated
@Tag(name = "Ajustes", description = "Ajustes de inventario con aprobación")
public class AjusteController {

    private final AjusteService servicio;
    private final ContextoSolicitud contexto;

    /** Solicita un ajuste (queda pendiente). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('INVENTARIO','ADMINISTRADOR')")
    @Operation(summary = "Solicitar ajuste", description = "No modifica el saldo hasta su aprobación.")
    public AjusteResponse solicitar(@Valid @RequestBody AjusteRequest req) {
        return servicio.solicitar(req, contexto.actor());
    }

    /** Lista ajustes. */
    @GetMapping
    @PreAuthorize("hasAnyRole('INVENTARIO','ADMINISTRADOR','GERENCIA')")
    @Operation(summary = "Listar ajustes")
    public PaginaResponse<AjusteResponse> listar(
            @RequestParam(required = false) com.ferreteria.inventario.domain.enums.EstadoAprobacion estado,
            @RequestParam(required = false) Long productoId,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return servicio.listar(estado, productoId, pagina, tamano);
    }

    /** Detalle. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('INVENTARIO','ADMINISTRADOR','GERENCIA')")
    @Operation(summary = "Consultar ajuste")
    public AjusteResponse obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    /** Aprueba y aplica. */
    @PostMapping("/{id}/aprobar")
    @PreAuthorize("hasAnyRole('GERENCIA','ADMINISTRADOR')")
    @Operation(summary = "Aprobar ajuste", description = "Aplica el ajuste de forma atómica; el aprobador debe ser distinto del solicitante.")
    public AjusteResponse aprobar(@PathVariable Long id) {
        return servicio.aprobar(id, contexto.actor());
    }

    /** Rechaza. */
    @PostMapping("/{id}/rechazar")
    @PreAuthorize("hasAnyRole('GERENCIA','ADMINISTRADOR')")
    @Operation(summary = "Rechazar ajuste")
    public AjusteResponse rechazar(@PathVariable Long id, @Valid @RequestBody MotivoRequest req) {
        return servicio.rechazar(id, req.motivo(), contexto.actor());
    }
}
