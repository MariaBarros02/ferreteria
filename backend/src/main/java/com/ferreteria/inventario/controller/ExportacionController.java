/*
 * Nombre: ExportacionController.java
 * Descripción: API REST de exportación a Excel de listados (productos, existencias, terceros, ventas, movimientos).
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
 * Permisos iguales a los de la consulta en pantalla; los costos solo para Inventario/Administrador/Gerencia.
 */
@RestController
@RequestMapping("/api/exportaciones")
@RequiredArgsConstructor
@Validated
@Tag(name = "Exportación", description = "Descarga de listados en Excel (.xlsx)")
public class ExportacionController {

    private final ExportacionService servicio;
    private final ContextoSolicitud contexto;

    /** Catálogo. */
    @GetMapping("/productos")
    @Operation(summary = "Exportar productos")
    public ResponseEntity<byte[]> productos() {
        return Descargas.xlsx("productos.xlsx", servicio.productos(veCostos(), contexto.actor()));
    }

    /** Existencias. */
    @GetMapping("/existencias")
    @Operation(summary = "Exportar existencias")
    public ResponseEntity<byte[]> existencias() {
        return Descargas.xlsx("existencias.xlsx", servicio.existencias(veCostos(), contexto.actor()));
    }

    /** Terceros (datos personales). */
    @GetMapping("/terceros")
    @PreAuthorize("hasAnyRole('COMPRAS','VENTAS','ADMINISTRADOR')")
    @Operation(summary = "Exportar terceros")
    public ResponseEntity<byte[]> terceros() {
        return Descargas.xlsx("terceros.xlsx", servicio.terceros(contexto.actor()));
    }

    /** Ventas. */
    @GetMapping("/ventas")
    @PreAuthorize("hasAnyRole('VENTAS','INVENTARIO','ADMINISTRADOR','GERENCIA')")
    @Operation(summary = "Exportar ventas")
    public ResponseEntity<byte[]> ventas(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return Descargas.xlsx("ventas.xlsx", servicio.ventas(desde, hasta, contexto.actor()));
    }

    /** Movimientos. */
    @GetMapping("/movimientos")
    @PreAuthorize("hasAnyRole('INVENTARIO','ADMINISTRADOR','GERENCIA')")
    @Operation(summary = "Exportar movimientos de inventario")
    public ResponseEntity<byte[]> movimientos(@RequestParam(required = false) Long productoId,
            @RequestParam(required = false) com.ferreteria.inventario.domain.enums.TipoMovimiento tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return Descargas.xlsx("movimientos.xlsx", servicio.movimientos(
                new AuditoriaService.Filtros(productoId, tipo, null, null, desde, hasta), contexto.actor()));
    }

    private boolean veCostos() {
        return contexto.tieneRol("INVENTARIO", "ADMINISTRADOR", "GERENCIA");
    }
}
