/*
 * Nombre: MonedaController.java
 * Descripción: API REST de monedas y tasas de cambio (RF-23): consulta para todos los roles; registro de tasas por Gerencia o Administrador; alta y activación de monedas por Administrador o Gerencia; conversión.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.controller;

import com.ferreteria.inventario.dto.ConversionMonedaResponse;
import com.ferreteria.inventario.dto.MonedaRequest;
import com.ferreteria.inventario.dto.MonedaResponse;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.dto.TasaCambioRequest;
import com.ferreteria.inventario.dto.TasaCambioResponse;
import com.ferreteria.inventario.service.ContextoSolicitud;
import com.ferreteria.inventario.service.MonedaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** Monedas y tasas de cambio. */
@RestController
@RequestMapping("/api/monedas")
@RequiredArgsConstructor
@Validated
@Tag(name = "Monedas", description = "Monedas, tasas de cambio y conversión (moneda funcional COP)")
public class MonedaController {

    private static final String GESTION = "hasAnyRole('GERENCIA','ADMINISTRADOR')";
    private static final String CODIGO = "^[A-Za-z]{3}$";

    private final MonedaService servicio;
    private final ContextoSolicitud contexto;

    /** Monedas con su tasa vigente. */
    @GetMapping
    @Operation(summary = "Listar monedas", description = "Incluye la tasa vigente hoy (pesos por unidad).")
    public List<MonedaResponse> listar(@RequestParam(defaultValue = "false") boolean soloActivas) {
        return servicio.listar(soloActivas);
    }

    /** Registra una moneda adicional. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GESTION)
    @Operation(summary = "Registrar moneda (ISO 4217)")
    public MonedaResponse crear(@Valid @RequestBody MonedaRequest req) {
        return servicio.crear(req, contexto.actor());
    }

    /** Activa o desactiva una moneda. */
    @PatchMapping("/{codigo}/estado")
    @PreAuthorize(GESTION)
    @Operation(summary = "Activar o desactivar moneda", description = "La moneda funcional no se desactiva.")
    public MonedaResponse cambiarEstado(@PathVariable @Pattern(regexp = CODIGO) String codigo,
                                        @Valid @RequestBody ProductoController.EstadoRequest req) {
        return servicio.cambiarEstado(codigo, req.activo(), contexto.actor());
    }

    /** Historial de tasas. */
    @GetMapping("/{codigo}/tasas")
    @Operation(summary = "Historial de tasas de cambio")
    public PaginaResponse<TasaCambioResponse> historial(@PathVariable @Pattern(regexp = CODIGO) String codigo,
                                                        @RequestParam(defaultValue = "0") @Min(0) int pagina,
                                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return servicio.historial(codigo, pagina, tamano);
    }

    /** Registra una tasa de cambio. */
    @PostMapping("/{codigo}/tasas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GESTION)
    @Operation(summary = "Registrar tasa de cambio", description = "Pesos colombianos por 1 unidad de la moneda. "
            + "Inmutable: una corrección se registra como tasa nueva.")
    public TasaCambioResponse registrarTasa(@PathVariable @Pattern(regexp = CODIGO) String codigo,
                                            @Valid @RequestBody TasaCambioRequest req) {
        return servicio.registrarTasa(codigo, req, contexto.actor());
    }

    /** Convierte un monto entre monedas con las tasas vigentes. */
    @GetMapping("/conversion")
    @Operation(summary = "Convertir un monto entre monedas")
    public ConversionMonedaResponse convertir(
            @RequestParam @DecimalMin("0") @Digits(integer = 14, fraction = 4) BigDecimal monto,
            @RequestParam @Pattern(regexp = CODIGO) String desde,
            @RequestParam @Pattern(regexp = CODIGO) String hacia) {
        return servicio.convertir(monto, desde, hacia);
    }
}
