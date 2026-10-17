/*
 * Nombre: ConteoController.java
 * Descripción: API REST de conteos físicos por sesión (RF-15).
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
 * Conteos: Inventario/Administrador cuentan; Gerencia/Administrador (otro usuario) aprueban.
 */
@RestController
@RequestMapping("/api/conteos")
@RequiredArgsConstructor
@Validated
@Tag(name = "Conteos físicos", description = "Sesiones de conteo, diferencias y aprobación")
public class ConteoController {

    private static final String CONTAR = "hasAnyRole('INVENTARIO','ADMINISTRADOR')";
    private static final String DECIDIR = "hasAnyRole('GERENCIA','ADMINISTRADOR')";

    private final ConteoService servicio;
    private final ContextoSolicitud contexto;

    /** Abre una sesión. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CONTAR)
    @Operation(summary = "Abrir sesión de conteo")
    public SesionConteoResponse abrir(@Valid @RequestBody(required = false) SesionConteoRequest req) {
        return servicio.abrir(req, contexto.actor());
    }

    /** Historial. */
    @GetMapping
    @PreAuthorize("hasAnyRole('INVENTARIO','ADMINISTRADOR','GERENCIA')")
    @Operation(summary = "Listar sesiones de conteo")
    public PaginaResponse<SesionConteoResponse> listar(
            @RequestParam(required = false) com.ferreteria.inventario.domain.enums.EstadoSesionConteo estado,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return servicio.listar(estado, pagina, tamano);
    }

    /** Detalle. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('INVENTARIO','ADMINISTRADOR','GERENCIA')")
    @Operation(summary = "Consultar sesión de conteo")
    public SesionConteoResponse obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    /** Registra el conteo de un producto. */
    @PostMapping("/{id}/registros")
    @PreAuthorize(CONTAR)
    @Operation(summary = "Registrar cantidad contada", description = "Compara contra el saldo disponible actual; no modifica el inventario.")
    public SesionConteoResponse registrar(@PathVariable Long id, @Valid @RequestBody ConteoRegistroRequest req) {
        return servicio.registrar(id, req, contexto.actor());
    }

    /** Cierra la sesión para aprobación. */
    @PostMapping("/{id}/cerrar")
    @PreAuthorize(CONTAR)
    @Operation(summary = "Cerrar sesión para aprobación")
    public SesionConteoResponse cerrar(@PathVariable Long id) {
        return servicio.cerrar(id, contexto.actor());
    }

    /** Aprueba: genera ajustes por las diferencias. */
    @PostMapping("/{id}/aprobar")
    @PreAuthorize(DECIDIR)
    @Operation(summary = "Aprobar conteo", description = "Cada diferencia se convierte en un ajuste aplicado.")
    public SesionConteoResponse aprobar(@PathVariable Long id) {
        return servicio.aprobar(id, contexto.actor());
    }

    /** Rechaza. */
    @PostMapping("/{id}/rechazar")
    @PreAuthorize(DECIDIR)
    @Operation(summary = "Rechazar conteo")
    public SesionConteoResponse rechazar(@PathVariable Long id, @Valid @RequestBody MotivoRequest req) {
        return servicio.rechazar(id, req.motivo(), contexto.actor());
    }
}
