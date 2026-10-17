/*
 * Nombre: ProductoController.java
 * Descripción: API REST del catálogo de productos (RF-01, RF-02, RF-11): CRUD, búsqueda por código y unidades; RBAC por rol.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
package com.ferreteria.inventario.controller;

import com.ferreteria.inventario.dto.ConversionRequest;
import com.ferreteria.inventario.dto.ConversionResponse;
import com.ferreteria.inventario.dto.InventarioResponse;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.dto.ProductoRequest;
import com.ferreteria.inventario.dto.ProductoResponse;
import com.ferreteria.inventario.dto.ProductoSeleccionResponse;
import com.ferreteria.inventario.security.Roles;
import com.ferreteria.inventario.service.ContextoSolicitud;
import com.ferreteria.inventario.service.ProductoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Lectura: cualquier usuario autenticado. Escritura: Inventario y Administrador (matriz RF-09).
 */
@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
@Validated
@Tag(name = "Productos", description = "Catálogo de referencias (producto + marca + presentación)")
public class ProductoController {

    private static final String ESCRITURA = "hasAnyRole('INVENTARIO','ADMINISTRADOR')";

    private final ProductoService servicio;
    private final ContextoSolicitud contexto;

    /** Crea una referencia. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(ESCRITURA)
    @Operation(summary = "Crear producto", responses = {
            @ApiResponse(responseCode = "201", description = "Creado"),
            @ApiResponse(responseCode = "400", description = "Validación fallida"),
            @ApiResponse(responseCode = "409", description = "Código de barras o referencia duplicados (RN-01)")})
    public ProductoResponse crear(@Valid @RequestBody ProductoRequest req) {
        return servicio.crear(req);
    }

    /** Lista paginada con filtro de texto. */
    @GetMapping
    @Operation(summary = "Listar/buscar productos")
    public PaginaResponse<ProductoResponse> listar(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        var r = servicio.listar(q, PageRequest.of(pagina, tamano, Sort.by("nombre", "marca", "presentacion")));
        return veCostos() ? r : new PaginaResponse<>(r.contenido().stream().map(ProductoResponse::sinCosto).toList(),
                r.pagina(), r.tamano(), r.totalElementos(), r.totalPaginas());
    }

    /** Consulta por identificador. */
    @GetMapping("/{id}")
    @Operation(summary = "Consultar producto por id")
    public ProductoResponse obtener(@PathVariable Long id) {
        var r = servicio.obtener(id);
        return veCostos() ? r : r.sinCosto();
    }

    /** Consulta por código de barras (lectores, RF-11). */
    @GetMapping("/codigo/{codigoBarras}")
    @Operation(summary = "Consultar producto por código de barras")
    public ProductoResponse porCodigo(@PathVariable @Pattern(regexp = "^\\d{8,14}$") String codigoBarras) {
        var r = servicio.obtenerPorCodigo(codigoBarras);
        return veCostos() ? r : r.sinCosto();
    }

    /** Actualiza una referencia. */
    @PutMapping("/{id}")
    @PreAuthorize(ESCRITURA)
    @Operation(summary = "Actualizar producto")
    public ProductoResponse actualizar(@PathVariable Long id, @Valid @RequestBody ProductoRequest req) {
        return servicio.actualizar(id, req);
    }

    /** Elimina un producto sin existencias ni historial. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Eliminar producto", description = "Solo si no tiene existencias ni movimientos asociados.")
    public void eliminar(@PathVariable Long id) {
        servicio.eliminar(id);
    }

    /** Conversiones de unidad configuradas. */
    @GetMapping("/{id}/unidades")
    @Operation(summary = "Consultar conversiones de unidad")
    public List<ConversionResponse> unidades(@PathVariable Long id) {
        return servicio.unidades(id);
    }

    /** Configura unidad base y conversiones (RF-02). */
    @PutMapping("/{id}/unidades")
    @PreAuthorize(ESCRITURA)
    @Operation(summary = "Configurar conversiones de unidad")
    public List<ConversionResponse> configurarUnidades(@PathVariable Long id,
                                                       @RequestBody @NotEmpty @Valid List<@Valid ConversionRequest> reqs) {
        return servicio.configurarUnidades(id, reqs);
    }

    /** Saldos por estado. */
    @GetMapping("/{id}/existencias")
    @Operation(summary = "Consultar existencias (disponible, reservado, dañado)")
    public InventarioResponse existencias(@PathVariable Long id) {
        return servicio.existencias(id);
    }

    /** Búsqueda para selectores (venta, compra, inventario): solo activos, con existencias y precio vigente. */
    @GetMapping("/seleccion")
    @Operation(summary = "Buscar productos activos para selección",
            description = "Busca por nombre, marca, presentación, categoría o código de barras. Incluye existencias, "
                    + "precio vigente aprobado y presentaciones configuradas.")
    public List<ProductoSeleccionResponse> seleccion(@RequestParam(required = false) @Size(max = 100) String q,
                                                     @RequestParam(defaultValue = "15") @Min(1) @Max(50) int tamano) {
        return servicio.buscarParaSeleccion(q, tamano);
    }

    /** Selección exacta por código de barras o QR leído con un lector (RF-11). */
    @GetMapping("/seleccion/codigo/{codigoBarras}")
    @Operation(summary = "Seleccionar producto activo por código leído (lector de barras/QR)")
    public ProductoSeleccionResponse seleccionPorCodigo(@PathVariable @Pattern(regexp = "^\\d{8,14}$") String codigoBarras) {
        return servicio.seleccionPorCodigo(codigoBarras);
    }

    /** Activa o desactiva una referencia (eliminación lógica). */
    @PatchMapping("/{id}/estado")
    @PreAuthorize(ESCRITURA)
    @Operation(summary = "Activar o desactivar producto", description = "Un producto inactivo conserva su historial "
            + "pero no puede venderse ni comprarse.")
    public ProductoResponse cambiarEstado(@PathVariable Long id, @RequestBody @Valid EstadoRequest req) {
        return servicio.cambiarEstado(id, req.activo());
    }

    /**
     * Cuerpo del cambio de estado.
     *
     * @param activo nuevo estado
     */
    public record EstadoRequest(@jakarta.validation.constraints.NotNull Boolean activo) { }

    private boolean veCostos() {
        return contexto.tieneRol(Roles.INVENTARIO, Roles.ADMINISTRADOR, Roles.GERENCIA);
    }
}
