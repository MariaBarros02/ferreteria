/*
 * Nombre: UsuarioController.java
 * Descripción: API REST de administración de usuarios y roles (RF-09).
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
 * Solo Administrador.
 */
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
@Validated
@Tag(name = "Usuarios", description = "Usuarios, roles y matriz de permisos")
public class UsuarioController {

    private final UsuarioService servicio;
    private final ContextoSolicitud contexto;

    /** Listado. */
    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Listar usuarios")
    public PaginaResponse<UsuarioResponse> listar(@RequestParam(required = false) @Size(max = 100) String q,
                                                  @RequestParam(defaultValue = "0") @Min(0) int pagina,
                                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return servicio.listar(q, pagina, tamano);
    }

    /** Crea un usuario. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Crear usuario", description = "Contraseña: 10+ caracteres con mayúscula, minúscula y número.")
    public UsuarioResponse crear(@Valid @RequestBody UsuarioRequest req) {
        return servicio.crear(req, contexto.actor());
    }

    /** Actualiza nombre, correo, rol y estado. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Actualizar usuario", description = "El cambio de rol o estado queda auditado. No permite "
            + "dejar el sistema sin administradores activos.")
    public UsuarioResponse actualizar(@PathVariable Integer id, @Valid @RequestBody UsuarioRequest req) {
        return servicio.actualizar(id, req, contexto.actor());
    }

    /** Restablece la contraseña. */
    @PostMapping("/{id}/clave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Restablecer contraseña")
    public void clave(@PathVariable Integer id, @Valid @RequestBody ClaveRequest req) {
        servicio.restablecerClave(id, req.password(), contexto.actor());
    }

    /** Roles y matriz de permisos. */
    @GetMapping("/roles")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Roles y matriz de permisos")
    public List<RolResponse> roles() {
        return servicio.roles();
    }
}
