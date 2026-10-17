/*
 * Nombre: AuthController.java
 * Descripción: Endpoints de autenticación: inicio de sesión, renovación de token y datos del usuario actual.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.controller;

import com.ferreteria.inventario.dto.LoginRequest;
import com.ferreteria.inventario.dto.LoginResponse;
import com.ferreteria.inventario.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API de autenticación.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Inicio de sesión con JWT")
public class AuthController {

    private final AuthService auth;

    /**
     * Inicia sesión.
     *
     * @param req credenciales
     * @return token JWT
     */
    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión", description = "Devuelve un JWT bearer. 5 fallos seguidos bloquean la cuenta 15 minutos (429).")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    /**
     * Renueva el token mientras el actual siga vigente.
     *
     * @param authentication usuario autenticado
     * @return nuevo token
     */
    @PostMapping("/refresh")
    @Operation(summary = "Renovar token", security = @SecurityRequirement(name = "bearerAuth"))
    public LoginResponse renovar(Authentication authentication) {
        return auth.renovar(authentication.getName());
    }

    /**
     * Devuelve el usuario autenticado.
     *
     * @param authentication usuario autenticado
     * @return nuevo token con los datos del usuario
     */
    @GetMapping("/me")
    @Operation(summary = "Usuario actual", security = @SecurityRequirement(name = "bearerAuth"))
    public LoginResponse yo(Authentication authentication) {
        return auth.renovar(authentication.getName());
    }
}
