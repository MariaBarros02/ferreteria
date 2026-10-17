/*
 * Nombre: EventoAuditoriaService.java
 * Descripción: Registro y consulta de eventos de auditoría generales (RF-08, RF-20): quién, qué, cuándo, desde dónde. Elimina datos sensibles del detalle.
 * Fecha de creación: 2026-10-16
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.0.0
 */
package com.ferreteria.inventario.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ferreteria.inventario.domain.model.EventoAuditoria;
import com.ferreteria.inventario.dto.EventoAuditoriaResponse;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.EventoAuditoriaRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bitácora de eventos. Los eventos se registran dentro de la transacción de la operación auditada:
 * si la operación se revierte, el evento también (no quedan eventos de operaciones inexistentes).
 */
@Service
@RequiredArgsConstructor
public class EventoAuditoriaService {

    private static final Logger LOG = LoggerFactory.getLogger(EventoAuditoriaService.class);
    /** Claves que nunca se guardan en el detalle (contraseñas, tokens, secretos). */
    private static final Pattern SENSIBLE = Pattern.compile(".*(password|contrasen|clave|token|secret|hash|authorization).*");
    private static final int MAX_TEXTO = 500;

    private final EventoAuditoriaRepository eventos;
    private final ObjectMapper json;
    private final com.ferreteria.inventario.config.ParametrosNegocio parametros;
    private final Clock reloj;

    /**
     * Filtros de consulta.
     *
     * @param desde fecha inicial (inclusive)
     * @param hasta fecha final (inclusive)
     * @param usuario texto contenido en el correo del usuario
     * @param modulo módulo exacto
     * @param accion acción exacta
     * @param entidad entidad exacta
     * @param q búsqueda en identificador del registro, clave de operación o descripción
     */
    public record Filtros(LocalDate desde, LocalDate hasta, String usuario, String modulo, String accion,
                          String entidad, String q) { }

    /**
     * Registra un evento.
     *
     * @param actor autor
     * @param modulo módulo (ver {@link Auditables})
     * @param accion acción (ver {@link Auditables})
     * @param entidad entidad afectada
     * @param idEntidad identificador o número del registro
     * @param descripcion descripción legible
     * @param detalle datos adicionales (se eliminan claves sensibles)
     * @return evento persistido
     */
    @Transactional
    public EventoAuditoria registrar(Actor actor, String modulo, String accion, String entidad, Object idEntidad,
                                     String descripcion, Map<String, ?> detalle) {
        return eventos.save(EventoAuditoria.builder()
                .fechaHora(LocalDateTime.ofInstant(reloj.instant(), ZoneOffset.UTC))
                .idUsuario(actor == null ? null : actor.idUsuario())
                .correoUsuario(actor == null ? null : recortar(actor.correo(), 150))
                .ip(actor == null ? null : recortar(actor.ip(), 45))
                .modulo(modulo).accion(accion).entidad(entidad)
                .idEntidad(idEntidad == null ? null : recortar(String.valueOf(idEntidad), 60))
                .descripcion(recortar(descripcion, MAX_TEXTO))
                .detalle(serializar(detalle))
                .build());
    }

