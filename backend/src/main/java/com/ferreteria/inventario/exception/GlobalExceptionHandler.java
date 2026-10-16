/*
 * Nombre: GlobalExceptionHandler.java
 * Descripción: Manejador global de errores: respuestas JSON {codigo, mensaje} internacionalizadas, sin filtrar detalles internos.
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
package com.ferreteria.inventario.exception;

import com.ferreteria.inventario.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduce excepciones a respuestas HTTP coherentes.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource mensajes;

    private String msg(String clave, Object... args) {
        return mensajes.getMessage(clave, args, clave, LocaleContextHolder.getLocale());
    }

    /** Errores de negocio controlados. */
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> negocio(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(ErrorResponse.de(e.getCodigo(), msg(e.getMessage(), e.getArgumentos())));
    }

    /** Validación de Bean Validation: 400 con detalle por campo. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validacion(MethodArgumentNotValidException e) {
        Map<String, String> detalles = new LinkedHashMap<>();
        for (FieldError f : e.getBindingResult().getFieldErrors()) {
            detalles.putIfAbsent(f.getField(), f.getDefaultMessage());
        }
        e.getBindingResult().getGlobalErrors().forEach(g -> detalles.putIfAbsent(g.getObjectName(), g.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDACION_FALLIDA", msg("error.validacion"), detalles));
    }


    /** JSON mal formado, campos desconocidos o tipos inválidos. */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ErrorResponse> cuerpoInvalido(Exception e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.de("SOLICITUD_INVALIDA", msg("error.validacion")));
    }

    /** Violación de restricciones de la base de datos (FK, UNIQUE, CHECK, disparadores). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> integridad(DataIntegrityViolationException e) {
        LOG.warn("Violación de integridad: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.de("VIOLACION_INTEGRIDAD", msg("error.integridad")));
    }

    /** Archivo cargado mayor al límite configurado (spring.servlet.multipart.max-file-size). */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorResponse> archivoGrande(Exception e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ErrorResponse.de("ARCHIVO_DEMASIADO_GRANDE", msg("error.archivo.tamano")));
    }

    /** Bloqueos o interbloqueos entre transacciones concurrentes: el cliente puede reintentar. */
    @ExceptionHandler({org.springframework.dao.PessimisticLockingFailureException.class,
            org.springframework.dao.CannotAcquireLockException.class})
    ResponseEntity<ErrorResponse> concurrencia(Exception e) {
        LOG.warn("Conflicto de concurrencia: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.de("OPERACION_CONCURRENTE", msg("error.concurrencia")));
    }

    /** Ruta inexistente. */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> ruta(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.de("NO_ENCONTRADO", msg("error.no.encontrado", "Recurso")));
    }

    /** Método HTTP no permitido. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> metodo(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ErrorResponse.de("METODO_NO_PERMITIDO", msg("error.validacion")));
    }

    /** Falta de permiso (RBAC por método). */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> prohibido(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.de("SIN_PERMISO", msg("error.sin.permiso")));
    }

    /** Violación de restricciones en parámetros de ruta/consulta (@Validated en controladores). */
    @ExceptionHandler({jakarta.validation.ConstraintViolationException.class,
            org.springframework.web.method.annotation.HandlerMethodValidationException.class})
    ResponseEntity<ErrorResponse> parametros(Exception e) {
        return ResponseEntity.badRequest().body(ErrorResponse.de("VALIDACION_FALLIDA", msg("error.validacion")));
    }

    /** Cualquier otro error: se registra internamente y no se expone el detalle al cliente. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> inesperado(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse er && er.getStatusCode().is4xxClientError()) {
            // Errores HTTP estándar de Spring MVC (404 de ruta, 405, parámetro faltante…): se respeta su estado.
            return ResponseEntity.status(er.getStatusCode()).body(ErrorResponse.de("SOLICITUD_INVALIDA", msg("error.validacion")));
        }
        LOG.error("Error no controlado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse.de("ERROR_INTERNO", msg("error.interno")));
    }
}
