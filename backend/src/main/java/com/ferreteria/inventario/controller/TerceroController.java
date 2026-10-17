/*
 * Nombre: TerceroController.java
 * Descripción: API REST de terceros (clientes y proveedores): CRUD con RBAC (RF-06).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
package com.ferreteria.inventario.controller;

import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.dto.TerceroRequest;
import com.ferreteria.inventario.dto.TerceroResponse;
import com.ferreteria.inventario.service.TerceroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Lectura: autenticados. Escritura: Compras, Ventas y Administrador. Eliminación: Administrador.
 */
@RestController
@RequestMapping("/api/terceros")
@RequiredArgsConstructor
@Validated
@Tag(name = "Terceros", description = "Clientes y proveedores (persona natural o jurídica)")
public class TerceroController {

    private static final String ESCRITURA = "hasAnyRole('COMPRAS','VENTAS','ADMINISTRADOR')";

    private final TerceroService servicio;

    /** Crea un tercero. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(ESCRITURA)
    @Operation(summary = "Crear tercero")
    public TerceroResponse crear(@Valid @RequestBody TerceroRequest req) {
        return servicio.crear(req);
    }

    /** Lista paginada con filtro de texto. */
    @GetMapping
    @Operation(summary = "Listar/buscar terceros")
    public PaginaResponse<TerceroResponse> listar(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return servicio.listar(q, PageRequest.of(pagina, tamano, Sort.by("id")));
    }

    /** Consulta por identificador. */
    @GetMapping("/{id}")
    @Operation(summary = "Consultar tercero")
    public TerceroResponse obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    /** Actualiza un tercero. */
    @PutMapping("/{id}")
    @PreAuthorize(ESCRITURA)
    @Operation(summary = "Actualizar tercero")
    public TerceroResponse actualizar(@PathVariable Long id, @Valid @RequestBody TerceroRequest req) {
        return servicio.actualizar(id, req);
    }

    /** Elimina un tercero sin documentos asociados. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Eliminar tercero", description = "Solo si no tiene documentos asociados (409 en caso contrario).")
    public void eliminar(@PathVariable Long id) {
        servicio.eliminar(id);
    }

    /** Selector de clientes o proveedores con búsqueda por nombre, identificación o correo. */
    @GetMapping("/seleccion")
    @Operation(summary = "Buscar clientes o proveedores para selección")
    public java.util.List<TerceroResponse> seleccion(@RequestParam(required = false) @Size(max = 100) String q,
                                                     @RequestParam(defaultValue = "cliente")
                                                     @jakarta.validation.constraints.Pattern(regexp = "cliente|proveedor|todos") String rol,
                                                     @RequestParam(defaultValue = "15") @Min(1) @Max(50) int tamano) {
        return servicio.buscarParaSeleccion(q, rol, tamano);
    }
}
