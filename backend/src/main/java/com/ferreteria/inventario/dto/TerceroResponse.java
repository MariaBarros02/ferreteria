/*
 * Nombre: TerceroResponse.java
 * Descripción: DTO de salida de tercero.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
package com.ferreteria.inventario.dto;

import com.ferreteria.inventario.domain.enums.TipoIdentificacion;
import com.ferreteria.inventario.domain.enums.TipoPersona;
import com.ferreteria.inventario.domain.model.Tercero;

/**
 * Representación pública de un tercero.
 */
public record TerceroResponse(Long id, TipoPersona tipoPersona, TipoIdentificacion tipoIdentificacion,
                              String numeroIdentificacion, String primerNombre, String segundoNombre,
                              String primerApellido, String segundoApellido, String razonSocial, String direccion,
                              String ciudad, String telefono, String correo, boolean cliente, boolean proveedor,
                              String nombre) {

    /** Construye desde la entidad. */
    public static TerceroResponse de(Tercero t) {
        return new TerceroResponse(t.getId(), t.getTipoPersona(), t.getTipoIdentificacion(), t.getNumeroIdentificacion(),
                t.getPrimerNombre(), t.getSegundoNombre(), t.getPrimerApellido(), t.getSegundoApellido(),
                t.getRazonSocial(), t.getDireccion(), t.getCiudad(), t.getTelefono(), t.getCorreo(),
                t.isCliente(), t.isProveedor(), nombreDe(t));
    }

    /**
     * Nombre para mostrar: razón social (jurídica) o nombres y apellidos (natural).
     *
     * @param t tercero
     * @return nombre legible
     */
    public static String nombreDe(Tercero t) {
        if (t.getRazonSocial() != null && !t.getRazonSocial().isBlank()) {
            return t.getRazonSocial();
        }
        return java.util.stream.Stream.of(t.getPrimerNombre(), t.getSegundoNombre(), t.getPrimerApellido(),
                        t.getSegundoApellido())
                .filter(x -> x != null && !x.isBlank()).collect(java.util.stream.Collectors.joining(" "));
    }
}
