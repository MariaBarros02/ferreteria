/*
 * Nombre: VentaController.java
 * Descripción: API REST de ventas (RF-04): registro con factura, consulta y listado; solo Ventas/Administrador registran.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.controller;

import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.dto.VentaRequest;
import com.ferreteria.inventario.dto.VentaResponse;
import com.ferreteria.inventario.service.VentaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Operaciones de venta.
 */
@RestController
@RequestMapping("/api/ventas")
@RequiredArgsConstructor
@Validated
@Tag(name = "Ventas", description = "Registro de ventas y emisión de factura")
public class VentaController {

    private final VentaService servicio;

    /** Registra una venta. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('VENTAS','ADMINISTRADOR')")
    @Operation(summary = "Registrar venta", description = "Descuenta inventario de forma atómica y emite la factura.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Venta y factura creadas"),
                    @ApiResponse(responseCode = "409", description = "Saldo insuficiente (RN-03, RN-11)"),
                    @ApiResponse(responseCode = "422", description = "Sin precio vigente o conversión no configurada")})
    public VentaResponse registrar(@Valid @RequestBody VentaRequest req, Authentication auth, HttpServletRequest http) {
        return servicio.registrar(req, auth.getName(), http.getRemoteAddr());
    }

    /** Lista paginada. */
    @GetMapping
    @PreAuthorize("hasAnyRole('VENTAS','INVENTARIO','ADMINISTRADOR','GERENCIA')")
    @Operation(summary = "Listar ventas")
    public PaginaResponse<VentaResponse> listar(@RequestParam(defaultValue = "0") @Min(0) int pagina,
                                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return servicio.listar(pagina, tamano);
    }

    /** Consulta una venta. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('VENTAS','INVENTARIO','ADMINISTRADOR','GERENCIA')")
    @Operation(summary = "Consultar venta")
    public VentaResponse obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }
}