    /**
     * Consulta paginada con filtros, más recientes primero.
     *
     * @param f filtros
     * @param pagina página base 0
     * @param tamano tamaño (máx. 100)
     * @return página de eventos
     */
    @Transactional(readOnly = true)
    public PaginaResponse<EventoAuditoriaResponse> consultar(Filtros f, int pagina, int tamano) {
        var p = PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100),
                Sort.by(Sort.Direction.DESC, "fechaHora", "id"));
        return PaginaResponse.de(eventos.findAll(especificacion(f, parametros.getZonaHoraria()), p), this::aDto);
    }

    /**
     * Detalle de un evento.
     *
     * @param id identificador
     * @return evento
     */
    @Transactional(readOnly = true)
    public EventoAuditoriaResponse obtener(Long id) {
        return eventos.findById(id).map(this::aDto).orElseThrow(() -> ApiException.noEncontrado("Evento"));
    }

    /**
     * Historial de eventos de un registro (p. ej. una venta).
     *
     * @param entidad entidad
     * @param idEntidad identificador
     * @return eventos
     */
    @Transactional(readOnly = true)
    public List<EventoAuditoriaResponse> deEntidad(String entidad, Object idEntidad) {
        return eventos.findByEntidadAndIdEntidadOrderByIdDesc(entidad, String.valueOf(idEntidad)).stream()
                .map(this::aDto).toList();
    }

    /**
     * Convierte a DTO.
     *
     * @param e entidad
     * @return DTO
     */
    public EventoAuditoriaResponse aDto(EventoAuditoria e) {
        Object detalle = null;
        if (e.getDetalle() != null) {
            try {
                detalle = json.readValue(e.getDetalle(), Object.class);
            } catch (JsonProcessingException ex) {
                detalle = e.getDetalle();
            }
        }
        return new EventoAuditoriaResponse(e.getId(), e.getFechaHora(), e.getIdUsuario(), e.getCorreoUsuario(), e.getIp(),
                e.getModulo(), e.getAccion(), e.getEntidad(), e.getIdEntidad(), e.getDescripcion(), detalle,
                e.getIdOperacion());
    }

    static Specification<EventoAuditoria> especificacion(Filtros f, java.time.ZoneId zona) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (f.desde() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("fechaHora"), Fechas.inicioUtc(f.desde(), zona)));
            }
            if (f.hasta() != null) {
                ps.add(cb.lessThan(root.<LocalDateTime>get("fechaHora"), Fechas.finExclusivoUtc(f.hasta(), zona)));
            }
            if (lleno(f.usuario())) {
                ps.add(cb.like(cb.lower(root.<String>get("correoUsuario")), "%" + escapar(f.usuario()) + "%"));
            }
            if (lleno(f.modulo())) {
                ps.add(cb.equal(root.get("modulo"), f.modulo().trim().toUpperCase(Locale.ROOT)));
            }
            if (lleno(f.accion())) {
                ps.add(cb.equal(root.get("accion"), f.accion().trim().toUpperCase(Locale.ROOT)));
            }
            if (lleno(f.entidad())) {
                ps.add(cb.equal(root.get("entidad"), f.entidad().trim().toUpperCase(Locale.ROOT)));
            }
            if (lleno(f.q())) {
                String t = "%" + escapar(f.q()) + "%";
                ps.add(cb.or(cb.like(cb.lower(root.<String>get("idEntidad")), t),
                        cb.like(cb.lower(root.<String>get("idOperacion")), t),
                        cb.like(cb.lower(root.<String>get("descripcion")), t)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    private static boolean lleno(String s) {
        return s != null && !s.isBlank();
    }

    /** Minúsculas y sin comodines de LIKE introducidos por el usuario. */
    private static String escapar(String s) {
        return s.trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "");
    }

    private static String recortar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private String serializar(Map<String, ?> detalle) {
        if (detalle == null || detalle.isEmpty()) {
            return null;
        }
        try {
            return json.writeValueAsString(limpiar(detalle));
        } catch (JsonProcessingException e) {
            LOG.warn("No se pudo serializar el detalle de auditoría: {}", e.getOriginalMessage());
            return null;
        }
    }

    /**
     * Copia el mapa eliminando claves sensibles (recursivo).
     *
     * @param m mapa original
     * @return copia sin datos sensibles
     */
    static Map<String, Object> limpiar(Map<String, ?> m) {
        Map<String, Object> r = new LinkedHashMap<>();
        m.forEach((k, v) -> {
            if (k == null || SENSIBLE.matcher(k.toLowerCase(Locale.ROOT)).matches()) {
                return;
            }
            if (v instanceof Map<?, ?> sub) {
                Map<String, Object> conv = new LinkedHashMap<>();
                sub.forEach((sk, sv) -> conv.put(String.valueOf(sk), sv));
                r.put(k, limpiar(conv));
            } else {
                r.put(k, v);
            }
        });
        return r;
    }
}
