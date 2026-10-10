/*
 * Nombre: AppConfig.java
 * Descripción: Beans de infraestructura compartidos: reloj del sistema y codificador de contraseñas BCrypt.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Configuración de beans transversales.
 */
@Configuration
public class AppConfig {

    /** @return reloj UTC (sustituible en pruebas) */
    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }

    /** @return codificador BCrypt para contraseñas */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
