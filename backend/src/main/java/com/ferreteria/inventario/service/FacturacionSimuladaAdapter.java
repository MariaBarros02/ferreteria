/*
 * Nombre: FacturacionSimuladaAdapter.java
 * Descripción: Adaptador de facturación simulado para desarrollo y pruebas (numeración consecutiva DIAN-SETP-n). Sustituir en producción.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import com.ferreteria.inventario.domain.model.Venta;
import com.ferreteria.inventario.repository.VentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementación de desarrollo: genera una numeración fiscal ficticia con una secuencia de base de datos.
 */
@Component
@RequiredArgsConstructor
public class FacturacionSimuladaAdapter implements FacturacionPort {

    private final VentaRepository ventas;

    @Override
    public String generarNumeracionFiscal(Venta venta) {
        return String.format("DIAN-SETP-%06d", ventas.siguienteNumeracionDian());
    }
}
