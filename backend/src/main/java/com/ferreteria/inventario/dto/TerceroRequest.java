/*
 * Nombre: TerceroRequest.java
 * Descripción: DTO de entrada para crear/actualizar un tercero; valida campos según tipo de persona (RN-06, RF-06).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ferreteria.inventario.domain.enums.TipoIdentificacion;
import com.ferreteria.inventario.domain.enums.TipoPersona;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos de un tercero. Persona natural exige primer nombre y primer apellido; persona jurídica exige razón social.
 */
public record TerceroRequest(
        @NotNull(message = "{validacion.obligatorio}") TipoPersona tipoPersona,
        @NotNull(message = "{validacion.obligatorio}") TipoIdentificacion tipoIdentificacion,
        @NotBlank(message = "{validacion.obligatorio}")
        @Pattern(regexp = Validaciones.IDENTIFICACION, message = "{validacion.identificacion}") String numeroIdentificacion,
        @Size(max = 80, message = "{validacion.longitud}") @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String primerNombre,
        @Size(max = 80, message = "{validacion.longitud}") @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String segundoNombre,
        @Size(max = 80, message = "{validacion.longitud}") @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String primerApellido,
        @Size(max = 80, message = "{validacion.longitud}") @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String segundoApellido,
        @Size(max = 200, message = "{validacion.longitud}") @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String razonSocial,
        @NotBlank(message = "{validacion.obligatorio}") @Size(max = 200, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String direccion,
        @NotBlank(message = "{validacion.obligatorio}") @Size(max = 80, message = "{validacion.longitud}")
        @Pattern(regexp = Validaciones.SIN_HTML, message = "{validacion.sin.html}") String ciudad,
        @NotBlank(message = "{validacion.obligatorio}") @Size(max = 30, message = "{validacion.longitud}")
        @Pattern(regexp = "^[0-9+()\\-\\s]{5,30}$", message = "{validacion.identificacion}") String telefono,
        @NotBlank(message = "{validacion.obligatorio}") @Email(message = "{validacion.correo}")
        @Size(max = 150, message = "{validacion.longitud}") String correo,
        boolean cliente,
        boolean proveedor) {

    private static boolean lleno(String s) {
        return s != null && !s.isBlank();
    }

    /** RN-06: persona natural exige primer nombre y primer apellido. */
    @JsonIgnore
    @AssertTrue(message = "{validacion.tercero.natural}")
    public boolean isNaturalValida() {
        return tipoPersona != TipoPersona.NATURAL || (lleno(primerNombre) && lleno(primerApellido));
    }

    /** RN-06: persona jurídica exige razón social. */
    @JsonIgnore
    @AssertTrue(message = "{validacion.tercero.juridica}")
    public boolean isJuridicaValida() {
        return tipoPersona != TipoPersona.JURIDICA || lleno(razonSocial);
    }

    /** RN-06: el tercero debe tener al menos un rol. */
    @JsonIgnore
    @AssertTrue(message = "{validacion.tercero.rol}")
    public boolean isRolValido() {
        return cliente || proveedor;
    }
}
