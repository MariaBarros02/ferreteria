/*
 * Nombre: DemoPasswordInitializer.java
 * Descripción: Reemplaza el marcador 'hash_demo' de los usuarios del seed por BCrypt de la contraseña demo (solo si app.bootstrap.demo-password está definida).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.bootstrap;

import com.ferreteria.inventario.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los usuarios del script 02_seed.sql usan el marcador {@code hash_demo}. Para poder iniciar sesión en
 * desarrollo, este componente lo sustituye por el hash BCrypt de {@code app.bootstrap.demo-password}.
 * En producción la propiedad no se define y no hace nada.
 */
@Component
public class DemoPasswordInitializer implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DemoPasswordInitializer.class);
    private static final String MARCADOR = "hash_demo";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final String passwordDemo;

    /**
     * Constructor.
     *
     * @param usuarios repositorio de usuarios
     * @param encoder codificador BCrypt
     * @param passwordDemo contraseña demo (vacía = deshabilitado)
     */
    public DemoPasswordInitializer(UsuarioRepository usuarios, PasswordEncoder encoder,
                                   @Value("${app.bootstrap.demo-password:}") String passwordDemo) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.passwordDemo = passwordDemo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (passwordDemo == null || passwordDemo.isBlank()) {
            return;
        }
        String hash = encoder.encode(passwordDemo);
        long n = usuarios.findAll().stream().filter(u -> MARCADOR.equals(u.getPasswordHash()))
                .peek(u -> u.setPasswordHash(hash)).count();
        if (n > 0) {
            LOG.warn("Se asignó la contraseña demo a {} usuario(s) del seed. No usar en producción.", n);
        }
    }
}
