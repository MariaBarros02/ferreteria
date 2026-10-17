/*
 * Nombre: ParametrosNegocio.java
 * Descripción: Parámetros de negocio configurables (descuento máximo, umbral de vencimiento, redondeo y modo de precio, datos de la empresa).
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.config;

import java.math.BigDecimal;
import java.time.ZoneId;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Valores leídos de {@code application.yml} (prefijo {@code app.}); todos admiten variable de entorno.
 */
@Component
@Getter
public class ParametrosNegocio {

    /** Descuento máximo por línea de venta, en porcentaje (0 deshabilita descuentos). */
    private final BigDecimal descuentoMaximoVenta;
    /** Días de anticipación para la alerta de vencimiento. */
    private final int diasAlertaVencimiento;
    /** Múltiplo al que se redondea el precio propuesto (1 = pesos enteros; 0.01 = centavos). */
    private final BigDecimal multiploRedondeoPrecio;
    /** Modo de cálculo por defecto del precio propuesto: recargo (RN-07) o margen. */
    private final String modoPrecioPorDefecto;
    /** Nombre comercial que se imprime en los comprobantes. */
    private final String empresaNombre;
    /** NIT de la empresa. */
    private final String empresaNit;
    /** Dirección. */
    private final String empresaDireccion;
    /** Ciudad. */
    private final String empresaCiudad;
    /** Teléfono. */
    private final String empresaTelefono;
    /** Correo. */
    private final String empresaCorreo;
    /** Zona horaria del negocio: define "hoy" para vencimientos y el mes de los cierres. */
    private final ZoneId zonaHoraria;

    /**
     * Constructor con los valores configurados.
     *
     * @param descuentoMaximoVenta app.ventas.descuento-maximo
     * @param diasAlertaVencimiento app.inventario.dias-alerta-vencimiento
     * @param multiploRedondeoPrecio app.precios.multiplo-redondeo
     * @param modoPrecioPorDefecto app.precios.modo
     * @param empresaNombre app.empresa.nombre
     * @param empresaNit app.empresa.nit
     * @param empresaDireccion app.empresa.direccion
     * @param empresaCiudad app.empresa.ciudad
     * @param empresaTelefono app.empresa.telefono
     * @param empresaCorreo app.empresa.correo
     * @param zonaHoraria app.zona-horaria (IANA, p. ej. America/Bogota)
     */
    public ParametrosNegocio(@Value("${app.ventas.descuento-maximo:10}") BigDecimal descuentoMaximoVenta,
                             @Value("${app.inventario.dias-alerta-vencimiento:30}") int diasAlertaVencimiento,
                             @Value("${app.precios.multiplo-redondeo:1}") BigDecimal multiploRedondeoPrecio,
                             @Value("${app.precios.modo:recargo}") String modoPrecioPorDefecto,
                             @Value("${app.empresa.nombre:Ferretería}") String empresaNombre,
                             @Value("${app.empresa.nit:}") String empresaNit,
                             @Value("${app.empresa.direccion:}") String empresaDireccion,
                             @Value("${app.empresa.ciudad:}") String empresaCiudad,
                             @Value("${app.empresa.telefono:}") String empresaTelefono,
                             @Value("${app.empresa.correo:}") String empresaCorreo,
                             @Value("${app.zona-horaria:America/Bogota}") String zonaHoraria) {
        this.descuentoMaximoVenta = descuentoMaximoVenta;
        this.diasAlertaVencimiento = diasAlertaVencimiento;
        this.multiploRedondeoPrecio = multiploRedondeoPrecio;
        this.modoPrecioPorDefecto = modoPrecioPorDefecto;
        this.empresaNombre = empresaNombre;
        this.empresaNit = empresaNit;
        this.empresaDireccion = empresaDireccion;
        this.empresaCiudad = empresaCiudad;
        this.empresaTelefono = empresaTelefono;
        this.empresaCorreo = empresaCorreo;
        this.zonaHoraria = ZoneId.of(zonaHoraria);
    }

    /**
     * Valores por defecto (pruebas unitarias).
     *
     * @return parámetros con los valores por defecto documentados
     */
    public static ParametrosNegocio porDefecto() {
        return new ParametrosNegocio(new BigDecimal("10"), 30, BigDecimal.ONE, "recargo", "Ferretería", "", "", "", "", "",
                "America/Bogota");
    }
}
