/*
 * Nombre: DevolucionController.java
 * Descripción: API REST de devoluciones de cliente y a proveedor (RF-13).
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
 * Devoluciones vinculadas al documento original.
 */
@RestController
@RequestMapping("/api/devoluciones")
@RequiredArgsConstructor
@Validated
@Tag(name = "Devoluciones", description = "Devoluciones de cliente y a proveedor")
public class DevolucionController {

    private final DevolucionService servicio;
    private final ContextoSolicitud contexto;

    /** Devolución de cliente. */
    @PostMapping("/clientes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('VENTAS','INVENTARIO','ADMINISTRADOR')")
    @Operation(summary = "Registrar devolución de cliente", description = "Valida lo devuelto acumulado contra lo vendido; "
            + "solo el producto apto vuelve a disponible.")
    public DevolucionResponse cliente(@Valid @RequestBody DevolucionClienteRequest req) {
        return servicio.registrarCliente(req, contexto.actor());
    }

    /** Devolución a proveedor. */
    @PostMapping("/proveedores")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('COMPRAS','INVENTARIO','ADMINISTRADOR')")
    @Operation(summary = "Registrar devolución a proveedor")
    public DevolucionResponse proveedor(@Valid @RequestBody DevolucionProveedorRequest req) {
        return servicio.registrarProveedor(req, contexto.actor());
    }

    /** Historial con filtros. */
    @GetMapping
    @PreAuthorize("hasAnyRole('VENTAS','COMPRAS','INVENTARIO','ADMINISTRADOR','GERENCIA')")
    @Operation(summary = "Listar devoluciones")
    public PaginaResponse<DevolucionResponse> listar(
            @RequestParam(required = false) com.ferreteria.inventario.domain.enums.TipoDevolucion tipo,
            @RequestParam(required = false) @Size(max = 30) String q,
            @RequestParam(required = false) Long ventaId,
            @RequestParam(required = false) Long compraId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return servicio.listar(new DevolucionService.Filtros(tipo, q, ventaId, compraId, desde, hasta), pagina, tamano);
    }

    /** Detalle. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('VENTAS','COMPRAS','INVENTARIO','ADMINISTRADOR','GERENCIA')")
    @Operation(summary = "Consultar devolución")
    public DevolucionResponse obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }
}
