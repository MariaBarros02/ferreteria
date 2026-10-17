/*
 * Nombre: TerceroService.java
 * Descripción: Casos de uso de terceros (clientes/proveedores): CRUD con reglas RN-06 y unicidad de identificación (RF-06).
 * Fecha de creación: 2026-10-07
 * Fecha de actualización: 2026-10-16
 * Autor: Equipo Ferretería
 * Versión: 1.1.0
 */
package com.ferreteria.inventario.service;

import com.ferreteria.inventario.domain.enums.TipoPersona;
import com.ferreteria.inventario.domain.model.Tercero;
import com.ferreteria.inventario.dto.PaginaResponse;
import com.ferreteria.inventario.dto.TerceroRequest;
import com.ferreteria.inventario.dto.TerceroResponse;
import com.ferreteria.inventario.exception.ApiException;
import com.ferreteria.inventario.repository.TerceroRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica de negocio de terceros. Persona natural y jurídica se normalizan para no almacenar
 * campos de la otra clase (datos inconsistentes).
 */
@Service
@RequiredArgsConstructor
public class TerceroService {

    private final TerceroRepository terceros;
    private final EventoAuditoriaService eventos;
    private final ContextoSolicitud contexto;

    /**
     * Crea un tercero.
     *
     * @param req datos validados
     * @return tercero creado
     */
    @Transactional
    public TerceroResponse crear(TerceroRequest req) {
        validarIdentificacion(req.numeroIdentificacion().trim(), null);
        Tercero t = new Tercero();
        aplicar(t, req);
        t = terceros.save(t);
        eventos.registrar(contexto.actor(), Auditables.TERCEROS, Auditables.CREAR, "TERCERO", t.getId(),
                "Tercero creado: " + TerceroResponse.nombreDe(t) + " (" + t.getNumeroIdentificacion() + ")", resumen(t));
        return TerceroResponse.de(t);
    }

    /**
     * Actualiza un tercero.
     *
     * @param id identificador
     * @param req nuevos datos
     * @return tercero actualizado
     */
    @Transactional
    public TerceroResponse actualizar(Long id, TerceroRequest req) {
        Tercero t = buscar(id);
        validarIdentificacion(req.numeroIdentificacion().trim(), id);
        Map<String, Object> antes = resumen(t);
        aplicar(t, req);
        t = terceros.save(t);
        eventos.registrar(contexto.actor(), Auditables.TERCEROS, Auditables.ACTUALIZAR, "TERCERO", t.getId(),
                "Tercero actualizado: " + TerceroResponse.nombreDe(t), Map.of("antes", antes, "despues", resumen(t)));
        return TerceroResponse.de(t);
    }

    /**
     * Consulta por identificador.
     *
     * @param id identificador
     * @return tercero
     */
    @Transactional(readOnly = true)
    public TerceroResponse obtener(Long id) {
        return TerceroResponse.de(buscar(id));
    }

    /**
     * Lista paginada con filtro de texto.
     *
     * @param q texto opcional
     * @param pageable paginación
     * @return página de terceros
     */
    @Transactional(readOnly = true)
    public PaginaResponse<TerceroResponse> listar(String q, Pageable pageable) {
        return PaginaResponse.de(terceros.buscar(q == null ? "" : q.trim(), pageable), TerceroResponse::de);
    }

    /**
     * Elimina un tercero sin documentos asociados; si tiene historial la base de datos lo impide (409).
     *
     * @param id identificador
     */
    @Transactional
    public void eliminar(Long id) {
        Tercero t = buscar(id);
        terceros.deleteById(id);
        terceros.flush();
        eventos.registrar(contexto.actor(), Auditables.TERCEROS, Auditables.ELIMINAR, "TERCERO", id,
                "Tercero eliminado (sin documentos): " + TerceroResponse.nombreDe(t), null);
    }

    /**
     * Búsqueda para selectores de cliente o proveedor (nombre, identificación o correo).
     *
     * @param q texto
     * @param rol "cliente" o "proveedor"
     * @param tamano máximo de resultados (1–50)
     * @return coincidencias con el rol pedido
     */
    @Transactional(readOnly = true)
    public List<TerceroResponse> buscarParaSeleccion(String q, String rol, int tamano) {
        boolean clientes = !"proveedor".equalsIgnoreCase(rol);
        boolean proveedores = !"cliente".equalsIgnoreCase(rol);
        return terceros.buscarPorRol(q == null ? "" : q.trim(), clientes, proveedores,
                        PageRequest.of(0, Math.min(Math.max(tamano, 1), 50), Sort.by("id")))
                .getContent().stream().map(TerceroResponse::de).toList();
    }

    private static Map<String, Object> resumen(Tercero t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tipoPersona", String.valueOf(t.getTipoPersona()));
        m.put("identificacion", t.getTipoIdentificacion() + " " + t.getNumeroIdentificacion());
        m.put("nombre", TerceroResponse.nombreDe(t));
        m.put("ciudad", t.getCiudad());
        m.put("cliente", t.isCliente());
        m.put("proveedor", t.isProveedor());
        return m;
    }

    private Tercero buscar(Long id) {
        return terceros.findById(id).orElseThrow(() -> ApiException.noEncontrado("Tercero"));
    }

    private void validarIdentificacion(String numero, Long idActual) {
        terceros.findByNumeroIdentificacion(numero).ifPresent(otro -> {
            if (!otro.getId().equals(idActual)) {
                throw ApiException.conflicto("IDENTIFICACION_DUPLICADA", "error.identificacion.duplicada", numero);
            }
        });
    }

    private static String limpiar(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private void aplicar(Tercero t, TerceroRequest r) {
        t.setTipoPersona(r.tipoPersona());
        t.setTipoIdentificacion(r.tipoIdentificacion());
        t.setNumeroIdentificacion(r.numeroIdentificacion().trim());
        boolean natural = r.tipoPersona() == TipoPersona.NATURAL;
        t.setPrimerNombre(natural ? limpiar(r.primerNombre()) : null);
        t.setSegundoNombre(natural ? limpiar(r.segundoNombre()) : null);
        t.setPrimerApellido(natural ? limpiar(r.primerApellido()) : null);
        t.setSegundoApellido(natural ? limpiar(r.segundoApellido()) : null);
        t.setRazonSocial(natural ? null : limpiar(r.razonSocial()));
        t.setDireccion(r.direccion().trim());
        t.setCiudad(r.ciudad().trim());
        t.setTelefono(r.telefono().trim());
        t.setCorreo(r.correo().trim());
        t.setCliente(r.cliente());
        t.setProveedor(r.proveedor());
    }
}
