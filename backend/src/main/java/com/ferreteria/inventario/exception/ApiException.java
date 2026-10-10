/*
 * Nombre: ApiException.java
 * Descripción: Excepción de negocio con estado HTTP, código estable y clave de mensaje internacionalizado.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-07
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.exception;

import org.springframework.http.HttpStatus;

/**
 * Error controlado de la API. El mensaje final se resuelve con {@code MessageSource}
 * según el idioma de la petición (cabecera {@code Accept-Language}).
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;
    private final transient Object[] argumentos;

    /**
     * Crea la excepción.
     *
     * @param status estado HTTP
     * @param codigo código estable para clientes (p. ej. SALDO_INSUFICIENTE)
     * @param claveMensaje clave en messages.properties
     * @param argumentos parámetros del mensaje
     */
    public ApiException(HttpStatus status, String codigo, String claveMensaje, Object... argumentos) {
        super(claveMensaje);
        this.status = status;
        this.codigo = codigo;
        this.argumentos = argumentos;
    }

    /** @return estado HTTP */
    public HttpStatus getStatus() { return status; }

    /** @return código estable del error */
    public String getCodigo() { return codigo; }

    /** @return argumentos del mensaje */
    public Object[] getArgumentos() { return argumentos; }

    /** 404: recurso inexistente. @param recurso nombre del recurso @return excepción */
    public static ApiException noEncontrado(String recurso) {
        return new ApiException(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "error.no.encontrado", recurso);
    }

    /** 409: conflicto (duplicados, saldo insuficiente). */
    public static ApiException conflicto(String codigo, String clave, Object... args) {
        return new ApiException(HttpStatus.CONFLICT, codigo, clave, args);
    }

    /** 422: regla de negocio incumplida. */
    public static ApiException regla(String codigo, String clave, Object... args) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, codigo, clave, args);
    }
}
