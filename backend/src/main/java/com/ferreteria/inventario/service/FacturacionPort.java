/*
 * Nombre: FacturacionPort.java
 * Descripción: Puerto hexagonal hacia el proveedor tecnológico de facturación electrónica DIAN (ADR-01, ADR-07, RN-14).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
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

    /**
     * Indica si la numeración generada tiene validez fiscal (factura electrónica validada por la DIAN).
     * Por defecto {@code false}: solo un adaptador integrado con un proveedor tecnológico autorizado debe
     * devolver {@code true}. Con {@code false} el documento se presenta como comprobante interno.
     *
     * @return {@code true} si la factura es válida ante la DIAN
     */
    default boolean esValidaFiscalmente() {
        return false;
    }
}
