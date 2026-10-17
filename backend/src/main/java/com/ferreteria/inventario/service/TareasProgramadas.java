/*
 * Nombre: TareasProgramadas.java
 * Descripción: Tareas periódicas del sistema: liberación diaria de reservas vencidas (RN-03).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cada reserva se libera en su propia transacción: un error en una no impide liberar las demás.
 */
@Component
@RequiredArgsConstructor
public class TareasProgramadas {

    private static final Logger LOG = LoggerFactory.getLogger(TareasProgramadas.class);

    private final ReservaService reservas;

    /** Todos los días a las 00:15 (zona del negocio) libera las reservas cuya fecha límite pasó. */
    @Scheduled(cron = "${app.reservas.cron-liberacion:0 15 0 * * *}", zone = "${app.zona-horaria:America/Bogota}")
    public void liberarReservasVencidas() {
        int n = 0;
        for (Long id : reservas.idsVencidas()) {
            try {
                n += reservas.liberarUna(id);
            } catch (RuntimeException e) {
                LOG.warn("No se pudo liberar la reserva {}: {}", id, e.getMessage());
            }
        }
        if (n > 0) {
            LOG.info("Reservas liberadas por vencimiento: {}", n);
        }
    }
}
