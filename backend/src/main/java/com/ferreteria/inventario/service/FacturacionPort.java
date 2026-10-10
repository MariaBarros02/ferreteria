/*
 * Nombre: FacturacionPort.java
 * Descripción: Puerto hexagonal hacia el proveedor tecnológico de facturación electrónica DIAN (ADR-01, ADR-07, RN-14).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import com.ferreteria.inventario.domain.model.Venta;

/**
 * Contrato del adaptador de facturación. La implementación real se integra con un proveedor
 * tecnológico autorizado por la DIAN; el dominio no depende de él.
 */
public interface FacturacionPort {

    /**
     * Obtiene la numeración fiscal de una venta. Debe ser distinta del número interno (RN-14).
     *
     * @param venta venta a facturar
     * @return numeración fiscal
     */
    String generarNumeracionFiscal(Venta venta);
}
