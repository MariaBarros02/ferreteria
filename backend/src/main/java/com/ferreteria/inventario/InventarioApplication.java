/*
 * Nombre: InventarioApplication.java
 * Descripción: Punto de entrada de la aplicación Spring Boot del sistema de inventario de ferretería.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Clase principal. Arranca el contexto de Spring Boot.
 */
@SpringBootApplication
public class InventarioApplication {

    /**
     * Método de arranque.
     *
     * @param args argumentos de línea de comandos
     */
    public static void main(String[] args) {
        SpringApplication.run(InventarioApplication.class, args);
    }
}
