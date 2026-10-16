/*
 * Nombre: CatalogoController.java
 * Descripción: API REST de sincronización del catálogo para consulta sin conexión (RF-12).
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
 * Solo lectura. Sin conexión el cliente consulta la copia local; no existen operaciones de escritura sin conexión (RN-10).
 */
@RestController
@RequestMapping("/api/catalogo")
@RequiredArgsConstructor
@Validated
@Tag(name = "Catálogo sin conexión", description = "Sincronización del catálogo para consulta offline")
public class CatalogoController {

    private final ProductoService productos;

    /** Página del catálogo activo con existencias y precio vigente. */
    @GetMapping("/sincronizacion")
    @Operation(summary = "Catálogo para consulta sin conexión", description = "Paginado (hasta 500 por página).")
    public PaginaResponse<ProductoSeleccionResponse> sincronizacion(@RequestParam(defaultValue = "0") @Min(0) int pagina,
                                                                    @RequestParam(defaultValue = "200") @Min(1) @Max(500) int tamano) {
        return productos.catalogo(pagina, tamano);
    }
}
