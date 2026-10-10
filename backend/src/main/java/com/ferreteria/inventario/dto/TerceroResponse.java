/*
 * Nombre: TerceroResponse.java
 * Descripción: DTO de salida de tercero.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
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
                              String ciudad, String telefono, String correo, boolean cliente, boolean proveedor) {

    /** Construye desde la entidad. */
    public static TerceroResponse de(Tercero t) {
        return new TerceroResponse(t.getId(), t.getTipoPersona(), t.getTipoIdentificacion(), t.getNumeroIdentificacion(),
                t.getPrimerNombre(), t.getSegundoNombre(), t.getPrimerApellido(), t.getSegundoApellido(),
                t.getRazonSocial(), t.getDireccion(), t.getCiudad(), t.getTelefono(), t.getCorreo(),
                t.isCliente(), t.isProveedor());
    }
}
