/*
 * Nombre: AuthService.java
 * Descripción: Caso de uso de autenticación: valida credenciales con BCrypt, bloqueo por intentos fallidos y emite JWT.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import com.ferreteria.inventario.domain.enums.EstadoCuenta;
import com.ferreteria.inventario.domain.model.Usuario;
import com.ferreteria.inventario.dto.LoginRequest;
import com.ferreteria.inventario.dto.LoginResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.UsuarioRepository;
import com.ferreteria.inventario.security.JwtService;
import com.ferreteria.inventario.security.LoginAttemptService;
import com.ferreteria.inventario.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Servicio de autenticación (RF-09, RNF-03).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final LoginAttemptService intentos;

    /**
     * Inicia sesión.
     *
     * @param req credenciales
     * @return token y datos del usuario
     * @throws ApiException 401 si las credenciales son inválidas, 429 si la cuenta está bloqueada, 403 si está inactiva
     */
    public LoginResponse login(LoginRequest req) {
        String correo = req.correo().trim();
        if (intentos.estaBloqueado(correo)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CUENTA_BLOQUEADA", "error.cuenta.bloqueada");
        }
        Usuario u = usuarios.findByCorreoIgnoreCase(correo).orElse(null);
        // Mismo mensaje para usuario inexistente y contraseña errónea: no revela qué cuentas existen.
        if (u == null || !encoder.matches(req.password(), u.getPasswordHash())) {
            intentos.registrarFallo(correo);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INVALIDAS", "error.credenciales");
        }
        if (u.getEstado() != EstadoCuenta.ACTIVO) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CUENTA_INACTIVA", "error.cuenta.inactiva");
        }
        intentos.registrarExito(correo);
        return respuesta(u);
    }

    /**
     * Renueva el token de un usuario ya autenticado (permanencia de sesión).
     *
     * @param correo correo del usuario autenticado
     * @return nuevo token
     */
    public LoginResponse renovar(String correo) {
        Usuario u = usuarios.findByCorreoIgnoreCase(correo)
                .filter(x -> x.getEstado() == EstadoCuenta.ACTIVO)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO", "error.no.autenticado"));
        return respuesta(u);
    }

    private LoginResponse respuesta(Usuario u) {
        String token = jwt.generar(u.getCorreo(), Roles.normalizar(u.getRol().getNombre()));
        return new LoginResponse(token, "Bearer", jwt.getVigenciaSegundos(), LoginResponse.UsuarioInfo.de(u));
    }
}
