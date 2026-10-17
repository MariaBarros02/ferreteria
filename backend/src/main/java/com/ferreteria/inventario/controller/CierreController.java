/*
 * Nombre: CierreController.java
 * Descripción: API REST de cierres mensuales (RF-08).
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
 * Cierres: solo Gerencia / responsable de cierre (matriz RF-09).
 */
@RestController
@RequestMapping("/api/cierres")
@RequiredArgsConstructor
@Validated
@Tag(name = "Cierres", description = "Cierre y reapertura de períodos mensuales")
public class CierreController {

    private final CierreService servicio;
    private final ContextoSolicitud contexto;

    /** Resumen del período. */
    @GetMapping("/resumen")
    @PreAuthorize("hasRole('GERENCIA')")
    @Operation(summary = "Resumen del período", description = "Totales, movimientos, conciliación y pendientes. periodo = AAAA-MM.")
    public CierreResumenResponse resumen(@RequestParam @jakarta.validation.constraints.Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$") String periodo) {
        return servicio.resumen(periodo);
    }

    /** Historial. */
    @GetMapping
    @PreAuthorize("hasRole('GERENCIA')")
    @Operation(summary = "Historial de cierres")
    public List<CierreResponse> listar() {
        return servicio.listar();
    }

    /** Cierra un período. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('GERENCIA')")
    @Operation(summary = "Cerrar período", description = "Rechaza si hay ajustes o conteos sin decidir; exige observación si hay diferencias.")
    public CierreResponse cerrar(@Valid @RequestBody CierreRequest req) {
        return servicio.cerrar(req.periodo(), req.observaciones(), contexto.actor());
    }

    /** Reabre un período. */
    @PostMapping("/{periodo}/reabrir")
    @PreAuthorize("hasRole('GERENCIA')")
    @Operation(summary = "Reabrir período")
    public CierreResponse reabrir(@PathVariable @jakarta.validation.constraints.Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$") String periodo,
                                  @Valid @RequestBody MotivoRequest req) {
        return servicio.reabrir(periodo, req.motivo(), contexto.actor());
    }
}
